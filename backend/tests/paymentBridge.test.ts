import { test } from "node:test";
import assert from "node:assert/strict";
import {
  createHmac,
  randomBytes,
  randomUUID
} from "node:crypto";
import request from "supertest";
import { getFirestore } from "firebase-admin/firestore";

import { app } from "../src/app";
import { initializeFirebaseAdmin } from "../src/shared/firebaseAuth";
import { OrderPolicy } from "../src/orders/orderPolicy";

const recipient = "TEST_RECIPIENT_ONLY";
const secret = "EMULATOR_ONLY_SECRET";

function requireEmulator(): void {
  if (
    process.env.FIREBASE_PROJECT_ID !== "demo-unishare" ||
    process.env.FIRESTORE_EMULATOR_HOST !== "127.0.0.1:8080" ||
    process.env.FIREBASE_AUTH_EMULATOR_HOST !== "127.0.0.1:9099" ||
    process.env.UNISHARE_PAYMENT_RECIPIENT_ID !== recipient ||
    process.env.SEPAY_WEBHOOK_SECRET !== secret
  ) {
    throw new Error("STOP: Emulator configuration required");
  }
}

function bankTime(): string {
  return new Date(Date.now() + 7 * 3600_000)
    .toISOString()
    .slice(0, 19)
    .replace("T", " ");
}

async function seedOrder(expired = false) {
  const db = getFirestore();
  const suffix = randomUUID().replace(/-/g, "");
  const orderId = `ord_${randomBytes(8).toString("hex")}`;
  const docId = `a07bridge_${suffix}`;
  const buyerId = `a07buyer_${suffix}`;
  const paymentCode = OrderPolicy.generatePaymentCode();

  const order = {
    orderId,
    buyerId,
    sellerId: `a07seller_${suffix}`,
    docId,
    versionId: "v1",
    documentTitle: "A07 Emulator Fixture",
    amountVnd: 12000,
    currency: "VND",
    status: "PENDING",
    paymentCode,
    createdAt: new Date(Date.now() - 60_000).toISOString(),
    expiresAt: new Date(
      Date.now() + (expired ? -10_000 : 600_000)
    ).toISOString()
  };

  await db.collection("orders").doc(orderId).create(order);

  await db.collection("paymentCodes").doc(paymentCode).create({
    orderId,
    createdAt: order.createdAt
  });

  return order;
}

async function signedWebhook(
  txnId: string,
  content: string,
  accountNumber = recipient,
  amount = 12000
) {
  const rawBody = JSON.stringify({
    id: txnId,
    gateway: "MBBank",
    transactionDate: bankTime(),
    accountNumber,
    content,
    transferType: "in",
    transferAmount: amount
  });

  const timestamp = String(Math.floor(Date.now() / 1000));
  const signature = createHmac("sha256", secret)
    .update(`${timestamp}.${rawBody}`)
    .digest("hex");

  return request(app)
    .post("/webhooks/sepay")
    .set("Content-Type", "application/json")
    .set("X-SePay-Timestamp", timestamp)
    .set("X-SePay-Signature", `sha256=${signature}`)
    .send(rawBody);
}

async function assertNoEntitlement(
  buyerId: string,
  docId: string
) {
  const entitlement = await getFirestore()
    .collection("entitlements")
    .doc(buyerId)
    .collection("items")
    .doc(docId)
    .get();

  assert.equal(entitlement.exists, false);
}

test("A07 payment bridge security and concurrency", async () => {
  requireEmulator();
  initializeFirebaseAdmin();

  const db = getFirestore();

  // Case 1: Wrong bank recipient.
  const wrongRecipientOrder = await seedOrder();
  const wrongRecipientTxn = `a07_${randomUUID()}`;

  const wrongRecipient = await signedWebhook(
    wrongRecipientTxn,
    `Thanh toan ${wrongRecipientOrder.paymentCode}`,
    "WRONG_ACCOUNT"
  );

  assert.equal(wrongRecipient.status, 200);
  assert.equal(
    wrongRecipient.body.status,
    "RECONCILIATION_REQUIRED"
  );

  const wrongRecipientReview = await db
    .collection("reconciliation")
    .doc(`sepay_${wrongRecipientTxn}`)
    .get();

  assert.equal(
    wrongRecipientReview.get("reason"),
    "WRONG_RECIPIENT"
  );

  await assertNoEntitlement(
    wrongRecipientOrder.buyerId,
    wrongRecipientOrder.docId
  );

  console.log("PASS: Wrong recipient never grants PDF");

  // Case 2: Missing payment code.
  const missingCodeOrder = await seedOrder();
  const missingCodeTxn = `a07_${randomUUID()}`;

  const missingCode = await signedWebhook(
    missingCodeTxn,
    "Chuyen khoan khong co ma don"
  );

  assert.equal(missingCode.status, 200);
  assert.equal(
    missingCode.body.status,
    "RECONCILIATION_REQUIRED"
  );

  const missingCodeReview = await db
    .collection("reconciliation")
    .doc(`sepay_${missingCodeTxn}`)
    .get();

  assert.equal(
    missingCodeReview.get("reason"),
    "MISSING_PAYMENT_CODE"
  );

  await assertNoEntitlement(
    missingCodeOrder.buyerId,
    missingCodeOrder.docId
  );

  console.log("PASS: Missing payment code never grants PDF");

  // Case 3: Payment after order expiry.
  const expiredOrder = await seedOrder(true);
  const expiredTxn = `a07_${randomUUID()}`;

  const expired = await signedWebhook(
    expiredTxn,
    `Thanh toan ${expiredOrder.paymentCode}`
  );

  assert.equal(expired.status, 200);
  assert.equal(
    expired.body.status,
    "RECONCILIATION_REQUIRED"
  );

  const expiredReview = await db
    .collection("reconciliation")
    .doc(`sepay_${expiredTxn}`)
    .get();

  assert.equal(
    expiredReview.get("reason"),
    "ORDER_EXPIRED"
  );

  await assertNoEntitlement(
    expiredOrder.buyerId,
    expiredOrder.docId
  );

  console.log("PASS: Expired order never grants PDF");

  // Case 4: Concurrent delivery of the same transaction.
  const raceOrder = await seedOrder();
  const raceTxn = `a07_${randomUUID()}`;
  const raceContent = `Thanh toan ${raceOrder.paymentCode}`;

  const [raceA, raceB] = await Promise.all([
    signedWebhook(raceTxn, raceContent),
    signedWebhook(raceTxn, raceContent)
  ]);

  assert.equal(raceA.status, 200, JSON.stringify(raceA.body));
  assert.equal(raceB.status, 200, JSON.stringify(raceB.body));

  const statuses = [
    raceA.body.status,
    raceB.body.status
  ].sort();

  assert.deepEqual(statuses, ["DUPLICATE", "MATCHED"]);

  const paidOrder = await db
    .collection("orders")
    .doc(raceOrder.orderId)
    .get();

  const entitlement = await db
    .collection("entitlements")
    .doc(raceOrder.buyerId)
    .collection("items")
    .doc(raceOrder.docId)
    .get();

  const payment = await db
    .collection("payments")
    .doc(`sepay_${raceTxn}`)
    .get();

  assert.equal(paidOrder.get("status"), "PAID");
  assert.equal(entitlement.exists, true);
  assert.equal(entitlement.get("orderId"), raceOrder.orderId);
  assert.equal(payment.get("result"), "MATCHED");

  console.log("PASS: Concurrent duplicate creates one payment");
  console.log("PASS: Concurrent duplicate grants one entitlement");
  console.log("PASS: A07.2d SECURITY AND CONCURRENCY");
});
