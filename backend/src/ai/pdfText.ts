export type PdfTextPage = {
  page: number;
  text: string;
};

export class PdfTextError extends Error {
  constructor(
    public readonly code:
      | "INVALID_PDF"
      | "PDF_TOO_LARGE"
      | "TOO_MANY_PAGES"
      | "PDF_TEXT_UNAVAILABLE"
  ) {
    super(code);
    this.name = "PdfTextError";
  }
}

const MAX_PDF_BYTES = 2 * 1024 * 1024;
const MAX_PAGES = 20;
const MAX_TEXT_CHARS = 12000;

export async function extractPdfPages(
  pdfBytes: Buffer
): Promise<PdfTextPage[]> {
  if (
    !Buffer.isBuffer(pdfBytes) ||
    pdfBytes.length < 5 ||
    pdfBytes.subarray(0, 5).toString("ascii") !== "%PDF-"
  ) {
    throw new PdfTextError("INVALID_PDF");
  }

  if (pdfBytes.length > MAX_PDF_BYTES) {
    throw new PdfTextError("PDF_TOO_LARGE");
  }

  const { getDocumentProxy, extractText } = await import("unpdf");

  let pdf;

  try {
    pdf = await getDocumentProxy(
      new Uint8Array(pdfBytes),
      {
        maxImageSize: 16_777_216
      }
    );
  } catch {
    throw new PdfTextError("INVALID_PDF");
  }

  try {
    if (pdf.numPages < 1 || pdf.numPages > MAX_PAGES) {
      throw new PdfTextError("TOO_MANY_PAGES");
    }

    const result = await extractText(pdf, {
      mergePages: false
    });

    if (!Array.isArray(result.text)) {
      throw new PdfTextError("PDF_TEXT_UNAVAILABLE");
    }

    let remaining = MAX_TEXT_CHARS;
    const pages: PdfTextPage[] = [];

    for (let i = 0; i < result.text.length; i++) {
      if (remaining <= 0) break;

      const text = result.text[i].trim();
      if (!text) continue;

      const limited = text.slice(0, remaining);
      pages.push({
        page: i + 1,
        text: limited
      });

      remaining -= limited.length;
    }

    if (pages.length === 0) {
      throw new PdfTextError("PDF_TEXT_UNAVAILABLE");
    }

    return pages;
  } finally {
    if ("destroy" in pdf && typeof pdf.destroy === "function") {
      await pdf.destroy();
    }
  }
}