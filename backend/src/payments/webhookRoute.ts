import { randomUUID } from "node:crypto";
import express, { Router } from "express";

import { verifyWebhookSignature } from "./verifyWebhook";
import { FirestorePaymentStore } from "./firestorePaymentStore";

export const webhookRouter = Router();

webhookRouter.post(
  "/",
  express.raw({
    type: "application/json",
    limit: "128kb"
  }),
  async (req, res) => {
    const secret = process.env.SEPAY_WEBHOOK_SECRET?.trim();
    const recipient =
      process.env.UNISHARE_PAYMENT_RECIPIENT_ID?.trim();

    if (!secret || !recipient) {
      res.status(503).json({
        success: false,
        error: "WEBHOOK_NOT_CONFIGURED"
      });
      return;
    }

    if (!Buffer.isBuffer(req.body)) {
      res.status(415).json({
        success: false,
        error: "JSON_BODY_REQUIRED"
      });
      return;
    }

    const verification = verifyWebhookSignature(
      req.body.toString("utf8"),
      req.headers,
      secret
    );

    if (!verification.isValid || !verification.event) {
      res.status(verification.statusCode).json({
        success: false,
        error: verification.errorCode ?? "INVALID_WEBHOOK"
      });
      return;
    }

    try {
      const store = new FirestorePaymentStore(recipient);

      const result = await store.processVerifiedEvent(
        verification.event,
        randomUUID()
      );

      // ACK only after Firestore transaction commits.
      res.status(200).json({
        success: true,
        status: result.status
      });
    } catch {
      // Provider must retry after transient storage failure.
      // Never log payment credentials or raw webhook bodies.
      console.error("Webhook persistence failed");

      res.status(503).json({
        success: false,
        error: "PAYMENT_PERSISTENCE_UNAVAILABLE"
      });
    }
  }
);
