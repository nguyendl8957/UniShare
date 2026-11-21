import { test } from "node:test";
import assert from "node:assert/strict";
import express from "express";
import request from "supertest";
import { webhookRouter } from "../src/payments/webhookRoute";

test("Cloud Functions rawBody is accepted for HMAC verification", async () => {
  const previousSecret = process.env.SEPAY_WEBHOOK_SECRET;
  const previousRecipient = process.env.UNISHARE_PAYMENT_RECIPIENT_ID;

  try {
    process.env.SEPAY_WEBHOOK_SECRET = "EMULATOR_ONLY_SECRET";
    process.env.UNISHARE_PAYMENT_RECIPIENT_ID = "TEST_RECIPIENT_ONLY";

    const app = express();

    app.use(express.json());

    app.use((req, _res, next) => {
      (req as typeof req & { rawBody?: Buffer }).rawBody =
        Buffer.from('{"test":"invalid-signature"}', "utf8");
      next();
    });

    app.use("/webhooks/sepay", webhookRouter);

    const response = await request(app)
      .post("/webhooks/sepay")
      .set("Content-Type", "application/json")
      .send({ test: "invalid-signature" });

    assert.notEqual(response.status, 415);
    assert.notEqual(response.status, 503);
    assert.notEqual(response.status, 200);

    console.log("Cloud rawBody reached signature verification.");
  } finally {
    if (previousSecret === undefined) {
      delete process.env.SEPAY_WEBHOOK_SECRET;
    } else {
      process.env.SEPAY_WEBHOOK_SECRET = previousSecret;
    }

    if (previousRecipient === undefined) {
      delete process.env.UNISHARE_PAYMENT_RECIPIENT_ID;
    } else {
      process.env.UNISHARE_PAYMENT_RECIPIENT_ID = previousRecipient;
    }
  }
});
