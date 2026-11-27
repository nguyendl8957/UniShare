import { test } from "node:test";
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import express from "express";
import request from "supertest";
import { PDFDocument, StandardFonts } from "pdf-lib";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";

import { initializeFirebaseAdmin } from "../src/shared/firebaseAuth";
import { createAiChatRouter } from "../src/ai/chatRoute";
import { utcDay } from "../src/ai/quota";
import type { AiProvider } from "../src/ai/provider";

test("A08 entitlement revocation during AI and cache", async (t) => {
  // Never run against production Firebase.
  assert.equal(process.env.FIREBASE_PROJECT_ID, "demo-unishare");
  assert.equal(process.env.FIRESTORE_EMULATOR_HOST, "127.0.0.1:8080");
  assert.equal(process.env.FIREBASE_AUTH_EMULATOR_HOST, "127.0.0.1:9099");
  assert.equal(process.env.FIREBASE_STORAGE_EMULATOR_HOST, "127.0.0.1:9199");
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

  const uid = user.localId;
  const suffix = randomUUID().replace(/-/g, "");
  const docId = `a08revoke_${suffix}`;

  const db = getFirestore();
  const versionRef = db.collection("documents")
    .doc(docId).collection("versions").doc("v1");

  const entitlementRef = db.collection("entitlements")
    .doc(uid).collection("items").doc(docId);

  const userUsageRef = db.collection("aiUsage").doc(uid);
  const dayRef = userUsageRef.collection("days").doc(utcDay());

  const pdfPath = `private/${docId}/v1/source.pdf`;
  const file = getStorage()
    .bucket("demo-unishare.firebasestorage.app")
    .file(pdfPath);

  let providerCalls = 0;

  const provider: AiProvider = {
    async ask(input) {
      providerCalls++;
      assert.equal(input.docId, docId);
      assert.match(input.pages[0].text, /Private AI document/);

      if (input.question === "Thu hồi giữa chừng") {
        // Simulate revocation while the AI provider is working.
        await entitlementRef.update({
          revokedAt: new Date().toISOString()
        });
      }

      return {
        answer: "Thông tin riêng tư từ tài liệu.",
        citations: [{ page: 1 }],
        requestId: `a08_request_${providerCalls}`,
        actualTokens: 100
      };
    }
  };

  const app = express();
  app.use(express.json());
  app.use("/v1/ai", createAiChatRouter(provider));

  async function ask(question: string, clientMessageId: string) {
    return request(app)
      .post("/v1/ai/chat")
      .set("Authorization", `Bearer ${user.idToken}`)
      .send({ docId, question, clientMessageId });
  }

  try {
    const pdf = await PDFDocument.create();
    const font = await pdf.embedFont(StandardFonts.Helvetica);
    const page = pdf.addPage([595, 842]);

    page.drawText("Private AI document.", {
      x: 40, y: 750, size: 14, font
    });

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
      orderId: "ord_a08_revocation_test",
      versionId: "v1",
      grantedAt: new Date().toISOString()
    });

    await t.test(
      "403 - entitlement revoked while provider is processing",
      async () => {
        const response = await ask(
          "Thu hồi giữa chừng",
          `msg_mid_${suffix}`
        );

        assert.equal(
          response.status, 403,
          JSON.stringify(response.body)
        );
        assert.equal(providerCalls, 1);
        assert.equal(response.body.answer, undefined);
      }
    );

    // Restore only this isolated Emulator fixture.
    await entitlementRef.update({
      revokedAt: FieldValue.delete()
    });

    await t.test(
      "403 - revoked Buyer cannot receive cached answer",
      async () => {
        const messageId = `msg_cache_${suffix}`;
        const first = await ask("Tóm tắt tài liệu", messageId);

        assert.equal(
          first.status, 200,
          JSON.stringify(first.body)
        );
        assert.equal(providerCalls, 2);

        await entitlementRef.update({
          revokedAt: new Date().toISOString()
        });

        const retry = await ask("Tóm tắt tài liệu", messageId);

        assert.equal(
          retry.status, 403,
          JSON.stringify(retry.body)
        );
        assert.equal(retry.body.answer, undefined);
        assert.equal(providerCalls, 2);
      }
    );
  } finally {
    const requestDocs = await userUsageRef
      .collection("requests").get();

    const batch = db.batch();

    requestDocs.docs.forEach(doc => batch.delete(doc.ref));
    batch.delete(dayRef);
    batch.delete(entitlementRef);
    batch.delete(versionRef);

    await batch.commit();
    await file.delete({ ignoreNotFound: true });
  }
});