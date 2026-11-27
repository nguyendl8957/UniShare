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
      !["archive", "unarchive"].includes(req.body?.action) ||
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
    const action = req.body.action as "archive" | "unarchive";

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

        if (action === "unarchive") {
          if (document.status === "PUBLISHED") {
            return "ALREADY_PUBLISHED";
          }

          if (document.status !== "ARCHIVED") {
            return "INVALID_STATUS";
          }

          const versionRef = documentRef
            .collection("versions")
            .doc(expectedVersionId);

          const versionSnapshot = await transaction.get(versionRef);
          const catalogSnapshot = await transaction.get(catalogRef);

          if (catalogSnapshot.exists) {
            return "CATALOG_CONFLICT";
          }

          const version = versionSnapshot.data();
          const previewRefs = version?.previewPaths;

          if (
            !versionSnapshot.exists ||
            version?.pdfPath !==
              `private/${docId}/${expectedVersionId}/source.pdf` ||
            !Array.isArray(previewRefs) ||
            previewRefs.length < 1 ||
            previewRefs.length > 5 ||
            !previewRefs.every(
              (path: unknown) =>
                typeof path === "string" &&
                path.startsWith(
                  `previews/${docId}/${expectedVersionId}/`
                )
            ) ||
            typeof document.title !== "string" ||
            !document.title.trim() ||
            typeof document.category !== "string" ||
            typeof document.subject !== "string" ||
            !Number.isSafeInteger(document.priceVnd) ||
            document.priceVnd <= 0
          ) {
            return "VERSION_NOT_READY";
          }

          const titleNormalized = document.title
            .trim()
            .toLowerCase()
            .normalize("NFD")
            .replace(/\p{M}/gu, "")
            .replace(/đ/g, "d")
            .replace(/\s+/g, " ");

          transaction.create(catalogRef, {
            title: document.title,
            titleNormalized,
            subjectId: document.subject,
            categoryId: document.category,
            priceVnd: document.priceVnd,
            sellerId: uid,
            previewRefs,
            publishedAt: FieldValue.serverTimestamp()
          });

          transaction.update(documentRef, {
            status: "PUBLISHED",
            updatedAt: FieldValue.serverTimestamp()
          });

          return "UNARCHIVED";
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

        case "CATALOG_CONFLICT":
        case "VERSION_NOT_READY":
          sendApiError(
            res, 409, "INVALID_REQUEST", "Cannot restore this document version"
          );
          return;

        default:
          res.status(200).json({
            docId,
            status:
              result === "UNARCHIVED" || result === "ALREADY_PUBLISHED"
                ? "PUBLISHED"
                : "ARCHIVED",
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