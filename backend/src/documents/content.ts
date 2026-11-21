import { Router } from "express";
import { getStorage } from "firebase-admin/storage";

import { requireAuth } from "../shared/firebaseAuth";
import { sendApiError } from "../shared/httpErrors";
import {
  authorizePdfRead,
  DocumentAccessError
} from "./entitlement";

export const contentRouter = Router();

const DOC_ID_PATTERN = /^[a-zA-Z0-9_-]{1,150}$/;
const MAX_PDF_BYTES = 25 * 1024 * 1024;

contentRouter.get(
  "/:docId/content",
  requireAuth,
  async (req, res) => {
    const uid = res.locals.uid as string;
    const docId = req.params.docId;

    res.setHeader("Cache-Control", "private, no-store");
    res.setHeader("X-Content-Type-Options", "nosniff");

    if (
      typeof docId !== "string" ||
      !DOC_ID_PATTERN.test(docId)
    ) {
      sendApiError(
        res,
        400,
        "INVALID_REQUEST",
        "Invalid document ID"
      );
      return;
    }

    const bucketName = (process.env.UNISHARE_STORAGE_BUCKET ?? process.env.FIREBASE_STORAGE_BUCKET);

    if (!bucketName) {
      sendApiError(
        res,
        503,
        "SERVICE_UNAVAILABLE",
        "PDF storage is unavailable"
      );
      return;
    }

    try {
      const authorized = await authorizePdfRead(uid, docId);

      if (authorized.sizeBytes > MAX_PDF_BYTES) {
        sendApiError(
          res,
          404,
          "NOT_FOUND",
          "PDF is unavailable"
        );
        return;
      }

      // Pin the exact immutable generation granted to Buyer.
      const file = getStorage()
        .bucket(bucketName)
        .file(authorized.pdfPath, {
          generation: authorized.pdfGeneration
        });

      const [metadata] = await file.getMetadata();

      if (
        String(metadata.generation) !== authorized.pdfGeneration ||
        metadata.contentType !== "application/pdf" ||
        Number(metadata.size) !== authorized.sizeBytes
      ) {
        sendApiError(
          res,
          404,
          "NOT_FOUND",
          "PDF version is unavailable"
        );
        return;
      }

      const [pdfBytes] = await file.download();

      if (
        pdfBytes.length !== authorized.sizeBytes ||
        pdfBytes.length > MAX_PDF_BYTES ||
        pdfBytes.subarray(0, 5).toString("ascii") !== "%PDF-"
      ) {
        sendApiError(
          res,
          404,
          "NOT_FOUND",
          "PDF content is unavailable"
        );
        return;
      }

      // Recheck entitlement before releasing PDF bytes.
      await authorizePdfRead(uid, docId);

      res.setHeader("Content-Type", "application/pdf");
      res.setHeader(
        "Content-Disposition",
        'inline; filename="document.pdf"'
      );
      res.setHeader("Content-Length", String(pdfBytes.length));

      res.status(200).end(pdfBytes);
    } catch (error) {
      if (error instanceof DocumentAccessError) {
        if (error.statusCode === 400) {
          sendApiError(res, 400, "INVALID_REQUEST", error.code);
        } else if (error.statusCode === 403) {
          sendApiError(res, 403, "FORBIDDEN", error.code);
        } else {
          sendApiError(res, 404, "NOT_FOUND", error.code);
        }
        return;
      }

      // Do not expose Storage paths, tokens or internal exceptions.
      console.error("PDF content request failed");

      sendApiError(
        res,
        503,
        "SERVICE_UNAVAILABLE",
        "Unable to retrieve PDF"
      );
    }
  }
);
