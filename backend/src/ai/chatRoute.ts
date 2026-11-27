import { Router, type Response } from "express";
import { randomUUID } from "node:crypto";

import { requireAuth } from "../shared/firebaseAuth";
import { sendApiError } from "../shared/httpErrors";
import {
  authorizePdfRead,
  DocumentAccessError
} from "../documents/entitlement";
import {
  beginAiRequest,
  finishAiRequest,
  getCachedAiAnswer
} from "./quotaLifecycle";
import { utcDay, AI_TOKENS_PER_REQUEST } from "./quota";
import {
  type AiProvider,
  AiProviderNotSentError
} from "./provider";
import { loadAuthorizedPdfText } from "./pdfSource";
import { PdfTextError } from "./pdfText";
import { createGeminiProvider } from "./geminiProvider";

const DOC_ID_PATTERN = /^[A-Za-z0-9_-]{1,150}$/;
const MESSAGE_ID_PATTERN = /^[A-Za-z0-9_-]{1,128}$/;

function aiError(
  res: Response,
  status: number,
  code: string,
  message: string
): void {
  res.status(status).json({
    error: { code, message, requestId: randomUUID() }
  });
}

// Production deliberately has no provider until explicitly configured.
// Tests inject a fake provider without affecting the deployed route.
export function createAiChatRouter(
  provider: AiProvider | null = null
): Router {
  const router = Router();

  router.post("/chat", requireAuth, async (req, res) => {
    res.setHeader("Cache-Control", "private, no-store");

    const uid = res.locals.uid as string;
    const { docId, question, clientMessageId } = req.body ?? {};

    if (
      typeof docId !== "string" ||
      !DOC_ID_PATTERN.test(docId) ||
      typeof question !== "string" ||
      question.trim().length === 0 ||
      question.length > 500 ||
      typeof clientMessageId !== "string" ||
      !MESSAGE_ID_PATTERN.test(clientMessageId)
    ) {
      sendApiError(
        res, 400, "INVALID_REQUEST", "Invalid AI chat request"
      );
      return;
    }

    try {
      await authorizePdfRead(uid, docId);
    } catch (error) {
      if (error instanceof DocumentAccessError) {
        if (error.statusCode === 403) {
          sendApiError(
            res, 403, "FORBIDDEN", "Document access denied"
          );
        } else if (error.statusCode === 400) {
          sendApiError(
            res, 400, "INVALID_REQUEST", "Invalid document"
          );
        } else {
          sendApiError(
            res, 404, "NOT_FOUND", "Document unavailable"
          );
        }
      } else {
        console.error("AI entitlement check failed");
        sendApiError(
          res, 503, "SERVICE_UNAVAILABLE", "AI unavailable"
        );
      }
      return;
    }

    // Production access is limited to explicitly approved Firebase UIDs.
    // The UID comes exclusively from verified Firebase Authentication.
    const allowedTestUids = (
      process.env.UNISHARE_AI_TEST_UIDS ?? ""
    )
      .split(",")
      .map((value) => value.trim())
      .filter(Boolean);

    const activeProvider = provider ?? (
      process.env.UNISHARE_AI_ENABLED === "true" &&
      Boolean(process.env.GEMINI_API_KEY) &&
      allowedTestUids.includes(uid)
        ? createGeminiProvider(process.env.GEMINI_API_KEY!)
        : null
    );

    if (activeProvider === null) {
      sendApiError(
        res, 503, "SERVICE_UNAVAILABLE",
        "AI provider is not configured"
      );
      return;
    }

    const identity = {
      uid, docId, question, clientMessageId
    };

    // Use the same UTC date for reservation and settlement,
    // including requests that finish after midnight.
    const startedAt = new Date();
    const dayUtc = utcDay(startedAt);

    try {
      const begin = await beginAiRequest(identity, startedAt);

      switch (begin.kind) {
        case "PAYLOAD_MISMATCH":
          aiError(res, 400, "INVALID_REQUEST",
            "Message ID already belongs to another question");
          return;

        case "QUOTA_EXCEEDED":
          aiError(res, 429, "QUOTA_EXCEEDED",
            "Daily AI quota exceeded");
          return;

        case "IN_PROGRESS":
          aiError(res, 409, "IN_PROGRESS",
            "AI request is already processing");
          return;

        case "STALE_REVIEW":
          aiError(res, 503, "SERVICE_UNAVAILABLE",
            "AI request needs reconciliation");
          return;

        case "COMPLETED": {
          if (!begin.dayUtc) {
            throw new Error("Completed AI request has no UTC day");
          }

          const cached = await getCachedAiAnswer(
            identity, begin.dayUtc
          );

          if (!cached) {
            aiError(res, 503, "SERVICE_UNAVAILABLE",
              "Cached AI answer unavailable");
            return;
          }

          // Recheck immediately before returning private AI content.
          try {
            await authorizePdfRead(uid, docId);
          } catch (error) {
            if (
              error instanceof DocumentAccessError &&
              error.statusCode === 403
            ) {
              sendApiError(
                res, 403, "FORBIDDEN", "Document access denied"
              );
            } else {
              sendApiError(
                res, 503, "SERVICE_UNAVAILABLE",
                "Cannot verify document access"
              );
            }
            return;
          }
          res.status(200).json(cached);
          return;
        }

        case "STARTED":
          break;

        default:
          aiError(res, 503, "SERVICE_UNAVAILABLE",
            "AI request cannot proceed");
          return;
      }

      // Only the request owner loads PDF.
      // Duplicate requests return the cache or IN_PROGRESS.
      let pages;

      try {
        pages = await loadAuthorizedPdfText(uid, docId);
      } catch (error) {
        // Provider has definitely not been called yet.
        try {
          await finishAiRequest(identity, dayUtc, "NOT_SENT");
        } catch {
          console.error("AI PDF load refund failed");
          sendApiError(
            res, 503, "SERVICE_UNAVAILABLE",
            "AI request requires reconciliation"
          );
          return;
        }

        if (
          error instanceof DocumentAccessError &&
          error.statusCode === 403
        ) {
          sendApiError(
            res, 403, "FORBIDDEN", "Document access denied"
          );
          return;
        }

        if (
          error instanceof PdfTextError &&
          error.code === "PDF_TEXT_UNAVAILABLE"
        ) {
          aiError(
            res, 422, "PDF_TEXT_UNAVAILABLE",
            "PDF has no extractable text"
          );
          return;
        }

        if (
          error instanceof PdfTextError &&
          error.code === "TOO_MANY_PAGES"
        ) {
          aiError(
            res, 422, "PDF_TOO_MANY_PAGES",
            "PDF exceeds AI page limit"
          );
          return;
        }

        sendApiError(
          res, 503, "SERVICE_UNAVAILABLE",
          "Document content unavailable"
        );
        return;
      }

      let reply;

      try {
        reply = await activeProvider.ask({ docId, question, pages });
      } catch (error) {
        if (error instanceof AiProviderNotSentError) {
          // Safe to refund only when definitely not sent.
          await finishAiRequest(
            identity, dayUtc, "NOT_SENT"
          );
        }

        // Unknown provider outcome stays RESERVED: fail closed.
        aiError(res, 503, "SERVICE_UNAVAILABLE",
          "AI provider unavailable");
        return;
      }

      if (
        typeof reply.answer !== "string" ||
        reply.answer.length === 0 ||
        reply.answer.length > 12000 ||
        typeof reply.requestId !== "string" ||
        !/^[A-Za-z0-9_-]{1,128}$/.test(reply.requestId) ||
        !Array.isArray(reply.citations) ||
        reply.citations.length > 20 ||
        !reply.citations.every(
          citation =>
            Number.isSafeInteger(citation.page) &&
            citation.page >= 1 &&
            citation.page <= 10000
        ) ||
        !Number.isSafeInteger(reply.actualTokens) ||
        reply.actualTokens < 0 ||
        reply.actualTokens > 1000000
      ) {
        // Do not blindly retry an already-sent provider call.
        throw new Error("Invalid AI provider response");
      }

      const answer = {
        answer: reply.answer,
        citations: reply.citations,
        requestId: reply.requestId
      };

      const settlement = await finishAiRequest(
        identity,
        dayUtc,
        "SUCCESS",
        reply.actualTokens,
        undefined,
        answer
      );

      if (settlement.kind !== "FINALIZED") {
        throw new Error("AI settlement failed");
      }

      // Do not return the generated answer after entitlement revocation.
      try {
        await authorizePdfRead(uid, docId);
      } catch (error) {
        if (
          error instanceof DocumentAccessError &&
          error.statusCode === 403
        ) {
          sendApiError(
            res, 403, "FORBIDDEN", "Document access denied"
          );
        } else {
          sendApiError(
            res, 503, "SERVICE_UNAVAILABLE",
            "Cannot verify document access"
          );
        }
        return;
      }
      res.status(200).json(answer);
    } catch {
      console.error("AI chat processing failed");
      sendApiError(
        res, 503, "SERVICE_UNAVAILABLE",
        "AI service temporarily unavailable"
      );
    }
  });

  return router;
}

export const aiChatRouter = createAiChatRouter();