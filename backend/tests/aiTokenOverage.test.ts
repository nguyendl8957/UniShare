import { test } from "node:test";
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { getApps, initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";

import {
  beginAiRequest,
  finishAiRequest,
  getCachedAiAnswer
} from "../src/ai/quotaLifecycle";

test("A08 over-budget token settlement", async () => {
  assert.equal(process.env.FIREBASE_PROJECT_ID, "demo-unishare");
  assert.equal(
    process.env.FIRESTORE_EMULATOR_HOST,
    "127.0.0.1:8080"
  );

  if (!getApps().length) {
    initializeApp({ projectId: "demo-unishare" });
  }

  const db = getFirestore();
  const uid = `a08over_${randomUUID().replace(/-/g, "")}`;
  const day = new Date("2026-11-27T12:00:00.000Z");
  const dayUtc = "2026-11-27";

  const usageRef = db.collection("aiUsage").doc(uid);
  const dayRef = usageRef.collection("days").doc(dayUtc);

  const identity = {
    uid,
    docId: "synthetic_document",
    question: "Tóm tắt nội dung",
    clientMessageId: "msg_overage"
  };

  const cachedAnswer = {
    answer: "Đây là câu trả lời giả lập.",
    citations: [{ page: 1 }],
    requestId: "req_overage"
  };

  try {
    assert.equal(
      (await beginAiRequest(identity, day, db)).kind,
      "STARTED"
    );

    // Provider finished and reported more than
    // the 8192 tokens originally reserved.
    assert.equal(
      (await finishAiRequest(
        identity, dayUtc, "SUCCESS", 8193, db, cachedAnswer
      )).kind,
      "FINALIZED"
    );

    const usage = await dayRef.get();

    assert.equal(usage.get("reservedTokens"), 0);
    assert.equal(usage.get("usedTokens"), 8193);
    assert.equal(usage.get("requests"), 1);

    // Duplicate requests must never charge again.
    assert.equal(
      (await beginAiRequest(identity, day, db)).kind,
      "COMPLETED"
    );

    assert.deepEqual(
      await getCachedAiAnswer(identity, dayUtc, db),
      cachedAnswer
    );

    assert.equal(
      (await finishAiRequest(
        identity, dayUtc, "SUCCESS", 8193, db, cachedAnswer
      )).kind,
      "ALREADY_FINALIZED"
    );

    const afterDuplicate = await dayRef.get();
    assert.equal(afterDuplicate.get("usedTokens"), 8193);

    console.log("PASS: Overage accounted exactly once.");
  } finally {
    const requests = await usageRef.collection("requests").get();
    const batch = db.batch();

    requests.docs.forEach(doc => batch.delete(doc.ref));
    batch.delete(dayRef);

    await batch.commit();
  }
});