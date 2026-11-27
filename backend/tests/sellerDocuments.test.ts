import { test } from "node:test";
import assert from "node:assert/strict";
import request from "supertest";
import { getFirestore } from "firebase-admin/firestore";

import { app } from "../src/app";
import { initializeFirebaseAdmin } from "../src/shared/firebaseAuth";

async function signUp() {
  const response = await fetch(
    "http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        returnSecureToken: true
      })
    }
  );

  assert.equal(response.status, 200);

  return await response.json() as {
    localId: string;
    idToken: string;
  };
}

test("A09 Seller documents security", async (t) => {
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

  const db = getFirestore();
  const sellerA = await signUp();
  const sellerB = await signUp();

  const userA = db.collection("users").doc(sellerA.localId);
  const userB = db.collection("users").doc(sellerB.localId);

  const docA = db.collection("documents").doc(
    "a09_a_" + sellerA.localId
  );
  const docB = db.collection("documents").doc(
    "a09_b_" + sellerB.localId
  );

  try {
    await userA.set({ sellerEnabled: false });
    await userB.set({ sellerEnabled: true });

    await docA.set({
      sellerId: sellerA.localId,
      title: "Seller A document",
      category: "notes",
      subject: "programming",
      description: "Private ownership test",
      priceVnd: 15000,
      status: "PUBLISHED",
      activeVersionId: "v1"
    });

    await docB.set({
      sellerId: sellerB.localId,
      title: "Seller B document",
      category: "notes",
      subject: "math",
      priceVnd: 20000,
      status: "DRAFT",
      activeVersionId: "v1"
    });

    await t.test("401 without token", async () => {
      const res = await request(app)
        .get("/v1/seller/documents");

      assert.equal(res.status, 401);
    });

    await t.test("403 Buyer without Seller permission", async () => {
      const res = await request(app)
        .get("/v1/seller/documents")
        .set(
          "Authorization",
          `Bearer ${sellerA.idToken}`
        );

      assert.equal(res.status, 403);
    });

    await t.test("200 Seller can list own documents", async () => {
      const res = await request(app)
        .get("/v1/seller/documents")
        .set(
          "Authorization",
          `Bearer ${sellerB.idToken}`
        );

      assert.equal(res.status, 200, JSON.stringify(res.body));
      assert.equal(res.body.documents.length, 1);
      assert.equal(res.body.documents[0].docId, docB.id);
      assert.equal(res.body.documents[0].status, "DRAFT");
      assert.equal(res.body.truncated, false);
    });

    await t.test("Seller cannot access another UID documents", async () => {
      await userA.update({ sellerEnabled: true });

      const res = await request(app)
        .get("/v1/seller/documents")
        .query({ sellerId: sellerB.localId })
        .set(
          "Authorization",
          `Bearer ${sellerA.idToken}`
        );

      assert.equal(res.status, 200, JSON.stringify(res.body));
      assert.equal(res.body.documents.length, 1);
      assert.equal(res.body.documents[0].docId, docA.id);
      assert.notEqual(res.body.documents[0].docId, docB.id);
    });

    console.log("PASS: Seller documents ownership isolation");
  } finally {
    await Promise.allSettled([
      docA.delete(),
      docB.delete(),
      userA.delete(),
      userB.delete()
    ]);
  }
});