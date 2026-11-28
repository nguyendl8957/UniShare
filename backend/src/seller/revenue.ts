import { Router } from "express";
import {
  AggregateField,
  FieldPath,
  getFirestore,
  type Query
} from "firebase-admin/firestore";

import { requireAuth } from "../shared/firebaseAuth";
import { sendApiError } from "../shared/httpErrors";

export const sellerRevenueRouter = Router();

const ORDER_ID_PATTERN = /^ord_[a-f0-9]{16}$/;
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;
const MIN_PAID_AT = "1970-01-01T00:00:00.000Z";

function vietnamMidnightUtc(value: unknown): string | null {
  if (typeof value !== "string" || !DATE_PATTERN.test(value)) {
    return null;
  }

  const date = new Date(`${value}T00:00:00+07:00`);

  if (!Number.isFinite(date.getTime())) {
    return null;
  }

  // Ensure invalid dates such as February 30 are rejected.
  const localDay = new Date(
    date.getTime() + 7 * 60 * 60 * 1000
  ).toISOString().slice(0, 10);

  return localDay === value ? date.toISOString() : null;
}

async function hasSellerPermission(uid: string): Promise<boolean> {
  const snapshot = await getFirestore()
    .collection("users")
    .doc(uid)
    .get();

  return snapshot.data()?.sellerEnabled === true;
}

sellerRevenueRouter.get(
  "/revenue",
  requireAuth,
  async (req, res) => {
    const uid = res.locals.uid as string;
    const rawFrom = req.query.from;
    const rawTo = req.query.to;

    if (
      (rawFrom !== undefined && typeof rawFrom !== "string") ||
      (rawTo !== undefined && typeof rawTo !== "string")
    ) {
      sendApiError(
        res, 400, "INVALID_REQUEST", "Invalid date parameters"
      );
      return;
    }

    const from = rawFrom === undefined
      ? null
      : vietnamMidnightUtc(rawFrom);

    const to = rawTo === undefined
      ? null
      : vietnamMidnightUtc(rawTo);

    if (
      (rawFrom !== undefined && from === null) ||
      (rawTo !== undefined && to === null) ||
      (from !== null && to !== null && from >= to)
    ) {
      sendApiError(
        res, 400, "INVALID_REQUEST", "Invalid date range"
      );
      return;
    }

    try {
      if (!(await hasSellerPermission(uid))) {
        sendApiError(
          res, 403, "FORBIDDEN", "Seller permission required"
        );
        return;
      }

      const db = getFirestore();

      // Only successful payments with a valid paidAt contribute.
      // [from, to) is based on Vietnam calendar days.
      let query: Query = db
        .collection("orders")
        .where("sellerId", "==", uid)
        .where("status", "==", "PAID")
        .where("paidAt", ">=", from ?? MIN_PAID_AT);

      if (to !== null) {
        query = query.where("paidAt", "<", to);
      }

      // Database aggregation: no large order list is downloaded.
      const aggregate = await query.aggregate({
        totalRevenueVnd: AggregateField.sum("amountVnd"),
        paidOrderCount: AggregateField.count()
      }).get();

      const result = aggregate.data();
      const revenue = result.totalRevenueVnd ?? 0;
      const count = result.paidOrderCount ?? 0;

      if (
        !Number.isSafeInteger(revenue) ||
        revenue < 0 ||
        !Number.isSafeInteger(count) ||
        count < 0
      ) {
        throw new Error("Invalid revenue aggregate");
      }

      res.status(200).json({
        totalRevenueVnd: revenue,
        paidOrderCount: count,
        // Defined as number of successful sales, not unique doc IDs.
        totalDocumentsSold: count
      });
    } catch {
      console.error("Seller revenue aggregation failed");

      sendApiError(
        res,
        503,
        "SERVICE_UNAVAILABLE",
        "Unable to load seller revenue"
      );
    }
  }
);

sellerRevenueRouter.get(
  "/sales",
  requireAuth,
  async (req, res) => {
    const uid = res.locals.uid as string;
    const rawLimit = req.query.limit;
    const rawCursor = req.query.cursor;

    const limit = rawLimit === undefined
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
      if (!(await hasSellerPermission(uid))) {
        sendApiError(
          res, 403, "FORBIDDEN", "Seller permission required"
        );
        return;
      }

      const db = getFirestore();

      let query = db
        .collection("orders")
        .where("sellerId", "==", uid)
        .where("status", "==", "PAID")
        .orderBy("paidAt", "desc")
        .orderBy(FieldPath.documentId(), "desc");

      if (typeof rawCursor === "string") {
        const cursorDoc = await db
          .collection("orders")
          .doc(rawCursor)
          .get();

        if (
          !cursorDoc.exists ||
          cursorDoc.get("sellerId") !== uid ||
          cursorDoc.get("status") !== "PAID" ||
          typeof cursorDoc.get("paidAt") !== "string"
        ) {
          sendApiError(
            res, 400, "INVALID_REQUEST", "Invalid cursor"
          );
          return;
        }

        query = query.startAfter(cursorDoc);
      }

      const snapshot = await query.limit(limit + 1).get();
      const page = snapshot.docs.slice(0, limit);

      const items = page.map((doc) => {
        const data = doc.data();

        return {
          orderId: doc.id,
          docId: data.docId,
          documentTitle: data.documentTitle,
          amountVnd: data.amountVnd,
          status: "PAID",
          paymentCode: data.paymentCode,
          paidAt: data.paidAt
        };
      });

      res.status(200).json({
        items,
        nextCursor:
          snapshot.size > limit && page.length > 0
            ? page[page.length - 1].id
            : null
      });
    } catch {
      console.error("Seller sales query failed");

      sendApiError(
        res,
        503,
        "SERVICE_UNAVAILABLE",
        "Unable to load seller sales"
      );
    }
  }
);
