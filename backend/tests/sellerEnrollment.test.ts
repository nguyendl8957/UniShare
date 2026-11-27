import { test } from "node:test";
import assert from "node:assert/strict";
import request from "supertest";
import { getFirestore } from "firebase-admin/firestore";

import { app } from "../src/app";
import { initializeFirebaseAdmin } from "../src/shared/firebaseAuth";

type TestUser = {
  uid: string;
  token: string;
};

async function createTestUser(): Promise<TestUser> {
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

  const user = await response.json() as {
    localId: string;
    idToken: string;
  };

  return {
    uid: user.localId,
    token: user.idToken
  };
}

test("A09 Seller self-enrollment security", async (t) => {
  assert.equal(process.env.FIREBASE_PROJECT_ID, "demo-unishare");
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
  const buyer = await createTestUser();
  const other = await createTestUser();
  const missingProfileUser = await createTestUser();

  const buyerRef = db.collection("users").doc(buyer.uid);
  const otherRef = db.collection("users").doc(other.uid);

  try {
    await buyerRef.set({
      displayName: "Buyer Test",
      school: "ĐH Công nghệ Thông tin",
      faculty: "Khoa CNTT",
      sellerEnabled: false
    });

    await otherRef.set({
      displayName: "Other Test",
      school: "ĐH Công nghệ Thông tin",
      faculty: "Khoa CNTT",
      sellerEnabled: false
    });

    await t.test("401 - no authentication", async () => {
      const res = await request(app)
        .post("/v1/seller/enroll")
        .send({ acceptTerms: true });

      assert.equal(res.status, 401);
    });

    await t.test("400 - terms not accepted", async () => {
      const res = await request(app)
        .post("/v1/seller/enroll")
        .set("Authorization", `Bearer ${buyer.token}`)
        .send({ acceptTerms: false });

      assert.equal(res.status, 400);
    });

    await t.test("400 - cannot submit another UID", async () => {
      const res = await request(app)
        .post("/v1/seller/enroll")
        .set("Authorization", `Bearer ${buyer.token}`)
        .send({
          acceptTerms: true,
          uid: other.uid
        });

      assert.equal(res.status, 400);
    });

    await t.test("404 - missing profile", async () => {
      const res = await request(app)
        .post("/v1/seller/enroll")
        .set(
          "Authorization",
          `Bearer ${missingProfileUser.token}`
        )
        .send({ acceptTerms: true });

      assert.equal(res.status, 404);
    });

    await t.test("400 - incomplete profile", async () => {
      await buyerRef.update({
        school: ""
      });

      const res = await request(app)
        .post("/v1/seller/enroll")
        .set("Authorization", `Bearer ${buyer.token}`)
        .send({ acceptTerms: true });

      assert.equal(res.status, 400);

      const snapshot = await buyerRef.get();

      assert.equal(
        snapshot.get("sellerEnabled"),
        false
      );

      await buyerRef.update({
        school: "ĐH Công nghệ Thông tin"
      });
    });

    await t.test("200 - Buyer becomes Seller", async () => {
      const res = await request(app)
        .post("/v1/seller/enroll")
        .set("Authorization", `Bearer ${buyer.token}`)
        .send({ acceptTerms: true });

      assert.equal(res.status, 200, JSON.stringify(res.body));
      assert.equal(res.body.sellerEnabled, true);
      assert.equal(res.body.status, "ACTIVE");

      const snapshot = await buyerRef.get();

      assert.equal(snapshot.get("sellerEnabled"), true);

      assert.ok(snapshot.get("sellerTermsAcceptedAt"));

      const otherSnapshot = await otherRef.get();

      assert.equal(
        otherSnapshot.get("sellerEnabled"),
        false
      );
    });

    await t.test("200 - repeated enrollment is idempotent", async () => {
      const before = await buyerRef.get();
      const acceptedAtBefore = before
        .get("sellerTermsAcceptedAt")
        .toMillis();

      const res = await request(app)
        .post("/v1/seller/enroll")
        .set("Authorization", `Bearer ${buyer.token}`)
        .send({ acceptTerms: true });

      assert.equal(res.status, 200);
      assert.equal(res.body.sellerEnabled, true);

      const after = await buyerRef.get();

      assert.equal(
        after.get("sellerTermsAcceptedAt").toMillis(),
        acceptedAtBefore
      );
    });

    console.log("PASS: A09 Seller enrollment and UID isolation");
  } finally {
    await Promise.allSettled([
      buyerRef.delete(),
      otherRef.delete()
    ]);
  }
});