import { Router } from "express";
import { createHash } from "node:crypto";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { requireAuth } from "../shared/firebaseAuth";
import { sendApiError } from "../shared/httpErrors";

export const draftsRouter = Router();

draftsRouter.post("/drafts", requireAuth, async (req, res) => {
  const uid = res.locals.uid as string;
  const idempotencyKey = req.header("Idempotency-Key")?.trim();

  if (!idempotencyKey || idempotencyKey.length > 128) {
    sendApiError(
      res,
      400,
      "INVALID_REQUEST",
      "Valid Idempotency-Key is required"
    );
    return;
  }

  const { title, subject, category, description, priceVnd } =
    req.body ?? {};

  if (
    typeof title !== "string" ||
    title.trim().length < 3 ||
    title.trim().length > 200 ||
    typeof subject !== "string" ||
    !subject.trim() ||
    typeof category !== "string" ||
    !category.trim() ||
    (description !== undefined &&
      (typeof description !== "string" ||
        description.length > 3000)) ||
    !Number.isSafeInteger(priceVnd) ||
    priceVnd <= 0
  ) {
    sendApiError(
      res,
      400,
      "INVALID_REQUEST",
      "Invalid document metadata"
    );
    return;
  }

  try {
    const db = getFirestore();

    // Quyen Seller phai do backend cap, khong tin body client.
    const userSnapshot = await db.collection("users").doc(uid).get();

    if (userSnapshot.data()?.sellerEnabled !== true) {
      sendApiError(
        res,
        403,
        "FORBIDDEN",
        "Seller permission required"
      );
      return;
    }

    // Cung UID + Idempotency-Key -> cung docId.
    const docId =
      "draft_" +
      createHash("sha256")
        .update(JSON.stringify([uid, idempotencyKey]))
        .digest("hex");

    const versionId = "v1";
    const documentRef = db.collection("documents").doc(docId);

    const normalizedMetadata = {
      title: title.trim(),
      subject: subject.trim(),
      category: category.trim(),
      description: description?.trim() ?? "",
      priceVnd
    };

    const result = await db.runTransaction(async (transaction) => {
      const existing = await transaction.get(documentRef);

      if (existing.exists) {
        const data = existing.data()!;

        const samePayload =
          data.sellerId === uid &&
          data.title === normalizedMetadata.title &&
          data.subject === normalizedMetadata.subject &&
          data.category === normalizedMetadata.category &&
          data.description === normalizedMetadata.description &&
          data.priceVnd === normalizedMetadata.priceVnd;

        if (!samePayload) {
          return { conflict: true, created: false };
        }

        return { conflict: false, created: false };
      }

      transaction.create(documentRef, {
        sellerId: uid,
        ...normalizedMetadata,
        status: "DRAFT",
        activeVersionId: versionId,
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp()
      });

      return { conflict: false, created: true };
    });

    if (result.conflict) {
      res.status(409).json({
        error: {
          code: "IDEMPOTENCY_CONFLICT",
          message: "Key already used with different metadata"
        }
      });
      return;
    }

    res.status(result.created ? 201 : 200).json({
      docId,
      versionId,
      status: "DRAFT",
      stagingPrefix: `staging/${uid}/${docId}/${versionId}/`
    });
  } catch (error) {
    console.error("Create draft failed", error);

    sendApiError(
      res,
      503,
      "SERVICE_UNAVAILABLE",
      "Unable to create draft"
    );
  }
});
