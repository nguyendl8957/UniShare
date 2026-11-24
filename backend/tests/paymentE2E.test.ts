// ============================================================================
// HẠ TẦNG VÀ CẤU HÌNH KIỂM THỬ E2E CHO ISSUE B07 #18
// ============================================================================

const TEST_RECIPIENT = "TEST_RECIPIENT_ONLY";
const TEST_SECRET = "EMULATOR_ONLY_SECRET";

// Thiết lập biến môi trường test an toàn trước khi nạp bất kỳ module nội bộ nào của dự án
process.env.FIREBASE_PROJECT_ID = process.env.FIREBASE_PROJECT_ID || "demo-unishare";
process.env.FIRESTORE_EMULATOR_HOST = process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8080";
process.env.FIREBASE_AUTH_EMULATOR_HOST = process.env.FIREBASE_AUTH_EMULATOR_HOST || "127.0.0.1:9099";
process.env.UNISHARE_PAYMENT_RECIPIENT_ID = process.env.UNISHARE_PAYMENT_RECIPIENT_ID || TEST_RECIPIENT;
process.env.SEPAY_WEBHOOK_SECRET = process.env.SEPAY_WEBHOOK_SECRET || TEST_SECRET;

import { test } from "node:test";
import assert from "node:assert/strict";
import {
  createHash,
  createHmac,
  randomBytes,
  randomUUID
} from "node:crypto";
import request from "supertest";
import { getFirestore, Firestore } from "firebase-admin/firestore";

import { app } from "../src/app";
import { initializeFirebaseAdmin } from "../src/shared/firebaseAuth";
import { OrderPolicy } from "../src/orders/orderPolicy";
import {
  authorizePdfRead,
  DocumentAccessError
} from "../src/documents/entitlement";

function ensureTestEnvironment(): void {
  if (
    process.env.FIREBASE_PROJECT_ID !== "demo-unishare" ||
    process.env.FIRESTORE_EMULATOR_HOST !== "127.0.0.1:8080" ||
    process.env.UNISHARE_PAYMENT_RECIPIENT_ID !== TEST_RECIPIENT ||
    process.env.SEPAY_WEBHOOK_SECRET !== TEST_SECRET
  ) {
    throw new Error("STOP: Yêu cầu chạy trên cấu hình Firebase Emulator an toàn");
  }
}

function bankTime(): string {
  return new Date(Date.now() + 7 * 3600_000)
    .toISOString()
    .slice(0, 19)
    .replace("T", " ");
}

function computePairKey(buyerId: string, docId: string): string {
  return createHash("sha256")
    .update(JSON.stringify([buyerId, docId]))
    .digest("hex");
}

/**
 * Helper khởi tạo đơn hàng mẫu và dữ liệu tài liệu tương ứng trên Firestore Emulator
 */
async function seedE2EOrderAndDocument(options?: {
  amountVnd?: number;
  expired?: boolean;
}) {
  const db = getFirestore();
  const suffix = randomUUID().replace(/-/g, "").slice(0, 12);
  const orderId = `ord_${randomBytes(8).toString("hex")}`;
  const docId = `doc_e2e_${suffix}`;
  const buyerId = `buyer1_e2e_${suffix}`;
  const sellerId = `seller_e2e_${suffix}`;
  const versionId = "v1";
  const amountVnd = options?.amountVnd ?? 12000;
  const paymentCode = OrderPolicy.generatePaymentCode();

  // 1. Tạo document và version trong collection documents
  await db.collection("documents").doc(docId).set({
    sellerId,
    title: `Tài liệu E2E ${suffix}`,
    priceVnd: amountVnd,
    activeVersionId: versionId,
    status: "PUBLISHED"
  });

  await db
    .collection("documents")
    .doc(docId)
    .collection("versions")
    .doc(versionId)
    .set({
      pdfPath: `private/${docId}/${versionId}/source.pdf`,
      pdfGeneration: "123456789",
      sizeBytes: 1024,
      createdAt: new Date().toISOString()
    });

  // 2. Tạo bản ghi đơn hàng
  const order = {
    orderId,
    buyerId,
    sellerId,
    docId,
    versionId,
    documentTitle: `Tài liệu E2E ${suffix}`,
    amountVnd,
    currency: "VND",
    status: "PENDING",
    paymentCode,
    recipientId: TEST_RECIPIENT,
    provider: "SEPAY",
    createdAt: new Date(Date.now() - 60_000).toISOString(),
    expiresAt: new Date(
      Date.now() + (options?.expired ? -10_000 : 900_000) // TTL 15 phút hoặc quá hạn
    ).toISOString()
  };

  await db.collection("orders").doc(orderId).set(order);

  // 3. Tạo ánh xạ paymentCode
  await db.collection("paymentCodes").doc(paymentCode).set({
    orderId,
    createdAt: order.createdAt
  });

  // 4. Tạo activePurchases lock
  const pairKey = computePairKey(buyerId, docId);

  await db.collection("activePurchases").doc(pairKey).set({
    buyerId,
    docId,
    orderId,
    state: "PENDING",
    expiresAt: order.expiresAt
  });

  return {
    order,
    docId,
    buyerId,
    sellerId,
    versionId,
    paymentCode,
    pairKey
  };
}

/**
 * Helper gửi webhook có chữ ký HMAC-SHA256 chuẩn SePay
 */
async function sendSignedSepayWebhook(options: {
  txnId: string;
  content: string;
  accountNumber?: string;
  amount?: number;
  secret?: string;
  timestampOffsetSec?: number;
}) {
  const rawBody = JSON.stringify({
    id: options.txnId,
    gateway: "MBBank",
    transactionDate: bankTime(),
    accountNumber: options.accountNumber ?? TEST_RECIPIENT,
    content: options.content,
    transferType: "in",
    transferAmount: options.amount ?? 12000
  });

  const timestamp = String(
    Math.floor(Date.now() / 1000) + (options.timestampOffsetSec ?? 0)
  );

  const signature = createHmac("sha256", options.secret ?? TEST_SECRET)
    .update(`${timestamp}.${rawBody}`)
    .digest("hex");

  return request(app)
    .post("/webhooks/sepay")
    .set("Content-Type", "application/json")
    .set("X-SePay-Timestamp", timestamp)
    .set("X-SePay-Signature", `sha256=${signature}`)
    .send(rawBody);
}

/**
 * Helper xác minh người dùng KHÔNG có quyền đọc PDF
 */
async function assertNoEntitlement(db: Firestore, buyerId: string, docId: string) {
  const snapshot = await db
    .collection("entitlements")
    .doc(buyerId)
    .collection("items")
    .doc(docId)
    .get();

  assert.equal(snapshot.exists, false, `Buyer ${buyerId} không được có entitlement cho ${docId}`);
}

// ============================================================================
// BỘ KIỂM THỬ TOÀN DIỆN E2E (P01 - P06)
// ============================================================================

test("B07 E2E Suite: Kiểm thử tích hợp toàn diện thanh toán và mở PDF", async () => {
  ensureTestEnvironment();
  initializeFirebaseAdmin();
  const db = getFirestore();

  // --------------------------------------------------------------------------
  // CA P01: KIỂM TRA TRƯỚC KHI MUA (PRE-PURCHASE DENY CHECK)
  // --------------------------------------------------------------------------
  const fixtureP01 = await seedE2EOrderAndDocument();
  const buyer2 = `buyer2_unauthorized_${randomUUID().slice(0, 8)}`;

  // Buyer 1 và Buyer 2 đều chưa mua -> không có quyền
  await assertNoEntitlement(db, fixtureP01.buyerId, fixtureP01.docId);
  await assertNoEntitlement(db, buyer2, fixtureP01.docId);

  // Kiểm tra qua hàm authorizePdfRead (nghiệp vụ backend cốt lõi A06)
  await assert.rejects(
    authorizePdfRead(fixtureP01.buyerId, fixtureP01.docId, db),
    (err: unknown) =>
      err instanceof DocumentAccessError &&
      err.statusCode === 403 &&
      err.code === "ENTITLEMENT_REQUIRED"
  );

  await assert.rejects(
    authorizePdfRead(buyer2, fixtureP01.docId, db),
    (err: unknown) =>
      err instanceof DocumentAccessError &&
      err.statusCode === 403 &&
      err.code === "ENTITLEMENT_REQUIRED"
  );
  console.log("PASS [P01]: Trước khi mua, cả Buyer 1 và Buyer 2 đều bị từ chối 403");

  // --------------------------------------------------------------------------
  // CA P02: XÁC THỰC BẢO MẬT CHỮ KÝ VÀ TIMESTAMP WEBHOOK
  // --------------------------------------------------------------------------
  const fixtureP02 = await seedE2EOrderAndDocument();
  const txnInvalidSig = `txn_bad_sig_${randomUUID().slice(0, 8)}`;

  // 1. Chữ ký HMAC sai
  const badSigRes = await sendSignedSepayWebhook({
    txnId: txnInvalidSig,
    content: `Thanh toan don ${fixtureP02.paymentCode}`,
    secret: "WRONG_SECRET_KEY"
  });
  assert.equal(badSigRes.status, 401);
  assert.equal(badSigRes.body.success, false);
  await assertNoEntitlement(db, fixtureP02.buyerId, fixtureP02.docId);

  // 2. Timestamp lệch quá 300 giây (ví dụ: lệch 400 giây trong tương lai)
  const txnExpiredTimestamp = `txn_exp_ts_${randomUUID().slice(0, 8)}`;
  const badTsRes = await sendSignedSepayWebhook({
    txnId: txnExpiredTimestamp,
    content: `Thanh toan don ${fixtureP02.paymentCode}`,
    timestampOffsetSec: 400
  });
  assert.equal(badTsRes.status, 400);
  assert.equal(badTsRes.body.success, false);
  await assertNoEntitlement(db, fixtureP02.buyerId, fixtureP02.docId);
  console.log("PASS [P02]: Chữ ký giả mạo hoặc timestamp lệch đều bị chặn 400/401");

  // --------------------------------------------------------------------------
  // CA P03: ĐỐI SOÁT CÁC CA ÂM (SAI TÀI KHOẢN, THIẾU TIỀN, THỪA TIỀN, SAI MÃ, NHIỀU MÃ)
  // --------------------------------------------------------------------------
  const fixtureP03 = await seedE2EOrderAndDocument({ amountVnd: 15000 });

  // P03a: Sai số tài khoản thụ hưởng
  const txnWrongAcc = `txn_wrong_acc_${randomUUID().slice(0, 8)}`;
  const wrongAccRes = await sendSignedSepayWebhook({
    txnId: txnWrongAcc,
    content: `Thanh toan ${fixtureP03.paymentCode}`,
    accountNumber: "9999999999" // Sai tài khoản
  });
  assert.equal(wrongAccRes.status, 200);
  assert.equal(wrongAccRes.body.status, "RECONCILIATION_REQUIRED");

  const reconWrongAcc = await db.collection("reconciliation").doc(`sepay_${txnWrongAcc}`).get();
  assert.equal(reconWrongAcc.get("reason"), "WRONG_RECIPIENT");
  await assertNoEntitlement(db, fixtureP03.buyerId, fixtureP03.docId);

  // P03b: Chuyển thiếu tiền (Đơn 15.000 VNĐ nhưng chuyển 10.000 VNĐ)
  const txnUnderpaid = `txn_underpaid_${randomUUID().slice(0, 8)}`;
  const underpaidRes = await sendSignedSepayWebhook({
    txnId: txnUnderpaid,
    content: `Thanh toan ${fixtureP03.paymentCode}`,
    amount: 10000
  });
  assert.equal(underpaidRes.status, 200);
  assert.equal(underpaidRes.body.status, "RECONCILIATION_REQUIRED");

  const reconUnderpaid = await db.collection("reconciliation").doc(`sepay_${txnUnderpaid}`).get();
  assert.equal(reconUnderpaid.get("reason"), "AMOUNT_MISMATCH");
  await assertNoEntitlement(db, fixtureP03.buyerId, fixtureP03.docId);

  // P03c: Chuyển thừa tiền (Đơn 15.000 VNĐ nhưng chuyển 25.000 VNĐ)
  const fixtureP03c = await seedE2EOrderAndDocument({ amountVnd: 15000 });
  const txnOverpaid = `txn_overpaid_${randomUUID().slice(0, 8)}`;
  const overpaidRes = await sendSignedSepayWebhook({
    txnId: txnOverpaid,
    content: `Thanh toan ${fixtureP03c.paymentCode}`,
    amount: 25000
  });
  assert.equal(overpaidRes.status, 200);
  assert.equal(overpaidRes.body.status, "RECONCILIATION_REQUIRED");

  const reconOverpaid = await db.collection("reconciliation").doc(`sepay_${txnOverpaid}`).get();
  assert.equal(reconOverpaid.get("reason"), "AMOUNT_MISMATCH");
  await assertNoEntitlement(db, fixtureP03c.buyerId, fixtureP03c.docId);

  // P03d: Không có mã đơn trong nội dung chuyển khoản
  const txnNoCode = `txn_no_code_${randomUUID().slice(0, 8)}`;
  const noCodeRes = await sendSignedSepayWebhook({
    txnId: txnNoCode,
    content: "Chuyen tien hoc phi khong co ma don"
  });
  assert.equal(noCodeRes.status, 200);
  assert.equal(noCodeRes.body.status, "RECONCILIATION_REQUIRED");

  const reconNoCode = await db.collection("reconciliation").doc(`sepay_${txnNoCode}`).get();
  assert.equal(reconNoCode.get("reason"), "MISSING_PAYMENT_CODE");
  await assertNoEntitlement(db, fixtureP03.buyerId, fixtureP03.docId);

  // P03e: Nội dung chứa từ 2 mã đơn khác nhau (chuyển gộp gian lận)
  const txnMultipleCodes = `txn_multi_codes_${randomUUID().slice(0, 8)}`;
  const multipleCodesRes = await sendSignedSepayWebhook({
    txnId: txnMultipleCodes,
    content: `Thanh toan don ${fixtureP03.paymentCode} va ca ma UNI999999`
  });
  assert.equal(multipleCodesRes.status, 200);
  assert.equal(multipleCodesRes.body.status, "RECONCILIATION_REQUIRED");

  const reconMultiCodes = await db.collection("reconciliation").doc(`sepay_${txnMultipleCodes}`).get();
  assert.equal(reconMultiCodes.get("reason"), "MULTIPLE_PAYMENT_CODES");
  await assertNoEntitlement(db, fixtureP03.buyerId, fixtureP03.docId);
  console.log("PASS [P03]: Toàn bộ 5 ca âm (sai TK, thiếu tiền, thừa tiền, không mã, nhiều mã) đều đối soát an toàn");

  // --------------------------------------------------------------------------
  // CA P04: XỬ LÝ TRÙNG LẶP VÀ TƯƠNG TRANH (IDEMPOTENCY & CONCURRENCY)
  // --------------------------------------------------------------------------
  const fixtureP04 = await seedE2EOrderAndDocument();
  const txnRace = `txn_race_${randomUUID().slice(0, 8)}`;
  const contentRace = `Thanh toan ${fixtureP04.paymentCode}`;

  // Bắn 2 webhook đồng thời
  const [resA, resB] = await Promise.all([
    sendSignedSepayWebhook({ txnId: txnRace, content: contentRace }),
    sendSignedSepayWebhook({ txnId: txnRace, content: contentRace })
  ]);

  assert.equal(resA.status, 200);
  assert.equal(resB.status, 200);

  const statuses = [resA.body.status, resB.body.status].sort();
  assert.deepEqual(statuses, ["DUPLICATE", "MATCHED"]);

  // Kiểm tra chỉ có duy nhất 1 bản ghi payment và 1 bản ghi entitlement
  const paymentRecord = await db.collection("payments").doc(`sepay_${txnRace}`).get();
  assert.equal(paymentRecord.exists, true);
  assert.equal(paymentRecord.get("result"), "MATCHED");

  const entitlementP04 = await db
    .collection("entitlements")
    .doc(fixtureP04.buyerId)
    .collection("items")
    .doc(fixtureP04.docId)
    .get();
  assert.equal(entitlementP04.exists, true);
  console.log("PASS [P04]: Xử lý webhook đồng thời bảo vệ tính Idempotent thành công");

  // --------------------------------------------------------------------------
  // CA P05: GIAO DỊCH SAU KHI ĐƠN HẾT HẠN (EXPIRED ORDER)
  // --------------------------------------------------------------------------
  const fixtureP05 = await seedE2EOrderAndDocument({ expired: true });
  const txnExpired = `txn_expired_${randomUUID().slice(0, 8)}`;

  const expiredRes = await sendSignedSepayWebhook({
    txnId: txnExpired,
    content: `Thanh toan ${fixtureP05.paymentCode}`
  });
  assert.equal(expiredRes.status, 200);
  assert.equal(expiredRes.body.status, "RECONCILIATION_REQUIRED");

  const reconExpired = await db.collection("reconciliation").doc(`sepay_${txnExpired}`).get();
  assert.equal(reconExpired.get("reason"), "ORDER_EXPIRED");
  await assertNoEntitlement(db, fixtureP05.buyerId, fixtureP05.docId);
  console.log("PASS [P05]: Đơn hàng quá hạn được cách ly đối soát, không cấp quyền");

  // --------------------------------------------------------------------------
  // CA P06: CHU TRÌNH DƯƠNG E2E ĐẦY ĐỦ (THANH TOÁN THÀNH CÔNG -> MỞ KHÓA PDF)
  // --------------------------------------------------------------------------
  const fixtureP06 = await seedE2EOrderAndDocument({ amountVnd: 20000 });
  const txnValid = `txn_e2e_valid_${randomUUID().slice(0, 8)}`;

  // 1. Trước khi thanh toán: Buyer 1 bị từ chối
  await assert.rejects(
    authorizePdfRead(fixtureP06.buyerId, fixtureP06.docId, db),
    (err: unknown) => err instanceof DocumentAccessError && err.statusCode === 403
  );

  // 2. Gửi webhook hợp lệ
  const validWebhookRes = await sendSignedSepayWebhook({
    txnId: txnValid,
    content: `Chuyen khoan don hang ${fixtureP06.paymentCode}`,
    amount: 20000
  });

  assert.equal(validWebhookRes.status, 200);
  assert.equal(validWebhookRes.body.status, "MATCHED");

  // 3. Kiểm tra trạng thái đơn hàng trên Firestore
  const updatedOrder = await db.collection("orders").doc(fixtureP06.order.orderId).get();
  assert.equal(updatedOrder.get("status"), "PAID");
  assert.ok(updatedOrder.get("paidAt"));

  // 4. Kiểm tra cập nhật khóa đơn activePurchases sang PAID
  const activePurchase = await db.collection("activePurchases").doc(fixtureP06.pairKey).get();
  assert.equal(activePurchase.exists, true);
  assert.equal(activePurchase.get("state"), "PAID");

  // 5. Kiểm tra quyền sở hữu Entitlement
  const grantedEntitlement = await db
    .collection("entitlements")
    .doc(fixtureP06.buyerId)
    .collection("items")
    .doc(fixtureP06.docId)
    .get();
  assert.equal(grantedEntitlement.exists, true);
  assert.equal(grantedEntitlement.get("versionId"), fixtureP06.versionId);
  assert.equal(grantedEntitlement.get("orderId"), fixtureP06.order.orderId);

  // 6. Kiểm tra truy cập PDF sau khi mua: Buyer 1 ĐƯỢC PHÉP ĐỌC
  const authorizedPdf = await authorizePdfRead(fixtureP06.buyerId, fixtureP06.docId, db);
  assert.equal(authorizedPdf.uid, fixtureP06.buyerId);
  assert.equal(authorizedPdf.docId, fixtureP06.docId);
  assert.equal(authorizedPdf.versionId, fixtureP06.versionId);
  assert.equal(authorizedPdf.pdfPath, `private/${fixtureP06.docId}/${fixtureP06.versionId}/source.pdf`);
  assert.equal(authorizedPdf.sizeBytes, 1024);

  // 7. Tài khoản đối chứng Buyer B: VẪN BỊ TỪ CHỐI 403
  const buyerB = `buyer_b_${randomUUID().slice(0, 8)}`;
  await assert.rejects(
    authorizePdfRead(buyerB, fixtureP06.docId, db),
    (err: unknown) =>
      err instanceof DocumentAccessError &&
      err.statusCode === 403 &&
      err.code === "ENTITLEMENT_REQUIRED"
  );
  console.log("PASS [P06]: Chu trình E2E hoàn tất: PAID nguyên tử, activePurchases=PAID, Buyer 1 mở PDF, Buyer B bị chặn");

  console.log("============================================================================");
  console.log("HOÀN THÀNH 100% CÁC CA KIỂM THỬ TÍCH HỢP P01-P06 CHO ISSUE B07 #18: PASS");
  console.log("============================================================================");
});
