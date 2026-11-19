import { test } from "node:test";
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import request from "supertest";
import { getFirestore } from "firebase-admin/firestore";

import { app } from "../src/app";
import { initializeFirebaseAdmin } from "../src/shared/firebaseAuth";

const metadata = {
  title: "Tai lieu Kotlin A04",
  subject: "programming",
  category: "note",
  description: "Test draft on Firebase Emulator",
  priceVnd: 20000
};

async function createEmulatorUser(): Promise<{
  uid: string;
  token: string;
}> {
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

  return {
    uid: data.localId,
    token: data.idToken
  };
}

test("A04 draft API on Firebase Emulator", async (t) => {
  assert.equal(
    process.env.FIREBASE_PROJECT_ID,
    "demo-unishare"
  );
  assert.equal(
    process.env.FIRESTORE_EMULATOR_HOST,
    "127.0.0.1:8080"
  );
  assert.equal(
    process.env.FIREBASE_AUTH_EMULATOR_HOST,
    "127.0.0.1:9099"
  );

  initializeFirebaseAdmin();

  const buyer = await createEmulatorUser();
  const seller = await createEmulatorUser();

  // Admin SDK ghi fixture Seller, khong cap quyen tu client.
  await getFirestore()
    .collection("users")
    .doc(seller.uid)
    .set({
      sellerEnabled: true,
      displayName: "Seller A",
      school: "Test University",
      faculty: "IT"
    });

  const key = randomUUID();
  let createdDocId = "";

  await t.test("Guest returns 401", async () => {
    const res = await request(app)
      .post("/v1/documents/drafts")
      .set("Idempotency-Key", key)
      .send(metadata);

    assert.equal(res.status, 401);
  });

  await t.test("Buyer returns 403", async () => {
    const res = await request(app)
      .post("/v1/documents/drafts")
      .set("Authorization", `Bearer ${buyer.token}`)
      .set("Idempotency-Key", key)
      .send(metadata);

    assert.equal(res.status, 403);
  });

  await t.test("Seller creates draft 201", async () => {
    const res = await request(app)
      .post("/v1/documents/drafts")
      .set("Authorization", `Bearer ${seller.token}`)
      .set("Idempotency-Key", key)
      .send(metadata);

    assert.equal(res.status, 201);
    assert.equal(res.body.status, "DRAFT");
    assert.equal(res.body.versionId, "v1");

    createdDocId = res.body.docId;

    assert.equal(
      res.body.stagingPrefix,
      `staging/${seller.uid}/${createdDocId}/v1/`
    );
  });

  await t.test("Same key and payload returns 200", async () => {
    const res = await request(app)
      .post("/v1/documents/drafts")
      .set("Authorization", `Bearer ${seller.token}`)
      .set("Idempotency-Key", key)
      .send(metadata);

    assert.equal(res.status, 200);
    assert.equal(res.body.docId, createdDocId);
  });

  await t.test("Same key different payload returns 409", async () => {
    const res = await request(app)
      .post("/v1/documents/drafts")
      .set("Authorization", `Bearer ${seller.token}`)
      .set("Idempotency-Key", key)
      .send({
        ...metadata,
        priceVnd: 30000
      });

    assert.equal(res.status, 409);
  });
});