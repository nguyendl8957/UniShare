import { test } from "node:test";
import assert from "node:assert/strict";

import { isProductionAiAllowed } from "../src/ai/chatRoute";

test("AI production allowlist security", async (t) => {
  const base = {
    enabled: "true",
    apiKey: "FAKE_TEST_KEY_ONLY",
    allowlist: "uid_one, uid_two"
  };

  await t.test("approved UID is allowed", () => {
    assert.equal(isProductionAiAllowed("uid_one", base), true);
    assert.equal(isProductionAiAllowed("uid_two", base), true);
  });

  await t.test("unapproved UID is denied", () => {
    assert.equal(isProductionAiAllowed("uid_other", base), false);
  });

  await t.test("partial UID cannot match", () => {
    assert.equal(isProductionAiAllowed("uid", base), false);
  });

  await t.test("empty allowlist denies everyone", () => {
    assert.equal(
      isProductionAiAllowed("uid_one", {
        ...base,
        allowlist: ""
      }),
      false
    );
  });

  await t.test("disabled flag denies approved UID", () => {
    assert.equal(
      isProductionAiAllowed("uid_one", {
        ...base,
        enabled: "false"
      }),
      false
    );
  });

  await t.test("missing key denies approved UID", () => {
    assert.equal(
      isProductionAiAllowed("uid_one", {
        ...base,
        apiKey: ""
      }),
      false
    );
  });

  await t.test("spaces around UIDs are handled", () => {
    assert.equal(
      isProductionAiAllowed("uid_one", {
        ...base,
        allowlist: " uid_one , uid_two "
      }),
      true
    );
  });

  await t.test("empty UID is always denied", () => {
    assert.equal(isProductionAiAllowed("", base), false);
  });
});