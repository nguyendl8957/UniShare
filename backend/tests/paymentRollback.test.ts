import { test } from "node:test";
import assert from "node:assert/strict";
import {
  createHash,
  createHmac,
  randomBytes,
  randomUUID
} from "node:crypto";

import {
  getFirestore,
  type Firestore,
  type Transaction
} from "firebase-admin/firestore";

import { initializeFirebaseAdmin } from "../src/shared/firebaseAuth";
import { OrderPolicy } from "../src/orders/orderPolicy";
import { FirestorePaymentStore } from "../src/payments/firestorePaymentStore";
import {
  verifyWebhookSignature
} from "../src/payments/verifyWebhook";

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
    throw new Error("STOP: Emulator-only configuration required");
  }
}

function pairKey(buyerId: string, docId: string): string {
  return createHash("sha256")
    .update(JSON.stringify([buyerId, docId]))
    .digest("hex");
}

function bankTimeVn(): string {
  return new Date(Date.now() + 7 * 3600_000)
    .toISOString()
    .slice(0, 19)
    .replace("T", " ");
}

test("A07: entitlement write failure rolls back PAID and payment", async () => {
  requireEmulator();
  initializeFirebaseAdmin();

  const db = getFirestore();
  const suffix = randomUUID().replace(/-/g, "");

  const buyerId = `buyer_${suffix}`;
  const sellerId = `seller_${suffix}`;
  const docId = `rollback_${suffix}`;
  const orderId = `ord_${randomBytes(8).toString("hex")}`;
  const paymentCode = OrderPolicy.generatePaymentCode();
  const txnId = `rollback_${suffix}`;
  const txnKey = `sepay_${txnId}`;

  const createdAt = new Date().toISOString();
  const expiresAt = new Date(
    Date.now() + 15 * 60_000
  ).toISOString();

  const orderRef = db.collection("orders").doc(orderId);

  const paymentRef = db
    .collection("payments")
    .doc(txnKey);

  const entitlementRef = db
    .collection("entitlements")
    .doc(buyerId)
    .collection("items")
    .doc(docId);

  const activeRef = db
    .collection("activePurchases")
    .doc(pairKey(buyerId, docId));

  const batch = db.batch();

  batch.create(orderRef, {
    orderId,
    buyerId,
    sellerId,
    docId,
    versionId: "v1",
    documentTitle: "A07 Rollback Fixture",
    amountVnd: 12000,
    currency: "VND",
    status: "PENDING",
    paymentCode,
    createdAt,
    expiresAt,
    recipientId: recipient,
    provider: "SEPAY"
  });

  batch.create(
    db.collection("paymentCodes").doc(paymentCode),
    { orderId, createdAt }
  );

  batch.create(activeRef, {
    buyerId,
    docId,
    orderId,
    state: "PENDING",
    expiresAt
  });

  await batch.commit();

  console.log("PASS: PENDING order fixture created");

  const rawBody = JSON.stringify({
    id: txnId,
    gateway: "MBBank",
    transactionDate: bankTimeVn(),
    accountNumber: recipient,
    content: `Thanh toan ${paymentCode}`,
    transferType: "in",
    transferAmount: 12000
  });

  const timestamp = String(
    Math.floor(Date.now() / 1000)
  );

  const signature = createHmac("sha256", secret)
    .update(`${timestamp}.${rawBody}`)
    .digest("hex");

  const verified = verifyWebhookSignature(
    rawBody,
    {
      "x-sepay-timestamp": timestamp,
      "x-sepay-signature": `sha256=${signature}`
    },
    secret
  );

  assert.equal(verified.isValid, true);
  assert.ok(verified.event);

  console.log("PASS: Signed webhook verified by B03");

  // Fault injection:
  // Queue payment and PAID writes as normal.
  // Then throw exactly when transaction.set()
  // attempts to write the entitlement.
  //
  // Since the transaction callback rejects,
  // Firestore must commit NONE of its queued writes.

  let faultTriggered = false;

  const faultyDb = new Proxy(db, {
    get(target, property) {
      if (property === "runTransaction") {
        return async (
          callback: (tx: Transaction) => Promise<unknown>
        ) => {
          return target.runTransaction(async originalTx => {
            const interceptedTx = new Proxy(originalTx, {
              get(txTarget, txProperty) {
                if (txProperty === "set") {
                  return (
                    ...args: unknown[]
                  ) => {
                    const reference = args[0] as {
                      path?: string
                    };

                    if (
                      reference?.path?.startsWith(
                        "entitlements/"
                      )
                    ) {
                      faultTriggered = true;
                      throw new Error(
                        "INJECTED_ENTITLEMENT_WRITE_FAILURE"
                      );
                    }

                    const originalSet = Reflect.get(
                      txTarget,
                      "set",
                      txTarget
                    ) as (...values: unknown[]) => unknown;

                    return originalSet.apply(
                      txTarget,
                      args
                    );
                  };
                }

                const value = Reflect.get(
                  txTarget,
                  txProperty,
                  txTarget
                );

                return typeof value === "function"
                  ? value.bind(txTarget)
                  : value;
              }
            });

            return callback(interceptedTx);
          });
        };
      }

      const value = Reflect.get(
        target,
        property,
        target
      );

      return typeof value === "function"
        ? value.bind(target)
        : value;
    }
  }) as Firestore;

  const faultyStore = new FirestorePaymentStore(
    recipient,
    faultyDb
  );

  await assert.rejects(
    () => faultyStore.processVerifiedEvent(
      verified.event!
    ),
    /INJECTED_ENTITLEMENT_WRITE_FAILURE/
  );

  assert.equal(faultTriggered, true);

  console.log("PASS: Entitlement write failure injected");

  const orderAfterFailure = await orderRef.get();
  const paymentAfterFailure = await paymentRef.get();
  const entitlementAfterFailure =
    await entitlementRef.get();
  const activeAfterFailure = await activeRef.get();

  assert.equal(
    orderAfterFailure.get("status"),
    "PENDING"
  );

  assert.equal(paymentAfterFailure.exists, false);
  assert.equal(entitlementAfterFailure.exists, false);

  assert.equal(
    activeAfterFailure.get("state"),
    "PENDING"
  );

  console.log("PASS: Order remains PENDING");
  console.log("PASS: No payment record committed");
  console.log("PASS: No entitlement committed");
  console.log("PASS: Atomic rollback verified");

  // Retry the SAME verified transaction
  // using the real Firestore instance.
  const workingStore = new FirestorePaymentStore(
    recipient,
    db
  );

  const retried = await workingStore.processVerifiedEvent(
    verified.event!
  );

  assert.equal(retried.status, "MATCHED");

  const finalOrder = await orderRef.get();
  const finalPayment = await paymentRef.get();
  const finalEntitlement = await entitlementRef.get();
  const finalActive = await activeRef.get();

  assert.equal(finalOrder.get("status"), "PAID");
  assert.equal(finalPayment.get("result"), "MATCHED");

  assert.equal(finalEntitlement.exists, true);
  assert.equal(
    finalEntitlement.get("orderId"),
    orderId
  );

  assert.equal(
    finalEntitlement.get("versionId"),
    "v1"
  );

  assert.equal(finalActive.get("state"), "PAID");

  console.log("PASS: Retry commits PAID");
  console.log("PASS: Retry grants correct entitlement");
  console.log("PASS: Active purchase state updated");

  const duplicate = await workingStore.processVerifiedEvent(
    verified.event!
  );

  assert.equal(duplicate.status, "DUPLICATE");

  console.log("PASS: Repeated retry is idempotent");
  console.log("PASS: A07 TRANSACTION ROLLBACK AND RETRY");
});
