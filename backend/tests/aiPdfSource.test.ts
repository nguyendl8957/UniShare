import { test } from "node:test";
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { PDFDocument, StandardFonts } from "pdf-lib";

import { getFirestore } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";

import { initializeFirebaseAdmin } from "../src/shared/firebaseAuth";
import { DocumentAccessError } from "../src/documents/entitlement";
import {
  loadAuthorizedPdfText,
  AiPdfSourceError
} from "../src/ai/pdfSource";

const BUCKET = "demo-unishare.firebasestorage.app";

test("A08 private PDF loading and entitlement", async (t) => {
  // Hard stop if the test is not running against Emulators.
  assert.equal(process.env.FIREBASE_PROJECT_ID, "demo-unishare");
  assert.equal(
    process.env.FIRESTORE_EMULATOR_HOST,
    "127.0.0.1:8080"
  );
  assert.equal(
    process.env.FIREBASE_STORAGE_EMULATOR_HOST,
    "127.0.0.1:9199"
  );
  assert.equal(process.env.UNISHARE_STORAGE_BUCKET, BUCKET);

  initializeFirebaseAdmin();

  const suffix = randomUUID().replace(/-/g, "");
  const uid = `a08buyer_${suffix}`;
  const otherUid = `a08other_${suffix}`;
  const docId = `a08pdf_${suffix}`;
  const versionId = "v1";

  const db = getFirestore();
  const pdfPath = `private/${docId}/${versionId}/source.pdf`;

  const versionRef = db.collection("documents")
    .doc(docId)
    .collection("versions")
    .doc(versionId);

  const entitlementRef = db.collection("entitlements")
    .doc(uid)
    .collection("items")
    .doc(docId);

  const file = getStorage().bucket(BUCKET).file(pdfPath);

  try {
    const pdf = await PDFDocument.create();
    const font = await pdf.embedFont(StandardFonts.Helvetica);

    const page = pdf.addPage([595, 842]);
    page.drawText("UniShare AI private PDF example.", {
      x: 45,
      y: 750,
      size: 14,
      font
    });

    const pdfBytes = Buffer.from(await pdf.save());

    await file.save(pdfBytes, {
      resumable: false,
      metadata: { contentType: "application/pdf" }
    });

    const [metadata] = await file.getMetadata();

    await versionRef.set({
      pdfPath,
      pdfGeneration: String(metadata.generation),
      sizeBytes: pdfBytes.length
    });

    await t.test("403 - other Buyer denied", async () => {
      await assert.rejects(
        loadAuthorizedPdfText(otherUid, docId, db),
        (error: unknown) =>
          error instanceof DocumentAccessError &&
          error.statusCode === 403
      );
    });

    await entitlementRef.set({
      orderId: "ord_a08_private_pdf_test",
      versionId,
      grantedAt: new Date().toISOString()
    });

    await t.test("authorized Buyer reads PDF text", async () => {
      const pages = await loadAuthorizedPdfText(uid, docId, db);

      assert.equal(pages.length, 1);
      assert.equal(pages[0].page, 1);
      assert.match(
        pages[0].text,
        /UniShare AI private PDF example/
      );
    });

    await t.test("PDF size mismatch is rejected", async () => {
      await versionRef.update({
        sizeBytes: pdfBytes.length + 1
      });

      await assert.rejects(
        loadAuthorizedPdfText(uid, docId, db),
        (error: unknown) =>
          error instanceof AiPdfSourceError &&
          error.code === "PDF_VERSION_MISMATCH"
      );

      await versionRef.update({
        sizeBytes: pdfBytes.length
      });
    });

    await t.test("revoked entitlement is denied", async () => {
      await entitlementRef.update({
        revokedAt: new Date().toISOString()
      });

      await assert.rejects(
        loadAuthorizedPdfText(uid, docId, db),
        (error: unknown) =>
          error instanceof DocumentAccessError &&
          error.statusCode === 403
      );
    });
  } finally {
    await Promise.allSettled([
      file.delete(),
      entitlementRef.delete(),
      versionRef.delete()
    ]);
  }
});