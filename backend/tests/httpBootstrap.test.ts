import { test } from "node:test";
import assert from "node:assert/strict";
import request from "supertest";
import { app } from "../src/app";

test("GET /health returns 200", async () => {
  const res = await request(app).get("/health");

  assert.equal(res.status, 200);
  assert.equal(res.body.status, "ok");
});

test("GET /v1/me without token returns 401", async () => {
  const res = await request(app).get("/v1/me");

  assert.equal(res.status, 401);
  assert.equal(res.body.error.code, "UNAUTHENTICATED");
  assert.ok(res.body.error.requestId);
});

test("Unknown route returns safe 404", async () => {
  const res = await request(app).get("/unknown");

  assert.equal(res.status, 404);
  assert.equal(res.body.error.code, "NOT_FOUND");
  assert.ok(res.body.error.requestId);
});
