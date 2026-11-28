import type { PdfTextPage } from "./pdfText";

export type AiProviderReply = {
  answer: string;
  citations: Array<{ page: number }>;
  requestId: string;
  actualTokens: number;
};

export class AiProviderError extends Error {
  constructor(
    readonly code: "GEMINI_RATE_LIMIT" | "GEMINI_TIMEOUT" | "GEMINI_UNAVAILABLE" | "GEMINI_RESPONSE_INVALID",
    message: string
  ) {
    super(message);
  }
}

export interface AiProvider {
  ask(input: {
    docId: string;
    question: string;
    pages: PdfTextPage[];
  }): Promise<AiProviderReply>;
}

// Only throw this when NO request was sent to the AI provider.
export class AiProviderNotSentError extends Error {
  constructor() {
    super("AI request was not sent");
    this.name = "AiProviderNotSentError";
  }
}