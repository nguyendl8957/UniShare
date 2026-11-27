import { test } from "node:test";
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import express from "express";
import request from "supertest";
import { PDFDocument, StandardFonts } from "pdf-lib";
import { getFirestore } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";

import { initializeFirebaseAdmin } from "../src/shared/firebaseAuth";
import { createAiChatRouter } from "../src/ai/chatRoute";
import { createGeminiProvider } from "../src/ai/geminiProvider";
import { utcDay } from "../src/ai/quota";
import type { AiProvider } from "../src/ai/provider";

test("A08 real Gemini HTTP E2E with synthetic PDF", async () => {
  // Absolutely never run this fixture against production Firebase.
  assert.equal(process.env.FIREBASE_PROJECT_ID, "demo-unishare");
  assert.equal(process.env.FIRESTORE_EMULATOR_HOST, "127.0.0.1:8080");
  assert.equal(process.env.FIREBASE_AUTH_EMULATOR_HOST, "127.0.0.1:9099");
  assert.equal(process.env.FIREBASE_STORAGE_EMULATOR_HOST, "127.0.0.1:9199");
  assert.equal(
    process.env.UNISHARE_STORAGE_BUCKET,
    "demo-unishare.firebasestorage.app"
  );

  const key = process.env.GEMINI_API_KEY;
  assert.ok(key, "Gemini API key must be configured");

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

  const suffix = randomUUID().replace(/-/g, "");
  const uid = user.localId;
  const docId = `a08live_${suffix}`;
  const messageId = `msg_${suffix}`;
  const db = getFirestore();

  const versionRef = db.collection("documents")
    .doc(docId).collection("versions").doc("v1");

  const entitlementRef = db.collection("entitlements")
    .doc(uid).collection("items").doc(docId);

  const usageRef = db.collection("aiUsage").doc(uid);
  const dayRef = usageRef.collection("days").doc(utcDay());
  const requestsRef = usageRef.collection("requests").doc(messageId);

  const pdfPath = `private/${docId}/v1/source.pdf`;
  const file = getStorage()
    .bucket("demo-unishare.firebasestorage.app")
    .file(pdfPath);

  let calls = 0;
  const real = createGeminiProvider(key);

  const countingProvider: AiProvider = {
    async ask(input) {
      calls++;
      if (calls > 1) {
        throw new Error("STOP: Unexpected second Gemini call");
      }
      return real.ask(input);
    }
  };

  const app = express();
  app.use(express.json());
  app.use("/v1/ai", createAiChatRouter(countingProvider));

  const payload = {
    docId,
    question: "Theo tài liệu, độ phức tạp tìm kiếm nhị phân là gì?",
    clientMessageId: messageId
  };

  try {
    const pdf = await PDFDocument.create();
    const font = await pdf.embedFont(StandardFonts.Helvetica);
    const page = pdf.addPage([595, 842]);

    page.drawText(
      "Binary search time complexity is O(log n).",
      { x: 40, y: 750, size: 14, font }
    );

    const bytes = Buffer.from(await pdf.save());

    await file.save(bytes, {
      resumable: false,
      metadata: { contentType: "application/pdf" }
    });

    const [metadata] = await file.getMetadata();

    await versionRef.set({
      pdfPath,
      pdfGeneration: String(metadata.generation),
      sizeBytes: bytes.length
    });

    await entitlementRef.set({
      orderId: "ord_a08_live_emulator",
      versionId: "v1",
      grantedAt: new Date().toISOString()
    });

    console.log("Sending one real Gemini request...");

    const first = await request(app)
      .post("/v1/ai/chat")
      .set("Authorization", `Bearer ${user.idToken}`)
      .send(payload);

    assert.equal(
      first.status, 200,
      JSON.stringify(first.body)
    );

    assert.match(first.body.answer, /log/i);
    assert.ok(
      first.body.citations.some(
        (citation: { page: number }) => citation.page === 1
      )
    );
    assert.equal(calls, 1);

    const usage = await dayRef.get();
    assert.equal(usage.get("requests"), 1);
    assert.ok(usage.get("usedTokens") > 0);

    // Reuses the cached answer, must not call Gemini again.
    const cached = await request(app)
      .post("/v1/ai/chat")
      .set("Authorization", `Bearer ${user.idToken}`)
      .send(payload);

    assert.equal(
      cached.status, 200,
      JSON.stringify(cached.body)
    );
    assert.deepEqual(cached.body, first.body);
    assert.equal(calls, 1);

    console.log("PASS: HTTP 200, PDF, Gemini, quota and cache");
    console.log("Answer:", first.body.answer);
    console.log("Citations:", JSON.stringify(first.body.citations));
    console.log("Gemini calls:", calls);
  } finally {
    // Only clean up this synthetic Emulator test fixture.
    const batch = db.batch();
    batch.delete(requestsRef);
    batch.delete(dayRef);
    batch.delete(entitlementRef);
    batch.delete(versionRef);

    await batch.commit();
    await file.delete({ ignoreNotFound: true });
  }
});