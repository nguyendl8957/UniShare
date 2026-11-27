import { test } from "node:test";
import assert from "node:assert/strict";
import {
  PDFDocument,
  StandardFonts
} from "pdf-lib";

import {
  extractPdfPages,
  PdfTextError
} from "../src/ai/pdfText";

async function makePdf(
  textOnPages: string[]
): Promise<Buffer> {
  const pdf = await PDFDocument.create();
  const font = await pdf.embedFont(StandardFonts.Helvetica);

  for (const text of textOnPages) {
    const page = pdf.addPage([595, 842]);

    if (text) {
      page.drawText(text, {
        x: 50,
        y: 750,
        size: 14,
        font
      });
    }
  }

  return Buffer.from(await pdf.save());
}

test("A08 secure PDF text extraction", async (t) => {
  await t.test("extracts text with correct page numbers", async () => {
    const bytes = await makePdf([
      "Binary search is O(log n).",
      "A stack follows LIFO."
    ]);

    const pages = await extractPdfPages(bytes);

    assert.equal(pages.length, 2);
    assert.equal(pages[0].page, 1);
    assert.match(pages[0].text, /Binary search/);
    assert.equal(pages[1].page, 2);
    assert.match(pages[1].text, /stack follows LIFO/);
  });

  await t.test("rejects non-PDF bytes", async () => {
    await assert.rejects(
      extractPdfPages(Buffer.from("not a PDF")),
      (error: unknown) =>
        error instanceof PdfTextError &&
        error.code === "INVALID_PDF"
    );
  });

  await t.test("rejects oversized PDF", async () => {
    const bytes = Buffer.alloc(
      2 * 1024 * 1024 + 1
    );
    bytes.write("%PDF-", 0, "ascii");

    await assert.rejects(
      extractPdfPages(bytes),
      (error: unknown) =>
        error instanceof PdfTextError &&
        error.code === "PDF_TOO_LARGE"
    );
  });

  await t.test("rejects PDFs with too many pages", async () => {
    const bytes = await makePdf(
      Array(21).fill("Sample text")
    );

    await assert.rejects(
      extractPdfPages(bytes),
      (error: unknown) =>
        error instanceof PdfTextError &&
        error.code === "TOO_MANY_PAGES"
    );
  });

  await t.test("rejects PDF without extractable text", async () => {
    const bytes = await makePdf([""]);

    await assert.rejects(
      extractPdfPages(bytes),
      (error: unknown) =>
        error instanceof PdfTextError &&
        error.code === "PDF_TEXT_UNAVAILABLE"
    );
  });
});