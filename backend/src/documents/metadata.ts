import { Router } from "express";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { requireAuth } from "../shared/firebaseAuth";
import { sendApiError } from "../shared/httpErrors";

export const metadataRouter = Router();

const DOC_ID = /^[A-Za-z0-9_-]{1,150}$/;

function normalizeTitle(value: string): string {
  return value
    .trim()
    .toLowerCase()
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .replace(/đ/g, "d")
    .replace(/\s+/g, " ");
}

metadataRouter.patch("/:docId/metadata", requireAuth, async (req, res) => {
  const uid = res.locals.uid as string;
  const docId = req.params.docId;
  const body = req.body ?? {};

  const {
    expectedVersionId,
    title,
    category,
    subject,
    description,
    priceVnd
  } = body;

  if (
    typeof docId !== "string" ||
    !DOC_ID.test(docId) ||
    expectedVersionId !== "v1" ||
    typeof title !== "string" ||
    title.trim().length < 5 ||
    title.trim().length > 200 ||
    typeof category !== "string" ||
    !category.trim() ||
    category.length > 150 ||
    typeof subject !== "string" ||
    !subject.trim() ||
    subject.length > 150 ||
    typeof description !== "string" ||
    description.length > 500 ||
    !Number.isSafeInteger(priceVnd) ||
    priceVnd < 5000 ||
    priceVnd > 500000 ||
    (body.school !== undefined && body.school !== "")
  ) {
    sendApiError(res, 400, "INVALID_REQUEST", "Invalid metadata");
    return;
  }

  const metadata = {
    title: title.trim(),
    category: category.trim(),
    subject: subject.trim(),
    description: description.trim(),
    priceVnd
  };

  try {
    const db = getFirestore();

    const seller = await db.collection("users").doc(uid).get();

    if (seller.data()?.sellerEnabled !== true) {
      sendApiError(res, 403, "FORBIDDEN", "Seller permission required");
      return;
    }

    const documentRef = db.collection("documents").doc(docId);
    const catalogRef = db.collection("catalog").doc(docId);

    const result = await db.runTransaction(async (tx) => {
      // All reads must happen before any Firestore writes.
      const documentSnapshot = await tx.get(documentRef);
      const catalogSnapshot = await tx.get(catalogRef);

      if (!documentSnapshot.exists) return "NOT_FOUND";

      const document = documentSnapshot.data()!;

      if (document.sellerId !== uid) return "FORBIDDEN";

      if (document.activeVersionId !== expectedVersionId) {
        return "VERSION_CONFLICT";
      }

      if (
        document.status !== "PUBLISHED" &&
        document.status !== "ARCHIVED"
      ) {
        return "INVALID_STATUS";
      }

      const listed = document.status === "PUBLISHED";

      if (
        listed &&
        (!catalogSnapshot.exists ||
          catalogSnapshot.data()?.sellerId !== uid)
      ) {
        return "CATALOG_CONFLICT";
      }

      if (!listed && catalogSnapshot.exists) {
        return "CATALOG_CONFLICT";
      }

      tx.update(documentRef, {
        ...metadata,
        updatedAt: FieldValue.serverTimestamp()
      });

      if (listed) {
        tx.update(catalogRef, {
          title: metadata.title,
          titleNormalized: normalizeTitle(metadata.title),
          subjectId: metadata.subject,
          categoryId: metadata.category,
          priceVnd: metadata.priceVnd
        });
      }

      return document.status as "PUBLISHED" | "ARCHIVED";
    });

    switch (result) {
      case "NOT_FOUND":
        sendApiError(res, 404, "NOT_FOUND", "Document not found");
        return;

      case "FORBIDDEN":
        sendApiError(res, 403, "FORBIDDEN", "Not document owner");
        return;

      case "VERSION_CONFLICT":
      case "CATALOG_CONFLICT":
        res.status(409).json({
          error: {
            code: "VERSION_CONFLICT",
            message: "Document or catalog state changed"
          }
        });
        return;

      case "INVALID_STATUS":
        sendApiError(res, 409, "INVALID_REQUEST", "Invalid document status");
        return;

      default:
        res.status(200).json({
          docId,
          versionId: expectedVersionId,
          status: result,
          ...metadata
        });
    }
  } catch (error) {
    console.error("Document metadata update failed");

    sendApiError(
      res,
      503,
      "SERVICE_UNAVAILABLE",
      "Unable to update document metadata"
    );
  }
});