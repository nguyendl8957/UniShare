import { getFirestore, type Firestore } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";

import { authorizePdfRead } from "../documents/entitlement";
import { extractPdfPages, type PdfTextPage } from "./pdfText";

const MAX_AI_PDF_BYTES = 2 * 1024 * 1024;

export class AiPdfSourceError extends Error {
  constructor(
    public readonly code:
      | "STORAGE_UNAVAILABLE"
      | "PDF_TOO_LARGE"
      | "PDF_VERSION_MISMATCH"
  ) {
    super(code);
    this.name = "AiPdfSourceError";
  }
}

export async function loadAuthorizedPdfText(
  uid: string,
  docId: string,
  db: Firestore = getFirestore()
): Promise<PdfTextPage[]> {
  // The caller's UID must come from verified Firebase Authentication.
  const authorized = await authorizePdfRead(uid, docId, db);

  if (authorized.sizeBytes > MAX_AI_PDF_BYTES) {
    throw new AiPdfSourceError("PDF_TOO_LARGE");
  }

  const bucketName =
    process.env.UNISHARE_STORAGE_BUCKET ??
    process.env.FIREBASE_STORAGE_BUCKET;

  if (!bucketName) {
    throw new AiPdfSourceError("STORAGE_UNAVAILABLE");
  }

  // Pin the exact immutable Storage generation authorized for Buyer.
  const file = getStorage()
    .bucket(bucketName)
    .file(authorized.pdfPath, {
      generation: authorized.pdfGeneration
    });

  let pdfBytes: Buffer;

  try {
    const [metadata] = await file.getMetadata();

    if (
      String(metadata.generation) !== authorized.pdfGeneration ||
      metadata.contentType !== "application/pdf" ||
      Number(metadata.size) !== authorized.sizeBytes
    ) {
      throw new AiPdfSourceError("PDF_VERSION_MISMATCH");
    }

    const [downloaded] = await file.download();

    if (
      downloaded.length !== authorized.sizeBytes ||
      downloaded.subarray(0, 5).toString("ascii") !== "%PDF-"
    ) {
      throw new AiPdfSourceError("PDF_VERSION_MISMATCH");
    }

    pdfBytes = downloaded;
  } catch (error) {
    if (error instanceof AiPdfSourceError) {
      throw error;
    }

    // Never expose a private Storage path or internal error.
    throw new AiPdfSourceError("STORAGE_UNAVAILABLE");
  }

  // Extract text using the bounded A08.6a parser.
  const pages = await extractPdfPages(pdfBytes);

  // Recheck permission before using extracted content.
  const stillAuthorized = await authorizePdfRead(uid, docId, db);

  if (
    stillAuthorized.versionId !== authorized.versionId ||
    stillAuthorized.orderId !== authorized.orderId ||
    stillAuthorized.pdfGeneration !== authorized.pdfGeneration ||
    stillAuthorized.pdfPath !== authorized.pdfPath ||
    stillAuthorized.sizeBytes !== authorized.sizeBytes
  ) {
    throw new AiPdfSourceError("PDF_VERSION_MISMATCH");
  }

  return pages;
}