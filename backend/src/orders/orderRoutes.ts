import { Router } from "express";
import { requireAuth } from "../shared/firebaseAuth";
import { sendApiError } from "../shared/httpErrors";
import { processOrderCreation } from "./createOrder";
import {
  FirestoreOrderStore,
  OrderStoreError
} from "./firestoreOrderStore";

export const orderRoutes = Router();

const DOC_ID_PATTERN = /^[a-zA-Z0-9_-]{1,150}$/;
const ORDER_ID_PATTERN = /^ord_[a-f0-9]{16}$/;

function sendOrderError(
  res: import("express").Response,
  status: number,
  code: string,
  message: string
): void {
  res.status(status).json({
    error: {
      code,
      message
    }
  });
}

function getStore(): FirestoreOrderStore {
  const recipientId =
    process.env.UNISHARE_PAYMENT_RECIPIENT_ID?.trim() ?? "";

  if (!recipientId) {
    throw new Error("Payment recipient is not configured");
  }

  return new FirestoreOrderStore(recipientId);
}

type BankConfig = {
  bankName: string;
  bankCode: string;
  accountNumber: string;
  accountHolder: string;
};

function readBankConfig(): BankConfig | null {
  const bankName = process.env.UNISHARE_BANK_NAME?.trim() ?? "";
  const bankCode = process.env.UNISHARE_BANK_CODE?.trim() ?? "";
  const accountNumber =
    process.env.UNISHARE_PAYMENT_RECIPIENT_ID?.trim() ?? "";
  const accountHolder =
    process.env.UNISHARE_BANK_ACCOUNT_HOLDER?.trim() ?? "";

  const complete =
    bankName.length >= 2 &&
    bankName.length <= 100 &&
    /^[A-Za-z0-9]{2,20}$/.test(bankCode) &&
    /^[0-9]{6,24}$/.test(accountNumber) &&
    accountHolder.length >= 2 &&
    accountHolder.length <= 100;

  if (!complete) return null;

  return {
    bankName,
    bankCode,
    accountNumber,
    accountHolder
  };
}

function transferInstructionsFor(
  order: import("./createOrder").OrderDTO
) {
  const config = readBankConfig();

  if (
    !config ||
    order.status !== "PENDING" ||
    !Number.isFinite(Date.parse(order.expiresAt)) ||
    Date.parse(order.expiresAt) <= Date.now()
  ) {
    return null;
  }

  return {
    ...config,
    amountVnd: order.amountVnd,
    paymentCode: order.paymentCode
  };
}
function publicOrder(
  order: import("./createOrder").OrderDTO
) {
  return {
    orderId: order.orderId,
    docId: order.docId,
    documentTitle: order.documentTitle,
    amountVnd: order.amountVnd,
    currency: order.currency,
    status: order.status,
    paymentCode: order.paymentCode,
    createdAt: order.createdAt,
    expiresAt: order.expiresAt,
    transferInstructions: transferInstructionsFor(order)
  };
}

orderRoutes.post("/", requireAuth, async (req, res) => {
  const buyerId = res.locals.uid as string;
  const idempotencyKey =
    req.header("Idempotency-Key")?.trim();
  const docId = req.body?.docId;

  if (
    typeof docId !== "string" ||
    !DOC_ID_PATTERN.test(docId) ||
    !idempotencyKey ||
    idempotencyKey.length > 128
  ) {
    sendApiError(
      res,
      400,
      "INVALID_REQUEST",
      "Valid docId and Idempotency-Key are required"
    );
    return;
  }

  if (
    process.env.NODE_ENV === "production" &&
    !readBankConfig()
  ) {
    sendApiError(
      res,
      503,
      "SERVICE_UNAVAILABLE",
      "Payment instructions are not configured"
    );
    return;
  }

  try {
    const result = await processOrderCreation(
      {
        buyerId,
        docId,
        idempotencyKey
      },
      getStore()
    );

    if (!result.order) {
      sendOrderError(
        res,
        result.statusCode,
        result.errorCode ?? "INVALID_REQUEST",
        result.message ?? "Cannot create order"
      );
      return;
    }

    res.status(result.statusCode).json({
      order: publicOrder(result.order)
    });
  } catch (error) {
    if (error instanceof OrderStoreError) {
      const code = error.code;
      const status =
        code === "INVALID_ORDER_DATA" ||
        code === "PAYMENT_CODE_COLLISION"
          ? 503
          : 409;

      sendOrderError(res, status, code, error.message);
      return;
    }

    console.error("Create order failed");

    sendApiError(
      res,
      503,
      "SERVICE_UNAVAILABLE",
      "Unable to create order"
    );
  }
});

orderRoutes.get(
  "/:orderId",
  requireAuth,
  async (req, res) => {
    const buyerId = res.locals.uid as string;
    const orderId = req.params.orderId;

    if (
      typeof orderId !== "string" ||
      !ORDER_ID_PATTERN.test(orderId)
    ) {
      sendApiError(
        res,
        400,
        "INVALID_REQUEST",
        "Invalid orderId"
      );
      return;
    }

    try {
      const store = getStore();
      const order = await store.getOwnedOrder(
        orderId,
        buyerId
      );

      if (!order) {
        sendApiError(
          res,
          404,
          "NOT_FOUND",
          "Order not found"
        );
        return;
      }

      const expired =
        order.status === "PENDING" &&
        Date.parse(order.expiresAt) <= Date.now();

      res.status(200).json({
        order: publicOrder({
          ...order,
          status: expired ? "EXPIRED" : order.status
        })
      });
    } catch {
      sendApiError(
        res,
        503,
        "SERVICE_UNAVAILABLE",
        "Unable to read order"
      );
    }
  }
);
