import type { Response } from "express";
import { randomUUID } from "node:crypto";

export type ApiErrorCode =
  | "INVALID_REQUEST"
  | "UNAUTHENTICATED"
  | "FORBIDDEN"
  | "NOT_FOUND"
  | "SERVICE_UNAVAILABLE";

export function sendApiError(
  res: Response,
  status: number,
  code: ApiErrorCode,
  message: string
): void {
  res.status(status).json({
    error: {
      code,
      message,
      requestId: randomUUID()
    }
  });
}