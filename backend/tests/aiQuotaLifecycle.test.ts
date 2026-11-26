import { test } from "node:test";
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { getApps, initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";

import {
  beginAiRequest,
  finishAiRequest, getCachedAiAnswer
} from "../src/ai/quotaLifecycle";

test("A08 idempotency and quota settlement", async (t) => {
  assert.equal(process.env.FIREBASE_PROJECT_ID, "demo-unishare");
  assert.equal(
    process.env.FIRESTORE_EMULATOR_HOST,
    "127.0.0.1:8080"
  );

  if (getApps().length === 0) {
    initializeApp({ projectId: "demo-unishare" });
  }

  const db = getFirestore();
  const uid = `a08_${randomUUID().replace(/-/g, "")}`;
  const day = new Date("2026-11-27T12:00:00.000Z");
  const dayUtc = "2026-11-27";

  const dayRef = db.collection("aiUsage")
    .doc(uid).collection("days").doc(dayUtc);

  const input = (clientMessageId: string) => ({
    uid,
    docId: "demo_document",
    question: "Tóm tắt nội dung tài liệu.",
    clientMessageId
  });

  const first = input("msg_first");

  const reply = {
    answer: "Test AI summary response.",
    citations: [{ page: 1 }],
    requestId: "req_a08_test"
  };
  const second = input("msg_second");
  const failed = input("msg_failed");
  const stale = input("msg_stale");

  try {
    await t.test("first request reserves quota", async () => {
      const result = await beginAiRequest(first, day, db);
      assert.equal(result.kind, "STARTED");
    });

    await t.test("same ID does not reserve twice", async () => {
      const result = await beginAiRequest(first, day, db);
      assert.equal(result.kind, "IN_PROGRESS");

      const snapshot = await dayRef.get();
      assert.equal(snapshot.get("requests"), 1);
      assert.equal(snapshot.get("reservedTokens"), 768);
    });

    await t.test("same ID with different content is rejected", async () => {
      const result = await beginAiRequest(
        { ...first, question: "Câu hỏi khác." },
        day,
        db
      );

      assert.equal(result.kind, "PAYLOAD_MISMATCH");
    });

    await t.test("concurrent duplicate reserves once", async () => {
      const results = await Promise.all(
        Array.from({ length: 5 }, () =>
          beginAiRequest(second, day, db)
        )
      );

      assert.equal(
        results.filter((r) => r.kind === "STARTED").length,
        1
      );

      assert.equal(
        results.filter((r) => r.kind === "IN_PROGRESS").length,
        4
      );

      const snapshot = await dayRef.get();
      assert.equal(snapshot.get("requests"), 2);
    });

    await t.test("success settles real token usage", async () => {
      const settled = await finishAiRequest(
        first, dayUtc, "SUCCESS", 150, db, reply
      );

      assert.equal(settled.kind, "FINALIZED");

      const again = await finishAiRequest(
        first, dayUtc, "SUCCESS", 150, db, reply
      );

      assert.equal(again.kind, "ALREADY_FINALIZED");

      const duplicate = await beginAiRequest(first, day, db);
      assert.equal(duplicate.kind, "COMPLETED");

      const cached = await getCachedAiAnswer(first, dayUtc, db);
      assert.deepEqual(cached, reply);

      const snapshot = await dayRef.get();
      assert.equal(snapshot.get("requests"), 2);
      assert.equal(snapshot.get("usedTokens"), 150);
      assert.equal(snapshot.get("reservedTokens"), 768);
    });

    await t.test("failed provider returns reservation", async () => {
      assert.equal(
        (await beginAiRequest(failed, day, db)).kind,
        "STARTED"
      );

      assert.equal(
        (await finishAiRequest(
          failed, dayUtc, "NOT_SENT", 0, db
        )).kind,
        "FINALIZED"
      );

      const retries = await Promise.all(
        Array.from({ length: 4 }, () =>
          beginAiRequest(failed, day, db)
        )
      );

      assert.equal(
        retries.filter(r => r.kind === "STARTED").length,
        1
      );
      assert.equal(
        retries.filter(r => r.kind === "IN_PROGRESS").length,
        3
      );

      // The retried request never reached the provider either.
      assert.equal(
        (await finishAiRequest(
          failed, dayUtc, "NOT_SENT", 0, db
        )).kind,
        "FINALIZED"
      );

      const snapshot = await dayRef.get();
      assert.equal(snapshot.get("requests"), 2);
      assert.equal(snapshot.get("reservedTokens"), 768);
      assert.equal(snapshot.get("usedTokens"), 150);
    });

    await t.test("stale request fails closed", async () => {
      assert.equal(
        (await beginAiRequest(stale, day, db)).kind,
        "STARTED"
      );

      const later = new Date(day.getTime() + 4 * 60_000);

      assert.equal(
        (await beginAiRequest(stale, later, db)).kind,
        "STALE_REVIEW"
      );

      const snapshot = await dayRef.get();
      assert.equal(snapshot.get("requests"), 3);
    });
    await t.test("cross-day retry keeps one reservation", async () => {
      const beforeMidnight = new Date(
        "2026-11-27T23:59:00.000Z"
      );
      const afterMidnight = new Date(
        "2026-11-28T00:01:00.000Z"
      );

      const cross = input("msg_cross_day");

      const firstBegin = await beginAiRequest(
        cross, beforeMidnight, db
      );
      assert.equal(firstBegin.kind, "STARTED");

      // Same message ID after midnight must not reserve again.
      const duplicate = await beginAiRequest(
        cross, afterMidnight, db
      );
      assert.equal(duplicate.kind, "IN_PROGRESS");

      const settled = await finishAiRequest(
        cross,
        "2026-11-27",
        "SUCCESS",
        120,
        db,
        {
          answer: "Cross-day cached answer",
          citations: [{ page: 1 }],
          requestId: "req_cross_day"
        }
      );

      assert.equal(settled.kind, "FINALIZED");

      const retry = await beginAiRequest(
        cross, afterMidnight, db
      );
      assert.equal(retry.kind, "COMPLETED");

      const cached = await getCachedAiAnswer(
        cross, "2026-11-27", db
      );
      assert.equal(cached?.answer, "Cross-day cached answer");

      const nextDayRef = db.collection("aiUsage")
        .doc(uid).collection("days").doc("2026-11-28");

      const nextDayUsage = await nextDayRef.get();
      assert.equal(nextDayUsage.exists, false);

      const originalDay = await dayRef.get();
      assert.equal(originalDay.get("requests"), 4);
    });
  } finally {
    const requests = await db.collection("aiUsage")
      .doc(uid).collection("requests").get();
    const batch = db.batch();

    requests.docs.forEach((doc) => batch.delete(doc.ref));
    batch.delete(dayRef);

    await batch.commit();
  }
});