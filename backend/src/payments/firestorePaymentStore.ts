import { createHash } from "node:crypto";
import {
  getFirestore,
  type Firestore,
  type Transaction
} from "firebase-admin/firestore";

import {
  matchPaymentTransaction,
  type IPaymentRepository,
  type OrderSnapshot,
  type PaymentMatchResult,
  type PaymentRecord
} from "./matchPayment";

import type {
  NormalizedWebhookEvent
} from "./verifyWebhook";

function pairKey(buyerId: string, docId: string): string {
  return createHash("sha256")
    .update(JSON.stringify([buyerId, docId]))
    .digest("hex");
}

function firestoreData<T>(value: T): T {
  // B03 contains optional fields. Firestore rejects undefined.
  return JSON.parse(JSON.stringify(value)) as T;
}

/**
 * Executes B03 reconciliation and all corresponding writes
 * inside ONE Firestore transaction.
 *
 * Never accepts a payment status from Android.
 * The caller must verify the provider webhook first.
 */
export class FirestorePaymentStore {
  constructor(
    private readonly recipientAccount: string,
    private readonly db: Firestore = getFirestore()
  ) {
    if (!recipientAccount.trim()) {
      throw new Error("Payment recipient account is required");
    }
  }

  async processVerifiedEvent(
    event: NormalizedWebhookEvent,
    requestId?: string
  ): Promise<PaymentMatchResult> {
    if (event.provider !== "SEPAY") {
      throw new Error("Unsupported provider");
    }

    if (
      !event.providerTxnId ||
      event.providerTxnId.includes("/")
    ) {
      throw new Error("Invalid provider transaction ID");
    }

    const txnKey = `sepay_${event.providerTxnId}`;

    return this.db.runTransaction(async transaction => {
      const repo = this.transactionRepository(transaction);

      // B03 performs all reads through the same transaction.
      const result = await matchPaymentTransaction(
        event,
        repo,
        Date.now(),
        requestId
      );

      if (result.status === "DUPLICATE") {
        return result;
      }

      const writes = result.writeSet;

      if (!writes) {
        throw new Error(
          "Non-duplicate payment result has no write set"
        );
      }

      if (writes.createPayment.txnKey !== txnKey) {
        throw new Error("Payment transaction key mismatch");
      }

      const paymentRef = this.db
        .collection("payments")
        .doc(txnKey);

      let orderRef:
        ReturnType<Firestore["doc"]> | null = null;

      let activeRef:
        ReturnType<Firestore["doc"]> | null = null;

      let activeMatches = false;

      if (writes.updateOrder) {
        orderRef = this.db
          .collection("orders")
          .doc(writes.updateOrder.orderId);

        const orderSnapshot =
          await transaction.get(orderRef);

        if (!orderSnapshot.exists) {
          throw new Error("Order disappeared");
        }

        const order = orderSnapshot.data()!;

        if (order.status !== "PENDING") {
          throw new Error(
            "Order is no longer pending; retry reconciliation"
          );
        }

        if (
          writes.updateOrder.status === "PAID" &&
          (
            result.status !== "MATCHED" ||
            !writes.createEntitlement ||
            writes.createEntitlement.uid !== order.buyerId ||
            writes.createEntitlement.docId !== order.docId ||
            writes.createEntitlement.orderId !== orderRef.id ||
            writes.createEntitlement.versionId !== order.versionId ||
            writes.createPayment.orderId !== orderRef.id ||
            writes.createPayment.amountVnd !== order.amountVnd ||
            writes.createPayment.result !== "MATCHED" ||
            writes.createPayment.recipientId !==
              this.recipientAccount
          )
        ) {
          throw new Error(
            "Invalid PAID/entitlement write set"
          );
        }

        activeRef = this.db
          .collection("activePurchases")
          .doc(pairKey(order.buyerId, order.docId));

        const activeSnapshot =
          await transaction.get(activeRef);

        activeMatches =
          activeSnapshot.exists &&
          activeSnapshot.get("orderId") === orderRef.id;
      }

      if (result.status === "MATCHED" &&
          (!writes.updateOrder ||
           !writes.createEntitlement)) {
        throw new Error(
          "MATCHED requires order and entitlement writes"
        );
      }

      const entitlement = writes.createEntitlement;

      let entitlementRef:
        ReturnType<Firestore["doc"]> | null = null;

      if (entitlement) {
        entitlementRef = this.db
          .collection("entitlements")
          .doc(entitlement.uid)
          .collection("items")
          .doc(entitlement.docId);

        const previous =
          await transaction.get(entitlementRef);

        if (
          previous.exists &&
          previous.data()?.revokedAt == null
        ) {
          throw new Error(
            "Entitlement already exists; retry reconciliation"
          );
        }
      }

      // All transaction reads finish BEFORE the first write.
      transaction.create(
        paymentRef,
        firestoreData(writes.createPayment)
      );

      if (writes.updateOrder && orderRef) {
        transaction.update(
          orderRef,
          firestoreData({
            status: writes.updateOrder.status,
            ...(writes.updateOrder.paidAt
              ? { paidAt: writes.updateOrder.paidAt }
              : {})
          })
        );

        if (activeRef && activeMatches) {
          transaction.update(activeRef, {
            state: writes.updateOrder.status
          });
        }
      }

      if (entitlement && entitlementRef) {
        transaction.set(
          entitlementRef,
          firestoreData({
            orderId: entitlement.orderId,
            versionId: entitlement.versionId,
            grantedAt: entitlement.grantedAt
          })
        );
      }

      if (writes.createReconciliation) {
        transaction.create(
          this.db
            .collection("reconciliation")
            .doc(txnKey),
          firestoreData(writes.createReconciliation)
        );
      }

      return result;
    });
  }

  private transactionRepository(
    tx: Transaction
  ): IPaymentRepository {
    const db = this.db;
    const recipientAccount = this.recipientAccount;

    return {
      async getPaymentByTxnKey(
        txnKey: string
      ): Promise<PaymentRecord | null> {
        const snapshot = await tx.get(
          db.collection("payments").doc(txnKey)
        );

        return snapshot.exists
          ? snapshot.data() as PaymentRecord
          : null;
      },

      async getOrderByPaymentCode(
        code: string
      ): Promise<OrderSnapshot | null> {
        const index = await tx.get(
          db.collection("paymentCodes").doc(code)
        );

        if (!index.exists) return null;

        const orderId = index.get("orderId");

        if (
          typeof orderId !== "string" ||
          !/^ord_[a-f0-9]{16}$/.test(orderId)
        ) {
          return null;
        }

        const snapshot = await tx.get(
          db.collection("orders").doc(orderId)
        );

        if (!snapshot.exists) return null;

        const data = snapshot.data()!;

        if (data.paymentCode !== code) {
          return null;
        }

        return {
          orderId,
          buyerId: data.buyerId,
          sellerId: data.sellerId,
          docId: data.docId,
          versionId: data.versionId,
          amountVnd: data.amountVnd,
          status: data.status,
          paymentCode: data.paymentCode,
          createdAt: data.createdAt,
          expiresAt: data.expiresAt
        } as OrderSnapshot;
      },

      async hasEntitlement(
        buyerId: string,
        docId: string
      ): Promise<boolean> {
        const snapshot = await tx.get(
          db.collection("entitlements")
            .doc(buyerId)
            .collection("items")
            .doc(docId)
        );

        return snapshot.exists &&
          snapshot.data()?.revokedAt == null;
      },

      async getSystemRecipientAccount(): Promise<string> {
        return recipientAccount;
      }
    };
  }
}
