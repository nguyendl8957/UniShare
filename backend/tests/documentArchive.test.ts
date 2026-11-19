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

async function createUser() {
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

  return { uid: data.localId, token: data.idToken };
}

test("A04 archive integration", async (t) => {
  assert.equal(process.env.FIREBASE_PROJECT_ID, "demo-unishare");
  assert.equal(
    process.env.FIRESTORE_EMULATOR_HOST,
    "127.0.0.1:8080"
  );
  assert.equal(
    process.env.FIREBASE_STORAGE_EMULATOR_HOST,
    "127.0.0.1:9199"
  );
  assert.equal(process.env.FIREBASE_STORAGE_BUCKET, BUCKET);

  initializeFirebaseAdmin();

  const db = getFirestore();
  const bucket = getStorage().bucket(BUCKET);

  const sellerA = await createUser();
  const sellerB = await createUser();

  for (const seller of [sellerA, sellerB]) {
    await db.collection("users").doc(seller.uid).set({
      sellerEnabled: true,
      displayName: "Archive Test Seller"
    });
  }

  // Create a real draft through the backend.
  const draft = await request(app)
    .post("/v1/documents/drafts")
    .set("Authorization", `Bearer ${sellerA.token}`)
    .set("Idempotency-Key", randomUUID())
    .send({
      title: "A04 Archive Test Document",
      subject: "programming",
      category: "note",
      priceVnd: 20000
    });

  assert.equal(draft.status, 201);

  const { docId, versionId, stagingPrefix } =
    draft.body as {
      docId: string;
      versionId: string;
      stagingPrefix: string;
    };

  const pdf = await PDFDocument.create();
  pdf.addPage([595, 842]);
  const pdfBytes = Buffer.from(await pdf.save());

  const imageBytes = await sharp({
    create: {
      width: 80,
      height: 80,
      channels: 3,
      background: { r: 255, g: 255, b: 255 }
    }
  }).png().toBuffer();

  const pdfFile = bucket.file(`${stagingPrefix}pdf/source.pdf`);
  const imageFile = bucket.file(`${stagingPrefix}previews/1.png`);

  await pdfFile.save(pdfBytes, {
    resumable: false,
    metadata: { contentType: "application/pdf" }
  });

  await imageFile.save(imageBytes, {
    resumable: false,
    metadata: { contentType: "image/png" }
  });

  const [pdfMetadata] = await pdfFile.getMetadata();
  const [imageMetadata] = await imageFile.getMetadata();

  const publish = await request(app)
    .post(`/v1/documents/${docId}/publish`)
    .set("Authorization", `Bearer ${sellerA.token}`)
    .send({
      versionId,
      pdfGeneration: String(pdfMetadata.generation),
      previews: [{
        slot: 1,
        extension: "png",
        generation: String(imageMetadata.generation)
      }]
    });

  assert.equal(publish.status, 200);

  const archiveUrl = `/v1/documents/${docId}`;

  await t.test("Published listing exists before archive", async () => {
    const catalog = await db.collection("catalog").doc(docId).get();
    assert.equal(catalog.exists, true);
  });

  await t.test("Guest cannot archive", async () => {
    const response = await request(app)
      .patch(archiveUrl)
      .send({ action: "archive", expectedVersionId: versionId });

    assert.equal(response.status, 401);
  });

  await t.test("Seller B cannot archive Seller A document", async () => {
    const response = await request(app)
      .patch(archiveUrl)
      .set("Authorization", `Bearer ${sellerB.token}`)
      .send({ action: "archive", expectedVersionId: versionId });

    assert.equal(response.status, 403);
  });

  await t.test("Wrong version is rejected", async () => {
    const response = await request(app)
      .patch(archiveUrl)
      .set("Authorization", `Bearer ${sellerA.token}`)
      .send({ action: "archive", expectedVersionId: "v999" });

    assert.equal(response.status, 409);
  });

  await t.test("Seller A archives successfully", async () => {
    const response = await request(app)
      .patch(archiveUrl)
      .set("Authorization", `Bearer ${sellerA.token}`)
      .send({ action: "archive", expectedVersionId: versionId });

    assert.equal(response.status, 200);
    assert.equal(response.body.status, "ARCHIVED");
    assert.equal(response.body.alreadyArchived, false);
  });

  await t.test("Catalog removed but PDF version retained", async () => {
    const document = await db.collection("documents").doc(docId).get();
    const catalog = await db.collection("catalog").doc(docId).get();

    const version = await db.collection("documents")
      .doc(docId)
      .collection("versions")
      .doc(versionId)
      .get();

    const [pdfExists] = await bucket
      .file(`private/${docId}/${versionId}/source.pdf`)
      .exists();

    assert.equal(document.data()?.status, "ARCHIVED");
    assert.equal(catalog.exists, false);
    assert.equal(version.exists, true);
    assert.equal(pdfExists, true);
  });

  await t.test("Retry archive is idempotent", async () => {
    const response = await request(app)
      .patch(archiveUrl)
      .set("Authorization", `Bearer ${sellerA.token}`)
      .send({ action: "archive", expectedVersionId: versionId });

    assert.equal(response.status, 200);
    assert.equal(response.body.alreadyArchived, true);
  });

  await t.test("Archived document cannot be published again", async () => {
    const response = await request(app)
      .post(`/v1/documents/${docId}/publish`)
      .set("Authorization", `Bearer ${sellerA.token}`)
      .send({
        versionId,
        pdfGeneration: String(pdfMetadata.generation),
        previews: [{
          slot: 1,
          extension: "png",
          generation: String(imageMetadata.generation)
        }]
      });

    assert.notEqual(response.status, 200);
  });
});