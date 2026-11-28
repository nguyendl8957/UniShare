import { test } from "node:test";
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import request from "supertest";
import { getFirestore } from "firebase-admin/firestore";

import { app } from "../src/app";
import { initializeFirebaseAdmin } from "../src/shared/firebaseAuth";

test("A08 AI chat authentication and entitlement", async (t) => {
  // Never allow this integration test to access production.
  assert.equal(process.env.FIREBASE_PROJECT_ID, "demo-unishare");
  assert.equal(
    process.env.FIREBASE_AUTH_EMULATOR_HOST,
    "127.0.0.1:9099"
  );
  assert.equal(
    process.env.FIRESTORE_EMULATOR_HOST,
    "127.0.0.1:8080"
  );

  initializeFirebaseAdmin();

  // Create a real Firebase Auth test token in Auth Emulator.
  const signup = await fetch(
    "http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key",
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ returnSecureToken: true })
    }
  );

  assert.equal(signup.status, 200);

  const user = (await signup.json()) as {
    localId: string;
    idToken: string;
  };

  const uid = user.localId;
  const token = user.idToken;
  const db = getFirestore();

  const suffix = randomUUID().replace(/-/g, "");
  const docId = `a08_doc_${suffix}`;
  const versionId = "v1";

  const entitlementRef = db
    .collection("entitlements")
    .doc(uid)
    .collection("items")
    .doc(docId);

  const versionRef = db
    .collection("documents")
    .doc(docId)
    .collection("versions")
    .doc(versionId);

  const endpoint = "/v1/ai/chat";

  const validBody = {
    docId,
    question: "Hãy tóm tắt tài liệu này.",
    clientMessageId: randomUUID()
  };

  try {
    await t.test("401 - missing Firebase token", async () => {
      const res = await request(app)
        .post(endpoint)
        .send(validBody);

      assert.equal(res.status, 401);
      assert.equal(res.body.error.code, "UNAUTHENTICATED");
    });

    await t.test("400 - empty question", async () => {
      const res = await request(app)
        .post(endpoint)
        .set("Authorization", `Bearer ${token}`)
        .send({ ...validBody, question: "   " });

      assert.equal(res.status, 400);
      assert.equal(res.body.error.code, "INVALID_REQUEST");
    });

    await t.test("400 - question exceeds 500 characters", async () => {
      const res = await request(app)
        .post(endpoint)
        .set("Authorization", `Bearer ${token}`)
        .send({ ...validBody, question: "x".repeat(501) });

      assert.equal(res.status, 400);
      assert.equal(res.body.error.code, "INVALID_REQUEST");
    });

    await t.test("403 - buyer has no entitlement", async () => {
      const res = await request(app)
        .post(endpoint)
        .set("Authorization", `Bearer ${token}`)
        .send(validBody);

      assert.equal(res.status, 403);
      assert.equal(res.body.error.code, "FORBIDDEN");
    });

    // Valid entitlement, but referenced PDF version does not exist.
    await entitlementRef.set({
      orderId: "ord_a08_test_123456",
      versionId,
      grantedAt: new Date().toISOString()
    });

    await t.test("404 - missing document version", async () => {
      const res = await request(app)
        .post(endpoint)
        .set("Authorization", `Bearer ${token}`)
        .send(validBody);

      assert.equal(res.status, 404);
      assert.equal(res.body.error.code, "NOT_FOUND");
    });

    // Add an immutable PDF version fixture.
    await versionRef.set({
      pdfPath: `private/${docId}/${versionId}/source.pdf`,
      pdfGeneration: "123456789",
      sizeBytes: 1024
    });

    await t.test("503 - authorized, AI disabled", async () => {
      delete process.env.UNISHARE_AI_ENABLED;
      const res = await request(app)
        .post(endpoint)
        .set("Authorization", `Bearer ${token}`)
        .send(validBody);

      assert.equal(res.status, 503);
      assert.equal(res.body.error.code, "AI_DISABLED");
    });

    await entitlementRef.update({
      revokedAt: new Date().toISOString()
    });

    await t.test("403 - revoked entitlement", async () => {
      const res = await request(app)
        .post(endpoint)
        .set("Authorization", `Bearer ${token}`)
        .send(validBody);

      assert.equal(res.status, 403);
      assert.equal(res.body.error.code, "FORBIDDEN");
    });
  } finally {
    await Promise.allSettled([
      entitlementRef.delete(),
      versionRef.delete()
    ]);
  }
});