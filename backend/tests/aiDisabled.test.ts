import { test } from "node:test";
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import express from "express";
import request from "supertest";
import { getFirestore } from "firebase-admin/firestore";

import { initializeFirebaseAdmin } from "../src/shared/firebaseAuth";
import { createAiChatRouter } from "../src/ai/chatRoute";

test("A08 AI disabled by default", async (t) => {
  assert.equal(process.env.FIREBASE_PROJECT_ID, "demo-unishare");
  assert.equal(process.env.FIRESTORE_EMULATOR_HOST, "127.0.0.1:8080");
  assert.equal(process.env.FIREBASE_AUTH_EMULATOR_HOST, "127.0.0.1:9099");

  initializeFirebaseAdmin();

  const signup = await fetch(
    "http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key",
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ returnSecureToken: true })
    }
  );

  assert.equal(signup.status, 200);

  const user = await signup.json() as {
    localId: string;
    idToken: string;
  };

  const docId = `a08disabled_${randomUUID().replace(/-/g, "")}`;
  const db = getFirestore();

  const versionRef = db.collection("documents")
    .doc(docId).collection("versions").doc("v1");

  const entitlementRef = db.collection("entitlements")
    .doc(user.localId).collection("items").doc(docId);

  const app = express();
  app.use(express.json());
  app.use("/v1/ai", createAiChatRouter());

  const previousFlag = process.env.UNISHARE_AI_ENABLED;
  const previousKey = process.env.GEMINI_API_KEY;
  const previousAllowlist = process.env.UNISHARE_AI_TEST_UIDS;

  try {
    await versionRef.set({
      pdfPath: `private/${docId}/v1/source.pdf`,
      pdfGeneration: "1",
      sizeBytes: 100
    });

    await entitlementRef.set({
      orderId: "ord_a08_disabled_test",
      versionId: "v1",
      grantedAt: new Date().toISOString()
    });

    // Deliberately fake key: this test must never call Google.
    process.env.GEMINI_API_KEY = "FAKE_TEST_KEY_ONLY";

    async function ask() {
      return request(app)
        .post("/v1/ai/chat")
        .set("Authorization", `Bearer ${user.idToken}`)
        .send({
          docId,
          question: "Tóm tắt tài liệu",
          clientMessageId: "msg_disabled_test"
        });
    }

    await t.test("503 when flag is absent", async () => {
      delete process.env.UNISHARE_AI_ENABLED;
      const res = await ask();

      assert.equal(res.status, 503, JSON.stringify(res.body));
      assert.equal(res.body.answer, undefined);
    });

    await t.test("503 when flag is false", async () => {
      process.env.UNISHARE_AI_ENABLED = "false";
      const res = await ask();

      assert.equal(res.status, 503, JSON.stringify(res.body));
      assert.equal(res.body.answer, undefined);
    });
    await t.test("503 when enabled but UID is not allowlisted", async () => {
      process.env.UNISHARE_AI_ENABLED = "true";
      process.env.UNISHARE_AI_TEST_UIDS = "another_test_uid";

      const res = await ask();

      assert.equal(res.status, 503, JSON.stringify(res.body));
      assert.equal(res.body.answer, undefined);
    });
  } finally {
    if (previousFlag === undefined) {
      delete process.env.UNISHARE_AI_ENABLED;
    } else {
      process.env.UNISHARE_AI_ENABLED = previousFlag;
    }

    if (previousKey === undefined) {
      delete process.env.GEMINI_API_KEY;
    } else {
      process.env.GEMINI_API_KEY = previousKey;
    }

    if (previousAllowlist === undefined) {
      delete process.env.UNISHARE_AI_TEST_UIDS;
    } else {
      process.env.UNISHARE_AI_TEST_UIDS = previousAllowlist;
    }
    await Promise.all([
      entitlementRef.delete(),
      versionRef.delete()
    ]);
  }
});