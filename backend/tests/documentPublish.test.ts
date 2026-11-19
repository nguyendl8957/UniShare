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

async function createValidPdf(): Promise<Buffer> {
  const document = await PDFDocument.create();
  document.addPage([595, 842]);

  return Buffer.from(await document.save());
}

async function createValidPng(): Promise<Buffer> {
  return sharp({
    create: {
      width: 100,
      height: 100,
      channels: 3,
      background: { r: 255, g: 255, b: 255 }
    }
  }).png().toBuffer();
}

test("A04 publish integration", async (t) => {
  assert.equal(process.env.FIREBASE_PROJECT_ID, "demo-unishare");
  assert.equal(
    process.env.FIREBASE_AUTH_EMULATOR_HOST,
    "127.0.0.1:9099"
  );
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

  await Promise.all([
    db.collection("users").doc(sellerA.uid).set({
      sellerEnabled: true,
      displayName: "A04 Seller A"
    }),
    db.collection("users").doc(sellerB.uid).set({
      sellerEnabled: true,
      displayName: "A04 Seller B"
    })
  ]);

  const draftResponse = await request(app)
    .post("/v1/documents/drafts")
    .set("Authorization", `Bearer ${sellerA.token}`)
    .set("Idempotency-Key", randomUUID())
    .send({
      title: "A04 Kotlin Test Document",
      subject: "programming",
      category: "note",
      description: "Integration fixture",
      priceVnd: 20000
    });

  assert.equal(draftResponse.status, 201);

  const { docId, versionId, stagingPrefix } =
    draftResponse.body as {
      docId: string;
      versionId: string;
      stagingPrefix: string;
    };

  const pdfFile = bucket.file(`${stagingPrefix}pdf/source.pdf`);
  const pngFile = bucket.file(`${stagingPrefix}previews/1.png`);

  await pdfFile.save(await createValidPdf(), {
    metadata: { contentType: "application/pdf" }
  });

  await pngFile.save(await createValidPng(), {
    metadata: { contentType: "image/png" }
  });

  const [pdfMetadata] = await pdfFile.getMetadata();
  const [pngMetadata] = await pngFile.getMetadata();

  const payload = {
    versionId,
    pdfGeneration: String(pdfMetadata.generation),
    previews: [
      {
        slot: 1,
        extension: "png",
        generation: String(pngMetadata.generation)
      }
    ]
  };

  await t.test("Seller B cannot publish Seller A document", async () => {
    const response = await request(app)
      .post(`/v1/documents/${docId}/publish`)
      .set("Authorization", `Bearer ${sellerB.token}`)
      .send(payload);

    assert.equal(response.status, 403);
  });

  await t.test("Draft is not yet in catalog", async () => {
    const catalog = await db.collection("catalog").doc(docId).get();
    assert.equal(catalog.exists, false);
  });

  await t.test("Seller A publishes valid PDF and PNG", async () => {
    const response = await request(app)
      .post(`/v1/documents/${docId}/publish`)
      .set("Authorization", `Bearer ${sellerA.token}`)
      .send(payload);

    assert.equal(response.status, 200);
    assert.equal(response.body.status, "PUBLISHED");
    assert.equal(response.body.alreadyPublished, false);
  });

  await t.test("Published document appears in catalog", async () => {
    const document = await db.collection("documents").doc(docId).get();
    const catalog = await db.collection("catalog").doc(docId).get();
    const version = await db
      .collection("documents")
      .doc(docId)
      .collection("versions")
      .doc(versionId)
      .get();

    assert.equal(document.data()?.status, "PUBLISHED");
    assert.equal(catalog.exists, true);
    assert.equal(version.exists, true);
    assert.equal(catalog.data()?.priceVnd, 20000);

    const [privateExists] = await bucket
      .file(`private/${docId}/${versionId}/source.pdf`)
      .exists();

    assert.equal(privateExists, true);
  });

  await t.test("Publish retry does not duplicate listing", async () => {
    const response = await request(app)
      .post(`/v1/documents/${docId}/publish`)
      .set("Authorization", `Bearer ${sellerA.token}`)
      .send(payload);

    assert.equal(response.status, 200);
    assert.equal(response.body.alreadyPublished, true);
  });
});