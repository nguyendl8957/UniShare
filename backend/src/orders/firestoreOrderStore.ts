import { createHash } from "node:crypto";
import {
  getFirestore,
  type Firestore
} from "firebase-admin/firestore";

import type {
  DocumentSnapshot,
  IOrderRepository,
  OrderDTO
} from "./createOrder";

function hashParts(parts: string[]): string {
  return createHash("sha256")
    .update(JSON.stringify(parts))
    .digest("hex");
}

export class OrderStoreError extends Error {
  constructor(
    public readonly code: string,
    message: string
  ) {
    super(message);
    this.name = "OrderStoreError";
  }
}

/**
 * Firestore implementation of the B02 repository contract.
 *
 * All writes use Firebase Admin SDK.
 * Android clients must never create or mark orders PAID.
 */
export class FirestoreOrderStore implements IOrderRepository {
  constructor(
    private readonly recipientId: string,
    private readonly db: Firestore = getFirestore()
  ) {
    if (!recipientId.trim()) {
      throw new Error(
        "Payment recipient must be configured on the server"
      );
    }
  }

  async getDocument(
    docId: string
  ): Promise<DocumentSnapshot | null> {
    const snapshot = await this.db
      .collection("documents")
      .doc(docId)
      .get();

    if (!snapshot.exists) return null;

    const data = snapshot.data()!;

    return {
      docId,
      sellerId: data.sellerId,
      priceVnd: data.priceVnd,
      activeVersionId: data.activeVersionId,
      status: data.status,
      title: data.title
    };
  }

  async hasEntitlement(
    buyerId: string,
    docId: string
  ): Promise<boolean> {
    const snapshot = await this.db
      .collection("entitlements")
      .doc(buyerId)
      .collection("items")
      .doc(docId)
      .get();

    return snapshot.exists &&
      snapshot.data()?.revokedAt == null;
  }

  async getOrderByKey(
    buyerId: string,
    idempotencyKey: string
  ): Promise<OrderDTO | null> {
    const requestKey = hashParts([
      buyerId,
      idempotencyKey
    ]);

    const request = await this.db
      .collection("orderRequests")
      .doc(requestKey)
      .get();

    if (!request.exists) return null;

    const orderId = request.get("orderId");

    if (typeof orderId !== "string") {
      throw new OrderStoreError(
        "INVALID_ORDER_DATA",
        "Stored order reference is invalid"
      );
    }

    return this.getOwnedOrder(orderId, buyerId);
  }

  async getActiveOrderForDoc(
    buyerId: string,
    docId: string
  ): Promise<OrderDTO | null> {
    const pairKey = hashParts([buyerId, docId]);

    const active = await this.db
      .collection("activePurchases")
      .doc(pairKey)
      .get();

    if (!active.exists) return null;

    const orderId = active.get("orderId");

    if (typeof orderId !== "string") {
      return null;
    }

    const order = await this.getOwnedOrder(
      orderId,
      buyerId
    );

    if (!order) return null;

    if (order.status === "REVIEW_REQUIRED") {
      return order;
    }

    if (
      order.status === "PENDING" &&
      Date.parse(order.expiresAt) > Date.now()
    ) {
      return order;
    }

    return null;
  }

  async getOwnedOrder(
    orderId: string,
    buyerId: string
  ): Promise<OrderDTO | null> {
    const snapshot = await this.db
      .collection("orders")
      .doc(orderId)
      .get();

    if (!snapshot.exists) return null;

    const data = snapshot.data()!;

    if (data.buyerId !== buyerId) {
      return null;
    }

    return data as OrderDTO;
  }

  async saveOrder(
    order: OrderDTO,
    buyerId: string,
    idempotencyKey: string
  ): Promise<OrderDTO> {
    const requestKey = hashParts([
      buyerId,
      idempotencyKey
    ]);

    const pairKey = hashParts([
      buyerId,
      order.docId
    ]);

    const requestRef = this.db
      .collection("orderRequests")
      .doc(requestKey);

    const activeRef = this.db
      .collection("activePurchases")
      .doc(pairKey);

    const documentRef = this.db
      .collection("documents")
      .doc(order.docId);

    const entitlementRef = this.db
      .collection("entitlements")
      .doc(buyerId)
      .collection("items")
      .doc(order.docId);

    const codeRef = this.db
      .collection("paymentCodes")
      .doc(order.paymentCode);

    const orderRef = this.db
      .collection("orders")
      .doc(order.orderId);

    return this.db.runTransaction(async transaction => {
      const requestSnapshot =
        await transaction.get(requestRef);

      if (requestSnapshot.exists) {
        const storedOrderId =
          requestSnapshot.get("orderId");

        const storedDocId =
          requestSnapshot.get("docId");

        if (storedDocId !== order.docId) {
          throw new OrderStoreError(
            "IDEMPOTENCY_CONFLICT",
            "Idempotency key belongs to another document"
          );
        }

        if (typeof storedOrderId !== "string") {
          throw new OrderStoreError(
            "INVALID_ORDER_DATA",
            "Stored order reference is invalid"
          );
        }

        const existing = await transaction.get(
          this.db.collection("orders").doc(storedOrderId)
        );

        if (!existing.exists) {
          throw new OrderStoreError(
            "INVALID_ORDER_DATA",
            "Referenced order does not exist"
          );
        }

        return existing.data() as OrderDTO;
      }

      const activeSnapshot =
        await transaction.get(activeRef);

      let activeOrder: OrderDTO | null = null;

      if (activeSnapshot.exists) {
        const activeOrderId =
          activeSnapshot.get("orderId");

        if (typeof activeOrderId === "string") {
          const snapshot = await transaction.get(
            this.db.collection("orders").doc(activeOrderId)
          );

          if (snapshot.exists) {
            activeOrder = snapshot.data() as OrderDTO;
          }
        }
      }

      const documentSnapshot =
        await transaction.get(documentRef);

      const entitlementSnapshot =
        await transaction.get(entitlementRef);

      const codeSnapshot =
        await transaction.get(codeRef);

      const document = documentSnapshot.data();

      if (
        !documentSnapshot.exists ||
        document?.status !== "PUBLISHED" ||
        document?.sellerId !== order.sellerId ||
        document?.sellerId === buyerId ||
        document?.activeVersionId !== order.versionId ||
        document?.priceVnd !== order.amountVnd
      ) {
        throw new OrderStoreError(
          "INVALID_DOCUMENT_STATUS",
          "Document changed before order creation"
        );
      }

      if (
        entitlementSnapshot.exists &&
        entitlementSnapshot.data()?.revokedAt == null
      ) {
        throw new OrderStoreError(
          "ALREADY_OWNED",
          "Buyer already owns this document"
        );
      }

      if (activeOrder) {
        if (activeOrder.status === "REVIEW_REQUIRED") {
          throw new OrderStoreError(
            "ORDER_UNDER_REVIEW",
            "Existing order requires reconciliation"
          );
        }

        if (
          activeOrder.status === "PENDING" &&
          Date.parse(activeOrder.expiresAt) > Date.now()
        ) {
          transaction.create(requestRef, {
            buyerId,
            docId: order.docId,
            orderId: activeOrder.orderId,
            createdAt: order.createdAt,
            requestFingerprint: hashParts([
              buyerId,
              idempotencyKey,
              order.docId
            ])
          });

          return activeOrder;
        }
      }

      if (codeSnapshot.exists) {
        throw new OrderStoreError(
          "PAYMENT_CODE_COLLISION",
          "Generated payment code already exists"
        );
      }

      transaction.create(orderRef, {
        ...order,
        recipientId: this.recipientId,
        provider: "SEPAY"
      });

      transaction.create(codeRef, {
        orderId: order.orderId,
        createdAt: order.createdAt
      });

      transaction.create(requestRef, {
        buyerId,
        docId: order.docId,
        orderId: order.orderId,
        createdAt: order.createdAt,
        requestFingerprint: hashParts([
          buyerId,
          idempotencyKey,
          order.docId
        ])
      });

      transaction.set(activeRef, {
        buyerId,
        docId: order.docId,
        orderId: order.orderId,
        state: "PENDING",
        expiresAt: order.expiresAt
      });

      return order;
    });
  }
}
