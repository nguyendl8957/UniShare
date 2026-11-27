import { test } from "node:test";
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { initializeApp, getApps } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";

import {
  reserveAiQuota,
  utcDay,
  AI_DAILY_REQUEST_LIMIT,
  AI_TOKENS_PER_REQUEST
} from "../src/ai/quota";

test("A08 quota transactions and isolation", async (t) => {
  assert.equal(process.env.FIREBASE_PROJECT_ID, "demo-unishare");
  assert.equal(
    process.env.FIRESTORE_EMULATOR_HOST,
    "127.0.0.1:8080"
  );

  if (getApps().length === 0) {
    initializeApp({ projectId: "demo-unishare" });
  }

  const db = getFirestore();
  const suffix = randomUUID().replace(/-/g, "");
  const uidA = `a08a_${suffix}`;
  const uidB = `a08b_${suffix}`;

  const day = new Date("2026-11-27T12:00:00.000Z");
  const dayUtc = utcDay(day);

  const refA = db.collection("aiUsage")
    .doc(uidA).collection("days").doc(dayUtc);

  const refB = db.collection("aiUsage")
    .doc(uidB).collection("days").doc(dayUtc);

  try {
    await t.test("UTC day is independent of local timezone", () => {
      assert.equal(
        utcDay(new Date("2026-11-27T23:59:59.000Z")),
        "2026-11-27"
      );
      assert.equal(
        utcDay(new Date("2026-11-28T00:00:00.000Z")),
        "2026-11-28"
      );
    });

    await t.test("first reservation creates usage", async () => {
      const result = await reserveAiQuota(uidA, day, db);

      assert.equal(result.allowed, true);

      const snapshot = await refA.get();
      assert.equal(snapshot.get("requests"), 1);
      assert.equal(
        snapshot.get("reservedTokens"),
        AI_TOKENS_PER_REQUEST
      );
      assert.equal(snapshot.get("usedTokens"), 0);
    });

    await t.test("concurrent requests respect daily limit", async () => {
      // Start more requests than remaining quota.
      const results = await Promise.all(
        Array.from({ length: 15 }, () =>
          reserveAiQuota(uidA, day, db)
        )
      );

      const allowed = results.filter((item) => item.allowed);
      const denied = results.filter((item) => !item.allowed);

      assert.equal(
        allowed.length,
        AI_DAILY_REQUEST_LIMIT - 1
      );
      assert.equal(denied.length, 6);

      const snapshot = await refA.get();
      assert.equal(
        snapshot.get("requests"),
        AI_DAILY_REQUEST_LIMIT
      );
      assert.equal(
        snapshot.get("reservedTokens"),
        AI_DAILY_REQUEST_LIMIT * AI_TOKENS_PER_REQUEST
      );
    });

    await t.test("second UID has independent quota", async () => {
      const result = await reserveAiQuota(uidB, day, db);

      assert.equal(result.allowed, true);

      const snapshot = await refB.get();
      assert.equal(snapshot.get("requests"), 1);
    });

    await t.test("next UTC day starts with fresh quota", async () => {
      const nextDay = new Date("2026-11-28T00:00:00.000Z");
      const result = await reserveAiQuota(uidA, nextDay, db);

      assert.equal(result.allowed, true);

      const snapshot = await db.collection("aiUsage")
        .doc(uidA).collection("days")
        .doc("2026-11-28").get();

      assert.equal(snapshot.get("requests"), 1);

      await snapshot.ref.delete();
    });

    await t.test("invalid UID is rejected", async () => {
      await assert.rejects(
        reserveAiQuota("../invalid", day, db),
        /Invalid Firebase UID/
      );
    });
  } finally {
    await Promise.allSettled([
      refA.delete(),
      refB.delete()
    ]);
  }
});