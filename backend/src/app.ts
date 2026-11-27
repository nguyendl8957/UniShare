import express from "express";

import { requireAuth } from "./shared/firebaseAuth";
import { sendApiError } from "./shared/httpErrors";

import { draftsRouter } from "./documents/drafts";
import { publishRouter } from "./documents/publish";
import { manageRouter } from "./documents/manage";
import { metadataRouter } from "./documents/metadata";
import { contentRouter } from "./documents/content";
import { orderRoutes } from "./orders/orderRoutes";
import { libraryRouter } from "./documents/library";
import { webhookRouter } from "./payments/webhookRoute";
import { aiChatRouter } from "./ai/chatRoute";
import { sellerEnrollmentRouter } from "./seller/enrollment";
import { sellerDocumentsRouter } from "./seller/documents";

export const app = express();

app.disable("x-powered-by");
app.use("/webhooks/sepay", webhookRouter);
app.use(express.json({ limit: "256kb" }));

app.get("/health", (_req, res) => {
  res.status(200).json({
    status: "ok",
    service: "unishare-backend"
  });
});

app.get("/v1/me", requireAuth, (_req, res) => {
  res.status(200).json({
    uid: res.locals.uid
  });
});

app.use("/v1/documents", draftsRouter);
app.use("/v1/documents", publishRouter);
app.use("/v1/documents", manageRouter);
app.use("/v1/documents", metadataRouter);
app.use("/v1/documents", contentRouter);
app.use("/v1/orders", orderRoutes);
app.use("/v1/me", libraryRouter);
app.use("/v1/ai", aiChatRouter);
app.use("/v1/seller", sellerEnrollmentRouter);
app.use("/v1/seller", sellerDocumentsRouter);

app.use((_req, res) => {
  sendApiError(res, 404, "NOT_FOUND", "Route not found");
});
