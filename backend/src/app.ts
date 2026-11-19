import express from "express";
import { requireAuth } from "./shared/firebaseAuth";
import { sendApiError } from "./shared/httpErrors";

export const app = express();

app.disable("x-powered-by");
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

app.use((_req, res) => {
  sendApiError(res, 404, "NOT_FOUND", "Route not found");
});
