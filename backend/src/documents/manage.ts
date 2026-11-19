import { Router } from "express";
import { getFirestore, FieldValue } from "firebase-admin/firestore";

import { requireAuth } from "../shared/firebaseAuth";
import { sendApiError } from "../shared/httpErrors";

export const manageRouter = Router();

manageRouter.patch(
  "/:docId",
  requireAuth,
  async (req, res) => {
    const uid = res.locals.uid as string;
    const docId = req.params.docId;

    if (
      typeof docId !== "string" ||
      !/^[a-zA-Z0-9_-]{1,150}$/.test(docId) ||
      req.body?.action !== "archive" ||
      typeof req.body?.expectedVersionId !== "string"
    ) {
      sendApiError(
        res,
        400,
        "INVALID_REQUEST",
        "Invalid archive request"
      );
      return;
    }

    const expectedVersionId = req.body.expectedVersionId;

    try {
      const db = getFirestore();
      const documentRef = db.collection("documents").doc(docId);
      const catalogRef = db.collection("catalog").doc(docId);

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

      const result = await db.runTransaction(async (transaction) => {
        const documentSnapshot =
          await transaction.get(documentRef);

        if (!documentSnapshot.exists) {
          return "NOT_FOUND";
        }

        const document = documentSnapshot.data()!;

        if (document.sellerId !== uid) {
          return "FORBIDDEN";
        }

        if (document.activeVersionId !== expectedVersionId) {
          return "VERSION_CONFLICT";
        }

        if (document.status === "ARCHIVED") {
          return "ALREADY_ARCHIVED";
        }

        if (document.status !== "PUBLISHED") {
          return "INVALID_STATUS";
        }

        // Atomic Firestore transaction:
        // remove public listing and archive metadata.
        transaction.delete(catalogRef);

        transaction.update(documentRef, {
          status: "ARCHIVED",
          updatedAt: FieldValue.serverTimestamp()
        });

        // Never delete document versions or stored PDF.
        return "ARCHIVED";
      });

      switch (result) {
        case "NOT_FOUND":
          sendApiError(res, 404, "NOT_FOUND", "Document not found");
          return;

        case "FORBIDDEN":
          sendApiError(res, 403, "FORBIDDEN", "Not document owner");
          return;

        case "VERSION_CONFLICT":
          res.status(409).json({
            error: {
              code: "VERSION_CONFLICT",
              message: "Document version changed"
            }
          });
          return;

        case "INVALID_STATUS":
          sendApiError(
            res,
            400,
            "INVALID_REQUEST",
            "Only published documents can be archived"
          );
          return;

        default:
          res.status(200).json({
            docId,
            status: "ARCHIVED",
            alreadyArchived: result === "ALREADY_ARCHIVED"
          });
      }
    } catch (error) {
      console.error("Archive failed", error);

      sendApiError(
        res,
        503,
        "SERVICE_UNAVAILABLE",
        "Unable to archive document"
      );
    }
  }
);