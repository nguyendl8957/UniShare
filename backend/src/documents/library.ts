import { Router } from "express";
import {
  FieldPath,
  getFirestore
} from "firebase-admin/firestore";

import { requireAuth } from "../shared/firebaseAuth";
import { sendApiError } from "../shared/httpErrors";
import { DocumentAccessError, resolveEntitledPdf } from "./entitlement";

export const libraryRouter = Router();

const ID_PATTERN = /^[A-Za-z0-9_-]{1,150}$/;

libraryRouter.get("/library", requireAuth, async (req, res) => {
  const uid = res.locals.uid as string;

  const rawLimit = req.query.limit;
  const rawCursor = req.query.cursor;

  const limit =
    rawLimit === undefined ? 20 : Number(rawLimit);

  if (
    !Number.isInteger(limit) ||
    limit < 1 ||
    limit > 50 ||
    (rawCursor !== undefined &&
      (typeof rawCursor !== "string" ||
        !ID_PATTERN.test(rawCursor)))
  ) {
    sendApiError(res, 400, "INVALID_REQUEST", "Invalid pagination");
    return;
  }

  res.setHeader("Cache-Control", "private, no-store");

  try {
    const db = getFirestore();

    const collection = db
      .collection("entitlements")
      .doc(uid)
      .collection("items");

    let query = collection
      .orderBy(FieldPath.documentId())
      .limit(limit + 1);

    if (typeof rawCursor === "string") {
      query = query.startAfter(rawCursor);
    }

    const snapshot = await query.get();

    const hasMore = snapshot.docs.length > limit;
    const pageDocs = snapshot.docs.slice(0, limit);

    const items = await Promise.all(
      pageDocs.map(async entitlementDoc => {
        const entitlement = entitlementDoc.data();

        const docId = entitlementDoc.id;
        const authorized = await resolveEntitledPdf(uid, docId, entitlement, db)
          .catch((error: unknown) => {
            if (error instanceof DocumentAccessError) return null;
            throw error;
          });
        if (!authorized) return null;

        const documentSnapshot = await db
          .collection("documents")
          .doc(docId)
          .get();

        const document = documentSnapshot.data();

        const grantedAt = entitlement.grantedAt;

        const purchasedAt =
          typeof grantedAt === "string"
            ? grantedAt
            : grantedAt?.toDate?.()?.toISOString?.() ?? null;

        return {
          docId,
          versionId: authorized.versionId,
          title:
            typeof document?.title === "string" && document.title.trim()
              ? document.title
              : "Tài liệu đã mua",
          purchasedAt,
          isSellerArchived: document?.status === "ARCHIVED"
        };
      })
    );

    const nextCursor = hasMore
      ? pageDocs[pageDocs.length - 1]?.id ?? null
      : null;

    res.status(200).json({
      items: items.filter(item => item !== null),
      nextCursor
    });
  } catch {
    console.error("Purchased library request failed");

    sendApiError(
      res,
      503,
      "SERVICE_UNAVAILABLE",
      "Unable to load purchased documents"
    );
  }
});