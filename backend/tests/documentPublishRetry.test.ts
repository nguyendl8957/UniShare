import { test } from "node:test";
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import request from "supertest";
import { PDFDocument } from "pdf-lib";
import sharp from "sharp";

import { getFirestore } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";

import { app } from "../src/app";
import { initializeFirebaseAdmin } from "../src/shared/firebaseAuth";

const BUCKET = "demo-unishare.firebasestorage.app";

async function createSeller() {
  const response = await fetch(
    "http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key",
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ returnSecureToken: true })
    }
  );

  assert.equal(response.status, 200);

  const data = await response.json() as {
    localId: string;
    idToken: string;
  };

  await getFirestore()
    .collection("users")
    .doc(data.localId)
    .set({
      sellerEnabled: true,
      displayName: "A04 Retry Test Seller"
    });

  return {
    uid: data.localId,
    token: data.idToken
  };
}

async function prepareDraft(token: string) {
  const draft = await request(app)
    .post("/v1/documents/drafts")
    .set("Authorization", `Bearer ${token}`)
    .set("Idempotency-Key", randomUUID())
    .send({
      title: "A04 Retry Test Document",
      subject: "programming",
      category: "note",
      priceVnd: 20000
    });

  assert.equal(draft.status, 201);

  const {
    docId,
    versionId,
    stagingPrefix
  } = draft.body as {
    docId: string;
    versionId: string;
    stagingPrefix: string;
  };

  const pdf = await PDFDocument.create();
  pdf.addPage([595, 842]);

  const pdfBytes = Buffer.from(await pdf.save());

  const pngBytes = await sharp({
    create: {
      width: 80,
      height: 80,
      channels: 3,
      background: { r: 255, g: 255, b: 255 }
    }
  }).png().toBuffer();

  const bucket = getStorage().bucket(BUCKET);

  const pdfFile = bucket.file(
    `${stagingPrefix}pdf/source.pdf`
  );

  const pngFile = bucket.file(
    `${stagingPrefix}previews/1.png`
  );

  await pdfFile.save(pdfBytes, {
    resumable: false,
    metadata: { contentType: "application/pdf" }
  });

  await pngFile.save(pngBytes, {
    resumable: false,
    metadata: { contentType: "image/png" }
  });

  const [pdfMeta] = await pdfFile.getMetadata();
  const [pngMeta] = await pngFile.getMetadata();

  return {
    docId,
    versionId,
    pdfBytes,
    payload: {
      versionId,
      pdfGeneration: String(pdfMeta.generation),
      previews: [{
        slot: 1,
        extension: "png",
        generation: String(pngMeta.generation)
      }]
    }
  };
}

test("A04 interrupted publish retry", async (t) => {
  assert.equal(
    process.env.FIREBASE_PROJECT_ID,
    "demo-unishare"
  );
  assert.equal(
    process.env.FIREBASE_STORAGE_BUCKET,
    BUCKET
  );
  assert.equal(
    process.env.FIREBASE_STORAGE_EMULATOR_HOST,
    "127.0.0.1:9199"
  );
  assert.equal(
    process.env.FIRESTORE_EMULATOR_HOST,
    "127.0.0.1:8080"
  );

  initializeFirebaseAdmin();

  const seller = await createSeller();
  const db = getFirestore();
  const bucket = getStorage().bucket(BUCKET);

  await t.test(
    "Retry resumes after PDF was copied",
    async () => {
      const draft = await prepareDraft(seller.token);

      const privateFile = bucket.file(
        `private/${draft.docId}/${draft.versionId}/source.pdf`
      );

      // Simulate a prior publish that copied PDF
      // but stopped before creating catalog/version.
      await privateFile.save(draft.pdfBytes, {
        resumable: false,
        metadata: {
          contentType: "application/pdf"
        }
      });

      const before = await db
        .collection("catalog")
        .doc(draft.docId)
        .get();

      assert.equal(before.exists, false);

      const response = await request(app)
        .post(`/v1/documents/${draft.docId}/publish`)
        .set("Authorization", `Bearer ${seller.token}`)
        .send(draft.payload);

      assert.equal(response.status, 200);
      assert.equal(response.body.status, "PUBLISHED");

      const catalog = await db
        .collection("catalog")
        .doc(draft.docId)
        .get();

      const version = await db
        .collection("documents")
        .doc(draft.docId)
        .collection("versions")
        .doc(draft.versionId)
        .get();

      assert.equal(catalog.exists, true);
      assert.equal(version.exists, true);

      const [actualBytes] = await privateFile.download();

      assert.deepEqual(actualBytes, draft.pdfBytes);
    }
  );

  await t.test(
    "Different destination bytes block publish",
    async () => {
      const draft = await prepareDraft(seller.token);

      const privateFile = bucket.file(
        `private/${draft.docId}/${draft.versionId}/source.pdf`
      );

      // Destination occupied by different content.
      await privateFile.save(
        Buffer.from("INVALID_DESTINATION_BYTES"),
        {
          resumable: false,
          metadata: {
            contentType: "application/pdf"
          }
        }
      );

      const response = await request(app)
        .post(`/v1/documents/${draft.docId}/publish`)
        .set("Authorization", `Bearer ${seller.token}`)
        .send(draft.payload);

      assert.equal(response.status, 409);

      const catalog = await db
        .collection("catalog")
        .doc(draft.docId)
        .get();

      const document = await db
        .collection("documents")
        .doc(draft.docId)
        .get();

      assert.equal(catalog.exists, false);
      assert.equal(document.data()?.status, "DRAFT");
    }
  );
});