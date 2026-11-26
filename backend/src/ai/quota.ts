import {
  getFirestore,
  type Firestore
} from "firebase-admin/firestore";

export const AI_DAILY_REQUEST_LIMIT = 10;
export const AI_DAILY_RESERVED_TOKEN_LIMIT = 8192;
export const AI_TOKENS_PER_REQUEST = 768;

export type AiQuotaResult =
  | { allowed: true; requests: number; reservedTokens: number }
  | { allowed: false; reason: "QUOTA_EXCEEDED" };

const UID_PATTERN = /^[A-Za-z0-9_-]{1,128}$/;

export function utcDay(date: Date = new Date()): string {
  if (!Number.isFinite(date.getTime())) {
    throw new Error("Invalid date");
  }

  return date.toISOString().slice(0, 10);
}

export async function reserveAiQuota(
  uid: string,
  date: Date = new Date(),
  db: Firestore = getFirestore()
): Promise<AiQuotaResult> {
  if (!UID_PATTERN.test(uid)) {
    throw new Error("Invalid Firebase UID");
  }

  const dayUtc = utcDay(date);

  const ref = db
    .collection("aiUsage")
    .doc(uid)
    .collection("days")
    .doc(dayUtc);

  return db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(ref);

    const requests = snapshot.exists
      ? snapshot.get("requests")
      : 0;

    const reservedTokens = snapshot.exists
      ? snapshot.get("reservedTokens")
      : 0;

    const usedTokens = snapshot.exists
      ? snapshot.get("usedTokens")
      : 0;

    if (
      !Number.isSafeInteger(requests) ||
      requests < 0 ||
      !Number.isSafeInteger(reservedTokens) ||
      reservedTokens < 0 ||
      !Number.isSafeInteger(usedTokens) ||
      usedTokens < 0
    ) {
      throw new Error("Invalid AI quota state");
    }

    if (
      requests >= AI_DAILY_REQUEST_LIMIT ||
      reservedTokens + usedTokens + AI_TOKENS_PER_REQUEST >
        AI_DAILY_RESERVED_TOKEN_LIMIT
    ) {
      return {
        allowed: false,
        reason: "QUOTA_EXCEEDED"
      };
    }

    const nextRequests = requests + 1;
    const nextReservedTokens =
      reservedTokens + AI_TOKENS_PER_REQUEST;

    transaction.set(
      ref,
      {
        requests: nextRequests,
        reservedTokens: nextReservedTokens,
        usedTokens,
        updatedAt: new Date().toISOString()
      },
      { merge: true }
    );

    return {
      allowed: true,
      requests: nextRequests,
      reservedTokens: nextReservedTokens
    };
  });
}