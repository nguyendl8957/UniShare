import { createHash } from "node:crypto";
import {
  getFirestore,
  type Firestore
} from "firebase-admin/firestore";
import {
  AI_DAILY_REQUEST_LIMIT,
  AI_DAILY_RESERVED_TOKEN_LIMIT,
  AI_TOKENS_PER_REQUEST,
  utcDay
} from "./quota";

type BeginKind =
  | "STARTED"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "FAILED"
  | "STALE_REVIEW"
  | "PAYLOAD_MISMATCH"
  | "QUOTA_EXCEEDED";

export type BeginResult = { kind: BeginKind };

export type FinishResult = {
  kind: "FINALIZED" | "ALREADY_FINALIZED" | "NOT_FOUND";
};

const ID_PATTERN = /^[A-Za-z0-9_-]{1,128}$/;
const DOC_PATTERN = /^[A-Za-z0-9_-]{1,150}$/;
const LEASE_MS = 3 * 60 * 1000;

export type AiRequestIdentity = {
  uid: string;
  docId: string;
  clientMessageId: string;
  question: string;
};

function validate(input: AiRequestIdentity): void {
  if (
    !ID_PATTERN.test(input.uid) ||
    !ID_PATTERN.test(input.clientMessageId) ||
    !DOC_PATTERN.test(input.docId) ||
    input.question.trim().length === 0 ||
    input.question.length > 500
  ) {
    throw new Error("Invalid AI request identity");
  }
}

function payloadHash(input: AiRequestIdentity): string {
  return createHash("sha256")
    .update(JSON.stringify([input.docId, input.question]))
    .digest("hex");
}

function references(
  db: Firestore,
  uid: string,
  dayUtc: string,
  clientMessageId: string
) {
  const userRef = db.collection("aiUsage").doc(uid);

  const dayRef = userRef
    .collection("days").doc(dayUtc);

  // Global per UID: the same message ID cannot move to a new day.
  const requestRef = userRef
    .collection("requests").doc(clientMessageId);

  return { dayRef, requestRef };
}

export type AiCachedAnswer = {
  answer: string;
  citations: Array<{ page: number }>;
  requestId: string;
};

const AI_CACHE_TTL_MS = 24 * 60 * 60 * 1000;

function validCachedAnswer(
  value: AiCachedAnswer | undefined
): value is AiCachedAnswer {
  return (
    value !== undefined &&
    typeof value.answer === "string" &&
    value.answer.length > 0 &&
    value.answer.length <= 12000 &&
    typeof value.requestId === "string" &&
    value.requestId.length > 0 &&
    value.requestId.length <= 128 &&
    Array.isArray(value.citations) &&
    value.citations.length <= 20 &&
    value.citations.every(
      item =>
        Number.isSafeInteger(item.page) &&
        item.page >= 1 &&
        item.page <= 10000
    )
  );
}
export async function beginAiRequest(
  input: AiRequestIdentity,
  at: Date = new Date(),
  db: Firestore = getFirestore()
): Promise<BeginResult> {
  validate(input);

  const dayUtc = utcDay(at);
  const nowMs = at.getTime();
  const hash = payloadHash(input);

  const { dayRef, requestRef } = references(
    db, input.uid, dayUtc, input.clientMessageId
  );

  return db.runTransaction(async (tx) => {
    // All reads precede writes.
    const existing = await tx.get(requestRef);
    const usage = await tx.get(dayRef);

    if (existing.exists) {
      if (existing.get("payloadHash") !== hash) {
        return { kind: "PAYLOAD_MISMATCH" } as const;
      }

      const state = existing.get("state");

      if (state === "SUCCEEDED") {
        return { kind: "COMPLETED" } as const;
      }

      if (state === "RESERVED") {
        const leaseUntilMs = existing.get("leaseUntilMs");

        if (
          !Number.isSafeInteger(leaseUntilMs) ||
          leaseUntilMs <= nowMs
        ) {
          // Provider outcome may be uncertain: never auto-retry.
          return { kind: "STALE_REVIEW" } as const;
        }

        return { kind: "IN_PROGRESS" } as const;
      }

      if (state !== "RETRYABLE") {
        throw new Error("Unknown AI request state");
      }

      // A known-not-sent request may reserve quota again.
      // Existing payloadHash has already been checked.
    }

    const requests = usage.exists
      ? usage.get("requests") : 0;

    const reservedTokens = usage.exists
      ? usage.get("reservedTokens") : 0;

    const usedTokens = usage.exists
      ? usage.get("usedTokens") : 0;

    for (const value of [requests, reservedTokens, usedTokens]) {
      if (!Number.isSafeInteger(value) || value < 0) {
        throw new Error("Invalid AI usage counters");
      }
    }

    if (
      requests >= AI_DAILY_REQUEST_LIMIT ||
      reservedTokens + usedTokens + AI_TOKENS_PER_REQUEST >
        AI_DAILY_RESERVED_TOKEN_LIMIT
    ) {
      return { kind: "QUOTA_EXCEEDED" } as const;
    }

    tx.set(dayRef, {
      requests: requests + 1,
      reservedTokens: reservedTokens + AI_TOKENS_PER_REQUEST,
      usedTokens,
      updatedAt: at.toISOString()
    }, { merge: true });

    const reservation = {
      docId: input.docId,
      dayUtc,
      payloadHash: hash,
      state: "RESERVED",
      reservedTokens: AI_TOKENS_PER_REQUEST,
      leaseUntilMs: nowMs + LEASE_MS,
      updatedAt: at.toISOString()
    };

    if (existing.exists) {
      tx.update(requestRef, reservation);
    } else {
      tx.create(requestRef, {
        ...reservation,
        createdAt: at.toISOString()
      });
    }

    return { kind: "STARTED" } as const;
  });
}

export async function finishAiRequest(
  input: AiRequestIdentity,
  dayUtc: string,
  outcome: "SUCCESS" | "NOT_SENT",
  actualTokens = 0,
  db: Firestore = getFirestore(),
  cachedAnswer?: AiCachedAnswer
): Promise<FinishResult> {
  if (
    (outcome === "SUCCESS" && !validCachedAnswer(cachedAnswer)) ||
    (outcome === "NOT_SENT" && cachedAnswer !== undefined)
  ) {
    throw new Error("Invalid AI cached answer");
  }
  validate(input);

  if (
    !/^\d{4}-\d{2}-\d{2}$/.test(dayUtc) ||
    !Number.isSafeInteger(actualTokens) ||
    actualTokens < 0 ||
    actualTokens > 1000000 ||
    (outcome === "NOT_SENT" && actualTokens !== 0) || (outcome !== "SUCCESS" && outcome !== "NOT_SENT")
  ) {
    throw new Error("Invalid AI settlement");
  }

  const { dayRef, requestRef } = references(
    db, input.uid, dayUtc, input.clientMessageId
  );

  return db.runTransaction(async (tx) => {
    const request = await tx.get(requestRef);
    const usage = await tx.get(dayRef);

    if (!request.exists) {
      return { kind: "NOT_FOUND" } as const;
    }

    if (request.get("payloadHash") !== payloadHash(input)) {
      throw new Error("AI request identity mismatch");
    }

    if (request.get("dayUtc") !== dayUtc) {
      throw new Error("AI reservation belongs to another UTC day");
    }

    if (request.get("state") !== "RESERVED") {
      return { kind: "ALREADY_FINALIZED" } as const;
    }

    if (!usage.exists) {
      throw new Error("AI usage document missing");
    }

    const reserved = usage.get("reservedTokens");
    const used = usage.get("usedTokens");
    const requests = usage.get("requests");

    if (
      !Number.isSafeInteger(reserved) ||
      !Number.isSafeInteger(used) ||
      !Number.isSafeInteger(requests) ||
      reserved < AI_TOKENS_PER_REQUEST ||
      used < 0 ||
      requests <= 0
    ) {
      throw new Error("Invalid AI settlement counters");
    }

    const success = outcome === "SUCCESS";

    tx.update(dayRef, {
      reservedTokens: reserved - AI_TOKENS_PER_REQUEST,
      usedTokens: used + (success ? actualTokens : 0),
      requests: requests - (success ? 0 : 1),
      updatedAt: new Date().toISOString()
    });

    const finishedAt = new Date();

    tx.update(requestRef, {
      state: success ? "SUCCEEDED" : "RETRYABLE",
      actualTokens: success ? actualTokens : 0,
      updatedAt: finishedAt.toISOString(),
      ...(success ? {
        cachedAnswer,
        cacheExpiresAtMs: finishedAt.getTime() + AI_CACHE_TTL_MS
      } : {})
    });

    return { kind: "FINALIZED" } as const;
  });
}
export async function getCachedAiAnswer(
  input: AiRequestIdentity,
  dayUtc: string,
  db: Firestore = getFirestore()
): Promise<AiCachedAnswer | null> {
  validate(input);

  if (!/^\d{4}-\d{2}-\d{2}$/.test(dayUtc)) {
    throw new Error("Invalid UTC day");
  }

  const { requestRef } = references(
    db, input.uid, dayUtc, input.clientMessageId
  );

  const snapshot = await requestRef.get();

  if (!snapshot.exists) return null;

  if (snapshot.get("payloadHash") !== payloadHash(input)) {
    throw new Error("AI request identity mismatch");
  }

  if (snapshot.get("state") !== "SUCCEEDED") {
    return null;
  }

  const expiresAt = snapshot.get("cacheExpiresAtMs");
  const cached = snapshot.get("cachedAnswer") as
    AiCachedAnswer | undefined;

  if (
    !Number.isSafeInteger(expiresAt) ||
    expiresAt <= Date.now() ||
    !validCachedAnswer(cached)
  ) {
    return null;
  }

  return cached;
}