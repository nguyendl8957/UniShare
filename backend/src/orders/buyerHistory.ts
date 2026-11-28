import { Router } from "express";
import {
  FieldPath,
  getFirestore
} from "firebase-admin/firestore";
import { requireAuth } from "../shared/firebaseAuth";
import { sendApiError } from "../shared/httpErrors";

export const buyerHistoryRouter = Router();

const ORDER_ID_PATTERN = /^ord_[a-f0-9]{16}$/;

buyerHistoryRouter.get(
  "/orders",
  requireAuth,
  async (req, res) => {
    const uid = res.locals.uid as string;
    const rawLimit = req.query.limit;
    const rawCursor = req.query.cursor;

    const limit =
      rawLimit === undefined
        ? 20
        : typeof rawLimit === "string"
          ? Number(rawLimit)
          : NaN;

    if (
      !Number.isInteger(limit) ||
      limit < 1 ||
      limit > 50 ||
      (
        rawCursor !== undefined &&
        (
          typeof rawCursor !== "string" ||
          !ORDER_ID_PATTERN.test(rawCursor)
        )
      )
    ) {
      sendApiError(
        res,
        400,
        "INVALID_REQUEST",
        "Invalid pagination parameters"
      );
      return;
    }

    try {
      const db = getFirestore();

      let query = db
        .collection("orders")
        .where("buyerId", "==", uid)
        .orderBy("createdAt", "desc")
        .orderBy(FieldPath.documentId(), "desc");

      if (typeof rawCursor === "string") {
        const cursorDoc = await db
          .collection("orders")
          .doc(rawCursor)
          .get();

        if (
          !cursorDoc.exists ||
          cursorDoc.get("buyerId") !== uid ||
          typeof cursorDoc.get("createdAt") !== "string"
        ) {
          sendApiError(
            res,
            400,
            "INVALID_REQUEST",
            "Invalid cursor"
          );
          return;
        }

        query = query.startAfter(cursorDoc);
      }

      const snapshot = await query.limit(limit + 1).get();
      const hasMore = snapshot.size > limit;
      const page = snapshot.docs.slice(0, limit);
      const now = Date.now();

      const items = page.map((doc) => {
        const data = doc.data();

        const expired =
          data.status === "PENDING" &&
          typeof data.expiresAt === "string" &&
          Number.isFinite(Date.parse(data.expiresAt)) &&
          Date.parse(data.expiresAt) <= now;

        return {
          orderId: doc.id,
          docId: data.docId,
          documentTitle: data.documentTitle,
          amountVnd: data.amountVnd,
          status: expired ? "EXPIRED" : data.status,
          paymentCode: data.paymentCode,
          createdAt: data.createdAt,
          expiresAt: data.expiresAt
        };
      });

      res.status(200).json({
        items,
        nextCursor:
          hasMore && page.length > 0
            ? page[page.length - 1].id
            : null
      });
    } catch {
      console.error("Buyer order history query failed");

      sendApiError(
        res,
        503,
        "SERVICE_UNAVAILABLE",
        "Unable to load order history"
      );
    }
  }
);
