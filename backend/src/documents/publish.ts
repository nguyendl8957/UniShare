import { Router } from "express";
import { createHash } from "node:crypto";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { PDFDocument } from "pdf-lib";
import sharp from "sharp";

import { requireAuth } from "../shared/firebaseAuth";
import { sendApiError } from "../shared/httpErrors";

export const publishRouter = Router();

const MAX_PDF_BYTES = 25 * 1024 * 1024;
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;

type PreviewInput = {
  slot: number;
  extension: "png" | "jpg" | "jpeg";
  generation: string;
};

type PublishInput = {
  versionId: string;
  pdfGeneration: string;
  previews: PreviewInput[];
};

type VerifiedFile = {
  sourcePath: string;
  destinationPath: string;
  contentType: string;
  bytes: Buffer;
  sha256: string;
  sourceGeneration: string;
};

type SavedFile = VerifiedFile & {
  destinationGeneration: string;
};

class PublishConflict extends Error {}

function normalizeTitle(value: string): string {
  return value
    .trim()
    .toLowerCase()
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .replace(/đ/g, "d")
    .replace(/\s+/g, " ");
}

function sha256(bytes: Buffer): string {
  return createHash("sha256")
    .update(bytes)
    .digest("hex");
}

function validGeneration(value: unknown): value is string {
  return (
    typeof value === "string" &&
    /^[0-9]{1,32}$/.test(value)
  );
}

function validateRequest(body: unknown): PublishInput {
  if (!body || typeof body !== "object") {
    throw new PublishConflict("Invalid request body");
  }

  const data = body as Record<string, unknown>;

  if (
    data.versionId !== "v1" ||
    !validGeneration(data.pdfGeneration) ||
    !Array.isArray(data.previews) ||
    data.previews.length < 1 ||
    data.previews.length > 5
  ) {
    throw new PublishConflict("Invalid publish payload");
  }

  const slots = new Set<number>();
  const previews: PreviewInput[] = [];

  for (const value of data.previews) {
    if (!value || typeof value !== "object") {
      throw new PublishConflict("Invalid preview");
    }

    const item = value as Record<string, unknown>;
    const slot = item.slot;
    const extension = item.extension;
    const generation = item.generation;

    if (
      typeof slot !== "number" ||
      !Number.isInteger(slot) ||
      slot < 1 ||
      slot > 5 ||
      (extension !== "png" &&
        extension !== "jpg" &&
        extension !== "jpeg") ||
      !validGeneration(generation) ||
      slots.has(slot)
    ) {
      throw new PublishConflict("Invalid preview metadata");
    }

    slots.add(slot);
    previews.push({ slot, extension, generation });
  }

  previews.sort((a, b) => a.slot - b.slot);

  return {
    versionId: "v1",
    pdfGeneration: data.pdfGeneration,
    previews
  };
}

async function verifyStagingFile(
  bucketName: string,
  sourcePath: string,
  destinationPath: string,
  generation: string,
  type: "pdf" | "image",
  extension?: string
): Promise<VerifiedFile> {
  const file = getStorage()
    .bucket(bucketName)
    .file(sourcePath);

  const [metadata] = await file.getMetadata();

  if (String(metadata.generation) !== generation) {
    throw new PublishConflict("Staging generation mismatch");
  }

  const maxBytes =
    type === "pdf" ? MAX_PDF_BYTES : MAX_IMAGE_BYTES;

  const size = Number(metadata.size);

  if (
    !Number.isSafeInteger(size) ||
    size < 1 ||
    size > maxBytes
  ) {
    throw new PublishConflict("Invalid file size");
  }

  const contentType =
    type === "pdf"
      ? "application/pdf"
      : extension === "png"
        ? "image/png"
        : "image/jpeg";

  if (metadata.contentType !== contentType) {
    throw new PublishConflict("Invalid file MIME");
  }

  const [bytes] = await file.download();

  if (bytes.length !== size || bytes.length > maxBytes) {
    throw new PublishConflict("File size mismatch");
  }

  if (type === "pdf") {
    if (bytes.subarray(0, 5).toString() !== "%PDF-") {
      throw new PublishConflict("Invalid PDF header");
    }

    try {
      const pdf = await PDFDocument.load(bytes, {
        updateMetadata: false
      });

      if (pdf.getPageCount() < 1) {
        throw new Error("Empty PDF");
      }
    } catch {
      throw new PublishConflict("Invalid PDF structure");
    }
  } else {
    try {
      const image = sharp(bytes, {
        limitInputPixels: 50_000_000
      });

      const metadata = await image.metadata();

      const validFormat =
        contentType === "image/png"
          ? metadata.format === "png"
          : metadata.format === "jpeg";

      if (
        !validFormat ||
        !metadata.width ||
        !metadata.height
      ) {
        throw new Error("Invalid image");
      }

      await image.stats();
    } catch {
      throw new PublishConflict("Invalid image structure");
    }
  }

  // Staging is create-only under Storage Rules.
  // Check generation again before committing verified bytes.
  const [latest] = await file.getMetadata();

  if (String(latest.generation) !== generation) {
    throw new PublishConflict("Staging file changed");
  }

  return {
    sourcePath,
    destinationPath,
    contentType,
    bytes,
    sha256: sha256(bytes),
    sourceGeneration: generation
  };
}

async function saveVerifiedFile(
  bucketName: string,
  verified: VerifiedFile
): Promise<SavedFile> {
  const destination = getStorage()
    .bucket(bucketName)
    .file(verified.destinationPath);

  const verifyExisting = async (): Promise<string> => {
    const [exists] = await destination.exists();

    if (!exists) {
      throw new Error("Destination does not exist");
    }

    const [metadata] = await destination.getMetadata();
    const [existingBytes] = await destination.download();

    if (
      existingBytes.length !== verified.bytes.length ||
      sha256(existingBytes) !== verified.sha256 ||
      metadata.contentType !== verified.contentType
    ) {
      throw new PublishConflict(
        "Destination exists with different content"
      );
    }

    return String(metadata.generation);
  };

  const [alreadyExists] = await destination.exists();

  if (alreadyExists) {
    const destinationGeneration = await verifyExisting();

    return { ...verified, destinationGeneration };
  }

  try {
    await destination.save(verified.bytes, {
      resumable: false,
      metadata: {
        contentType: verified.contentType,
        metadata: {
          sourceSha256: verified.sha256,
          stagingGeneration: verified.sourceGeneration
        }
      },
      preconditionOpts: {
        ifGenerationMatch: 0
      }
    });
  } catch (error) {
    // A previous attempt or concurrent request might
    // have already created the destination.
    // Reuse it ONLY if its actual bytes match.
    const [exists] = await destination.exists();

    if (!exists) {
      throw error;
    }

    const destinationGeneration = await verifyExisting();
    return { ...verified, destinationGeneration };
  }

  const destinationGeneration = await verifyExisting();

  return { ...verified, destinationGeneration };
}

publishRouter.post(
  "/:docId/publish",
  requireAuth,
  async (req, res) => {
    const uid = res.locals.uid as string;
    const docId = req.params.docId;

    if (
      typeof docId !== "string" ||
      !/^[a-zA-Z0-9_-]{1,150}$/.test(docId)
    ) {
      sendApiError(res, 400, "INVALID_REQUEST", "Invalid document ID");
      return;
    }

    let input: PublishInput;

    try {
      input = validateRequest(req.body);
    } catch {
      sendApiError(res, 400, "INVALID_REQUEST", "Invalid publish payload");
      return;
    }

    try {
      const db = getFirestore();
      const documentRef = db.collection("documents").doc(docId);

      const [sellerSnapshot, documentSnapshot] =
        await Promise.all([
          db.collection("users").doc(uid).get(),
          documentRef.get()
        ]);

      if (sellerSnapshot.data()?.sellerEnabled !== true) {
        sendApiError(res, 403, "FORBIDDEN", "Seller permission required");
        return;
      }

      if (!documentSnapshot.exists) {
        sendApiError(res, 404, "NOT_FOUND", "Document not found");
        return;
      }

      const draft = documentSnapshot.data()!;

      if (draft.sellerId !== uid) {
        sendApiError(res, 403, "FORBIDDEN", "Not document owner");
        return;
      }

      if (
        draft.status === "PUBLISHED" &&
        draft.activeVersionId === input.versionId
      ) {
        res.status(200).json({
          docId,
          versionId: input.versionId,
          status: "PUBLISHED",
          alreadyPublished: true
        });
        return;
      }

      if (
        draft.status !== "DRAFT" ||
        draft.activeVersionId !== input.versionId
      ) {
        throw new PublishConflict("Document is not a matching draft");
      }

      const bucketName = (process.env.UNISHARE_STORAGE_BUCKET ?? process.env.FIREBASE_STORAGE_BUCKET);

      if (!bucketName) {
        throw new Error("FIREBASE_STORAGE_BUCKET is required");
      }

      const prefix =
        `staging/${uid}/${docId}/${input.versionId}`;

      const pdf = await verifyStagingFile(
        bucketName,
        `${prefix}/pdf/source.pdf`,
        `private/${docId}/${input.versionId}/source.pdf`,
        input.pdfGeneration,
        "pdf"
      );

      const previews: VerifiedFile[] = [];

      for (const item of input.previews) {
        previews.push(
          await verifyStagingFile(
            bucketName,
            `${prefix}/previews/${item.slot}.${item.extension}`,
            `previews/${docId}/${input.versionId}/${item.slot}.${item.extension}`,
            item.generation,
            "image",
            item.extension
          )
        );
      }

      // If a previous attempt copied some files, verify
      // their bytes and resume instead of overwriting.
      const savedPdf = await saveVerifiedFile(bucketName, pdf);

      const savedPreviews: SavedFile[] = [];

      for (const preview of previews) {
        savedPreviews.push(
          await saveVerifiedFile(bucketName, preview)
        );
      }

      const previewRefs = savedPreviews.map(
        file => file.destinationPath
      );

      const versionRef = documentRef
        .collection("versions")
        .doc(input.versionId);

      const catalogRef = db.collection("catalog").doc(docId);

      const outcome = await db.runTransaction(async transaction => {
        const current = await transaction.get(documentRef);
        const existingVersion = await transaction.get(versionRef);
        const existingCatalog = await transaction.get(catalogRef);

        if (!current.exists || current.data()?.sellerId !== uid) {
          throw new PublishConflict("Document ownership changed");
        }

        const data = current.data()!;

        if (
          data.activeVersionId !== input.versionId ||
          data.status === "ARCHIVED"
        ) {
          throw new PublishConflict("Document version/status changed");
        }

        if (data.status === "PUBLISHED") {
          return "ALREADY_PUBLISHED";
        }

        if (
          data.status !== "DRAFT" ||
          existingVersion.exists ||
          existingCatalog.exists
        ) {
          throw new PublishConflict("Publish state conflict");
        }

        transaction.create(versionRef, {
          pdfPath: savedPdf.destinationPath,
          pdfGeneration: savedPdf.destinationGeneration,
          sha256: savedPdf.sha256,
          previewPaths: previewRefs,
          sizeBytes: savedPdf.bytes.length,
          readyAt: FieldValue.serverTimestamp()
        });

        transaction.update(documentRef, {
          status: "PUBLISHED",
          updatedAt: FieldValue.serverTimestamp()
        });

        transaction.create(catalogRef, {
          title: data.title,
          titleNormalized: normalizeTitle(data.title),
          subjectId: data.subject,
          categoryId: data.category,
          priceVnd: data.priceVnd,
          sellerId: uid,
          previewRefs,
          publishedAt: FieldValue.serverTimestamp()
        });

        return "PUBLISHED";
      });

      res.status(200).json({
        docId,
        versionId: input.versionId,
        status: "PUBLISHED",
        alreadyPublished: outcome === "ALREADY_PUBLISHED"
      });
    } catch (error) {
      if (error instanceof PublishConflict) {
        sendApiError(res, 409, "INVALID_REQUEST", error.message);
        return;
      }

      console.error("Publish failed", error);

      sendApiError(
        res,
        503,
        "SERVICE_UNAVAILABLE",
        "Unable to publish document"
      );
    }
  }
);