import { test } from "node:test";
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import express from "express";
import { PDFDocument, StandardFonts } from "pdf-lib";
import request from "supertest";

import { getFirestore } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { initializeFirebaseAdmin } from "../src/shared/firebaseAuth";
import { createAiChatRouter } from "../src/ai/chatRoute";
import { utcDay } from "../src/ai/quota";
import { AiProviderError, type AiProvider } from "../src/ai/provider";

test("A08 HTTP quota, cache and idempotency", async (t) => {
  assert.equal(process.env.FIREBASE_PROJECT_ID, "demo-unishare");
  assert.equal(
    process.env.FIRESTORE_EMULATOR_HOST, "127.0.0.1:8080"
  );
  assert.equal(
    process.env.FIREBASE_AUTH_EMULATOR_HOST, "127.0.0.1:9099"
  );

  assert.equal(
    process.env.FIREBASE_STORAGE_EMULATOR_HOST,
    "127.0.0.1:9199"
  );
  assert.equal(
    process.env.UNISHARE_STORAGE_BUCKET,
    "demo-unishare.firebasestorage.app"
  );

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

  const db = getFirestore();
  const uid = user.localId;
  const docId = `a08http_${randomUUID().replace(/-/g, "")}`;
  const versionId = "v1";

  const versionRef = db.collection("documents")
    .doc(docId).collection("versions").doc(versionId);

  const entitlementRef = db.collection("entitlements")
    .doc(uid).collection("items").doc(docId);

  const dayRef = db.collection("aiUsage")
    .doc(uid).collection("days").doc(utcDay());

  let providerCalls = 0;
  let receivedPdfText = "";
  let rateLimited = false;

  const provider: AiProvider = {
    async ask(input) {
      providerCalls++;

      assert.equal(input.docId, docId);
      assert.equal(input.question, "Tóm tắt tài liệu");
      assert.equal(input.pages.length, 1);
      assert.equal(input.pages[0].page, 1);

      receivedPdfText = input.pages[0].text;
      if (rateLimited) throw new AiProviderError("GEMINI_RATE_LIMIT", "Test upstream 429");

      return {
        answer: "Câu trả lời từ provider giả lập.",
        citations: [{ page: 1 }],
        requestId: "a08_fake_request",
        actualTokens: 100
      };
    }
  };

  const app = express();
  app.use(express.json());
  app.use("/v1/ai", createAiChatRouter(provider));

  const body = {
    docId,
    question: "Tóm tắt tài liệu",
    clientMessageId: `msg_${randomUUID().replace(/-/g, "")}`
  };

  async function ask(payload = body) {
    return request(app)
      .post("/v1/ai/chat")
      .set("Authorization", `Bearer ${user.idToken}`)
      .send(payload);
  }

  const pdfPath = `private/${docId}/v1/source.pdf`;
  const file = getStorage()
    .bucket("demo-unishare.firebasestorage.app")
    .file(pdfPath);

  try {
    const pdf = await PDFDocument.create();
    const font = await pdf.embedFont(StandardFonts.Helvetica);
    const page = pdf.addPage([595, 842]);

    page.drawText("UniShare PDF context test.", {
      x: 50,
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

    await entitlementRef.set({
      orderId: "ord_a08http123456",
      versionId,
      grantedAt: new Date().toISOString()
    });

    await t.test("200 - first request uses provider", async () => {
      const res = await ask();

      assert.equal(res.status, 200, JSON.stringify(res.body));
      assert.equal(res.body.requestId, "a08_fake_request");
      assert.equal(providerCalls, 1);
      assert.match(
        receivedPdfText,
        /UniShare PDF context test/
      );
    });

    await t.test("200 - same ID returns cached answer", async () => {
      const res = await ask();

      assert.equal(res.status, 200);
      assert.equal(res.body.answer, "Câu trả lời từ provider giả lập.");
      assert.equal(providerCalls, 1);

      const usage = await dayRef.get();
      assert.equal(usage.get("requests"), 1);
      assert.equal(usage.get("usedTokens"), 100);
    });

    await t.test("400 - same ID different question", async () => {
      const res = await ask({
        ...body,
        question: "Câu hỏi khác"
      });

      assert.equal(res.status, 400);
      assert.equal(providerCalls, 1);
    });

    await t.test("503 - Gemini rate limit permits one manual retry with the same ID", async () => {
      const next = { ...body, clientMessageId: `msg_${randomUUID().replace(/-/g, "")}` };
      rateLimited = true;
      const unavailable = await ask(next);
      assert.equal(unavailable.status, 503);
      assert.equal(unavailable.body.error.code, "GEMINI_RATE_LIMIT");
      assert.equal(providerCalls, 2);
      assert.equal((await dayRef.get()).get("requests"), 1);

      rateLimited = false;
      const retried = await ask(next);
      assert.equal(retried.status, 200);
      assert.deepEqual(retried.body.citations, [{ page: 1 }]);
      assert.equal(providerCalls, 3);
      assert.equal((await dayRef.get()).get("requests"), 2);
    });

    await t.test("429 - quota blocks new question", async () => {
      await dayRef.update({
        requests: 10
      });

      const res = await ask({
        ...body,
        clientMessageId: `msg_${randomUUID().replace(/-/g, "")}`
      });

      assert.equal(res.status, 429, JSON.stringify(res.body));
      assert.equal(res.body.error.code, "QUOTA_EXCEEDED");
      assert.equal(providerCalls, 3);
    });
  } finally {
    const requests = await db.collection("aiUsage")
      .doc(uid).collection("requests").get();
    const batch = db.batch();

    requests.docs.forEach(d => batch.delete(d.ref));
    batch.delete(dayRef);
    batch.delete(entitlementRef);
    batch.delete(versionRef);

    await batch.commit();
    await file.delete({ ignoreNotFound: true });
  }
});