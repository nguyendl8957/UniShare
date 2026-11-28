import { test } from "node:test";
import assert from "node:assert/strict";

import { createGeminiProvider } from "../src/ai/geminiProvider";

const input = {
  docId: "doc_test",
  question: "Thuật toán tìm kiếm nhị phân có độ phức tạp gì?",
  pages: [{
    page: 3,
    text: "Tìm kiếm nhị phân có độ phức tạp O(log n)."
  }]
};

function mockResponse(
  citations: Array<{ page: number }> = [{ page: 3 }]
) {
  return {
    candidates: [{
      finishReason: "STOP",
      content: {
        parts: [{
          text: JSON.stringify({
            answer: "Độ phức tạp là O(log n).",
            citations
          })
        }]
      }
    }],
    usageMetadata: { totalTokenCount: 310 }
  };
}

test("A08 Gemini provider adapter", async (t) => {
  await t.test("success sends PDF context and reads usage", async () => {
    let calls = 0;

    const fakeFetch = (async (
      url: unknown,
      options?: RequestInit
    ) => {
      calls++;

      assert.match(String(url), /gemini-3.5-flash-lite/);

      const body = JSON.parse(
        String(options?.body)
      );

      const text = body.contents[0].parts[0].text;

      assert.match(text, /O\(log n\)/);
      assert.match(text, /\[Trang 3\]/);
      assert.equal(
        body.generationConfig.maxOutputTokens,
        256
      );

      return new Response(
        JSON.stringify(mockResponse()),
        { status: 200 }
      );
    }) as typeof fetch;

    const provider = createGeminiProvider(
      "TEST_KEY_NOT_REAL",
      fakeFetch
    );

    const result = await provider.ask(input);

    assert.equal(calls, 1);
    assert.equal(result.actualTokens, 310);
    assert.equal(result.citations[0].page, 3);
    assert.match(result.answer, /O\(log n\)/);
  });

  await t.test("maps upstream throttle and service errors without retrying", async () => {
    for (const [status, code] of [[429, "GEMINI_RATE_LIMIT"], [503, "GEMINI_UNAVAILABLE"]] as const) {
      let calls = 0;
      const provider = createGeminiProvider("TEST_KEY", async () => {
        calls++;
        return new Response(null, { status });
      });
      await assert.rejects(provider.ask(input), { code });
      assert.equal(calls, 1);
    }
  });

  await t.test("maps a provider timeout without retrying", async () => {
    let calls = 0;
    const provider = createGeminiProvider("TEST_KEY", async () => {
      calls++;
      throw new DOMException("Test provider timeout", "TimeoutError");
    });
    await assert.rejects(provider.ask(input), { code: "GEMINI_TIMEOUT" });
    assert.equal(calls, 1);
  });

  await t.test("rejects citation outside provided pages", async () => {
    const fakeFetch = (async () =>
      new Response(
        JSON.stringify(mockResponse([{ page: 99 }])),
        { status: 200 }
      )
    ) as typeof fetch;

    const provider = createGeminiProvider("TEST_KEY", fakeFetch);

    await assert.rejects(
      provider.ask(input),
      /citation invalid/
    );
  });

  await t.test("rejects missing API key without a request", async () => {
    let calls = 0;

    const fakeFetch = (async () => {
      calls++;
      throw new Error("Should not be called");
    }) as typeof fetch;

    const provider = createGeminiProvider("", fakeFetch);

    await assert.rejects(
      provider.ask(input),
      /API key is not configured/
    );

    assert.equal(calls, 0);
  });

  await t.test("network failure is not silently retried", async () => {
    let calls = 0;

    const fakeFetch = (async () => {
      calls++;
      throw new Error("Network unavailable");
    }) as typeof fetch;

    const provider = createGeminiProvider("TEST_KEY", fakeFetch);

    await assert.rejects(
      provider.ask(input),
      /Network unavailable/
    );

    assert.equal(calls, 1);
  });
});