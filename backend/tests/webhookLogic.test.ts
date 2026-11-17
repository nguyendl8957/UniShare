import * as assert from 'assert';
import * as crypto from 'crypto';
import {
    extractPaymentCodeFromContent,
    NormalizedWebhookEvent,
    parseBankTimeToIso,
    SePayRawPayload,
    SIGNATURE_TOLERANCE_SECONDS,
    verifyWebhookSignature
} from '../src/payments/verifyWebhook';
import {
    ExpectedWriteSet,
    IPaymentRepository,
    matchPaymentTransaction,
    OrderSnapshot,
    PaymentMatchResult,
    PaymentRecord,
    ReconciliationRecord
} from '../src/payments/matchPayment';

// ============================================================================
// HẠ TẦNG GIẢ LẬP PHỤC VỤ KIỂM THỬ (FAKE TEST INFRASTRUCTURE)
// Issue: [B03] Xác minh webhook và đối soát giao dịch
// ============================================================================

/**
 * 1. FakeClock: Đồng hồ thời gian giả lập
 * Giúp kiểm tra các mốc thời gian quá khứ/tương lai và thời hạn TTL 15 phút
 * mà không phụ thuộc vào đồng hồ thực của hệ thống.
 */
export class FakeClock {
    private currentTimeMs: number;

    constructor(initialTimeMs: number = 1794823200000) { // 2026-11-17T10:00:00.000Z
        this.currentTimeMs = initialTimeMs;
    }

    now(): number {
        return this.currentTimeMs;
    }

    advanceMinutes(minutes: number): void {
        this.currentTimeMs += minutes * 60 * 1000;
    }

    advanceSeconds(seconds: number): void {
        this.currentTimeMs += seconds * 1000;
    }

    setTime(timeMs: number): void {
        this.currentTimeMs = timeMs;
    }
}

/**
 * 2. FakePaymentRepository: Kho lưu trữ in-memory giả lập
 * Triển khai đầy đủ Port Interface IPaymentRepository hoàn toàn trên bộ nhớ RAM.
 */
export class FakePaymentRepository implements IPaymentRepository {
    private payments: Map<string, PaymentRecord> = new Map();
    private ordersByCode: Map<string, OrderSnapshot> = new Map();
    private entitlements: Set<string> = new Set();
    private systemRecipientAccount: string = '0123456789';

    // --- Các hàm tiện ích nạp dữ liệu mẫu (Seed Helpers) ---

    seedPayment(payment: PaymentRecord): void {
        this.payments.set(payment.txnKey, payment);
    }

    seedOrder(order: OrderSnapshot): void {
        this.ordersByCode.set(order.paymentCode, order);
    }

    seedEntitlement(buyerId: string, docId: string): void {
        this.entitlements.add(`${buyerId}:${docId}`);
    }

    setSystemRecipientAccount(accountNumber: string): void {
        this.systemRecipientAccount = accountNumber;
    }

    // --- Triển khai IPaymentRepository ---

    async getPaymentByTxnKey(txnKey: string): Promise<PaymentRecord | null> {
        return this.payments.get(txnKey) || null;
    }

    async getOrderByPaymentCode(paymentCode: string): Promise<OrderSnapshot | null> {
        return this.ordersByCode.get(paymentCode) || null;
    }

    async hasEntitlement(buyerId: string, docId: string): Promise<boolean> {
        return this.entitlements.has(`${buyerId}:${docId}`);
    }

    async getSystemRecipientAccount(): Promise<string> {
        return this.systemRecipientAccount;
    }
}

// ============================================================================
// HÀM TIỆN ÍCH TẠO FIXTURE KIỂM THỬ (TEST FIXTURE FACTORIES)
// ============================================================================

const TEST_SECRET_KEY = 'unishare_webhook_secret_key_super_safe';
const TEST_ACCOUNT_NUMBER = '0123456789';

/**
 * Tạo chữ ký HMAC-SHA256 chuẩn phục vụ test
 */
export function generateValidSignature(rawBody: string, timestamp: number | string, secretKey: string = TEST_SECRET_KEY): string {
    const payload = `${timestamp}.${rawBody}`;
    return crypto.createHmac('sha256', secretKey).update(payload).digest('hex');
}

/**
 * Tạo đối tượng payload mẫu từ SePay
 */
export function createSampleSePayPayload(override: Partial<SePayRawPayload> = {}): SePayRawPayload {
    return {
        id: 998877,
        gateway: 'MBBank',
        transactionDate: '2026-11-17 10:05:00',
        accountNumber: TEST_ACCOUNT_NUMBER,
        code: 'UNIA1B2C3',
        content: 'Chuyen tien mua sach UNIA1B2C3',
        transferType: 'in',
        transferAmount: 50000,
        referenceCode: 'FT261117998877',
        ...override
    };
}

/**
 * Tạo đơn hàng mẫu còn hạn PENDING
 */
export function createSampleOrder(override: Partial<OrderSnapshot> = {}): OrderSnapshot {
    const createdAtMs = 1794823200000; // 2026-11-17T10:00:00.000Z
    const expiresAtMs = createdAtMs + 15 * 60 * 1000; // +15 phút: 10:15:00

    return {
        orderId: 'ord_sample_123',
        buyerId: 'buyer_abc_456',
        docId: 'doc_giao_trinh_toan',
        amountVnd: 50000,
        status: 'PENDING',
        paymentCode: 'UNIA1B2C3',
        createdAt: new Date(createdAtMs).toISOString(),
        expiresAt: new Date(expiresAtMs).toISOString(),
        ...override
    };
}

// ============================================================================
// BỘ KIỂM THỬ P02: XÁC THỰC WEBHOOK & AN TOÀN CHỮ KÝ (P02 TEST SUITE)
// ============================================================================

export async function runP02Tests(): Promise<{ passed: number; failed: number }> {
    console.log('\n--- [TEST SUITE P02] XÁC THỰC CHỮ KÝ WEBHOOK & AN TOÀN BẢO MẬT ---');
    let passed = 0;
    let failed = 0;

    const clock = new FakeClock();

    // TC-01: Chữ ký HMAC đúng, timestamp trong hạn 5 phút -> Thành công 200 OK
    try {
        const payloadObj = createSampleSePayPayload();
        const rawBody = JSON.stringify(payloadObj);
        const timestamp = Math.floor(clock.now() / 1000); // Unix timestamp dạng giây
        const signature = generateValidSignature(rawBody, timestamp);

        const headers = {
            'x-sepay-signature': signature,
            'x-sepay-timestamp': String(timestamp)
        };

        const result = verifyWebhookSignature(rawBody, headers, TEST_SECRET_KEY, clock.now());

        assert.strictEqual(result.isValid, true, 'Xác thực phải thành công');
        assert.strictEqual(result.statusCode, 200);
        assert.ok(result.event, 'Phải có event chuẩn hóa');
        assert.strictEqual(result.event?.provider, 'SEPAY');
        assert.strictEqual(result.event?.providerTxnId, '998877');
        assert.strictEqual(result.event?.amountVnd, 50000);
        assert.strictEqual(result.event?.direction, 'IN');
        assert.strictEqual(result.event?.paymentCode, 'UNIA1B2C3');
        assert.strictEqual(result.event?.hasMultipleCodes, false);

        console.log('  [PASS] TC-01: Chữ ký hợp lệ, timestamp trong hạn xác thực thành công (200 OK).');
        passed++;
    } catch (err) {
        console.error('  [FAIL] TC-01:', (err as Error).message);
        failed++;
    }

    // TC-02: Chữ ký bị sai 1 ký tự -> Bị từ chối 401 INVALID_SIGNATURE
    try {
        const rawBody = JSON.stringify(createSampleSePayPayload());
        const timestamp = Math.floor(clock.now() / 1000);
        const validSig = generateValidSignature(rawBody, timestamp);
        // Thay đổi ký tự cuối của chữ ký
        const invalidSig = validSig.slice(0, -1) + (validSig.endsWith('0') ? '1' : '0');

        const headers = {
            'x-sepay-signature': invalidSig,
            'x-sepay-timestamp': String(timestamp)
        };

        const result = verifyWebhookSignature(rawBody, headers, TEST_SECRET_KEY, clock.now());

        assert.strictEqual(result.isValid, false);
        assert.strictEqual(result.statusCode, 401);
        assert.strictEqual(result.errorCode, 'INVALID_SIGNATURE');

        console.log('  [PASS] TC-02: Chữ ký sai lệch bị từ chối chính xác (401 INVALID_SIGNATURE).');
        passed++;
    } catch (err) {
        console.error('  [FAIL] TC-02:', (err as Error).message);
        failed++;
    }

    // TC-03: Chữ ký sai độ dài Buffer -> Bị từ chối 401 constant-time an toàn
    try {
        const rawBody = JSON.stringify(createSampleSePayPayload());
        const timestamp = Math.floor(clock.now() / 1000);

        const headers = {
            'x-sepay-signature': 'too_short_sig',
            'x-sepay-timestamp': String(timestamp)
        };

        const result = verifyWebhookSignature(rawBody, headers, TEST_SECRET_KEY, clock.now());

        assert.strictEqual(result.isValid, false);
        assert.strictEqual(result.statusCode, 401);
        assert.strictEqual(result.errorCode, 'INVALID_SIGNATURE');

        console.log('  [PASS] TC-03: Chữ ký sai độ dài không gây lỗi runtime, từ chối an toàn.');
        passed++;
    } catch (err) {
        console.error('  [FAIL] TC-03:', (err as Error).message);
        failed++;
    }

    // TC-04: Thiếu header chữ ký hoặc timestamp -> Bị từ chối 400 MISSING_HEADERS
    try {
        const rawBody = JSON.stringify(createSampleSePayPayload());

        // Thiếu signature
        const res1 = verifyWebhookSignature(rawBody, { 'x-sepay-timestamp': '1794823200' }, TEST_SECRET_KEY, clock.now());
        assert.strictEqual(res1.statusCode, 400);
        assert.strictEqual(res1.errorCode, 'MISSING_HEADERS');

        // Thiếu timestamp
        const res2 = verifyWebhookSignature(rawBody, { 'x-sepay-signature': 'some_sig' }, TEST_SECRET_KEY, clock.now());
        assert.strictEqual(res2.statusCode, 400);
        assert.strictEqual(res2.errorCode, 'MISSING_HEADERS');

        console.log('  [PASS] TC-04: Thiếu header bắt buộc trả về 400 MISSING_HEADERS.');
        passed++;
    } catch (err) {
        console.error('  [FAIL] TC-04:', (err as Error).message);
        failed++;
    }

    // TC-05: Timestamp quá cũ (lệch quá 5 phút) -> Bị từ chối 400 TIMESTAMP_OUT_OF_WINDOW
    try {
        const rawBody = JSON.stringify(createSampleSePayPayload());
        // Lùi timestamp về 6 phút trước (360 giây > 300 giây tolerance)
        const oldTimestamp = Math.floor((clock.now() - 360 * 1000) / 1000);
        const signature = generateValidSignature(rawBody, oldTimestamp);

        const headers = {
            'x-sepay-signature': signature,
            'x-sepay-timestamp': String(oldTimestamp)
        };

        const result = verifyWebhookSignature(rawBody, headers, TEST_SECRET_KEY, clock.now());

        assert.strictEqual(result.isValid, false);
        assert.strictEqual(result.statusCode, 400);
        assert.strictEqual(result.errorCode, 'TIMESTAMP_OUT_OF_WINDOW');

        console.log('  [PASS] TC-05: Chặn tấn công Replay Attack khi timestamp quá hạn 5 phút.');
        passed++;
    } catch (err) {
        console.error('  [FAIL] TC-05:', (err as Error).message);
        failed++;
    }

    // TC-06: Raw body bị format lại (thêm dấu cách) dù JSON cùng nội dung -> Chữ ký fail
    try {
        const originalBody = '{"id":123,"transferAmount":50000}';
        const timestamp = Math.floor(clock.now() / 1000);
        const signature = generateValidSignature(originalBody, timestamp);

        // Body bị reserialize thêm khoảng trắng
        const formattedBody = '{ "id": 123, "transferAmount": 50000 }';

        const headers = {
            'x-sepay-signature': signature,
            'x-sepay-timestamp': String(timestamp)
        };

        const result = verifyWebhookSignature(formattedBody, headers, TEST_SECRET_KEY, clock.now());

        assert.strictEqual(result.isValid, false, 'Body bị format lại phải làm chữ ký fail');
        assert.strictEqual(result.statusCode, 401);

        console.log('  [PASS] TC-06: Phát hiện và chặn body bị parse/reserialize làm thay đổi raw bytes.');
        passed++;
    } catch (err) {
        console.error('  [FAIL] TC-06:', (err as Error).message);
        failed++;
    }

    // TC-07: Helper extractPaymentCodeFromContent
    try {
        // Một mã hợp lệ
        const r1 = extractPaymentCodeFromContent('CK tien mua sach UNIA1B2C3 nhe');
        assert.strictEqual(r1.code, 'UNIA1B2C3');
        assert.strictEqual(r1.hasMultipleCodes, false);

        // Chữ thường tự đổi sang in hoa
        const r2 = extractPaymentCodeFromContent('unia1b2c3');
        assert.strictEqual(r2.code, 'UNIA1B2C3');

        // Không có mã
        const r3 = extractPaymentCodeFromContent('Chuyen tien ban oi');
        assert.strictEqual(r3.code, null);
        assert.strictEqual(r3.hasMultipleCodes, false);

        // Chứa 2 mã khác nhau
        const r4 = extractPaymentCodeFromContent('Nap UNIA1B2C3 va UNID4E5F6');
        assert.strictEqual(r4.code, null);
        assert.strictEqual(r4.hasMultipleCodes, true, 'Phát hiện có 2 mã khác nhau');

        console.log('  [PASS] TC-07: Helper trích xuất mã thanh toán và phát hiện đa mã chính xác.');
        passed++;
    } catch (err) {
        console.error('  [FAIL] TC-07:', (err as Error).message);
        failed++;
    }

    return { passed, failed };
}

// ============================================================================
// BỘ KIỂM THỬ P03: ĐỐI SOÁT NGÂN HÀNG & SAI LỆCH NGHIỆP VỤ (P03 TEST SUITE)
// ============================================================================

export async function runP03Tests(): Promise<{ passed: number; failed: number }> {
    console.log('\n--- [TEST SUITE P03] ĐỐI SOÁT GIAO DỊCH & XỬ LÝ SAI LỆCH ---');
    let passed = 0;
    let failed = 0;

    const clock = new FakeClock();

    // TC-08: Giao dịch chiều rút tiền (OUT) -> Chuyển đối soát WRONG_DIRECTION
    try {
        const repo = new FakePaymentRepository();
        const event: NormalizedWebhookEvent = {
            provider: 'SEPAY',
            providerTxnId: 'txn_out_1',
            amountVnd: 50000,
            recipientAccountNumber: TEST_ACCOUNT_NUMBER,
            direction: 'OUT',
            content: 'Rut tien',
            paymentCode: 'UNIA1B2C3',
            hasMultipleCodes: false,
            bankOccurredAt: new Date(clock.now()).toISOString(),
            signatureVerifiedAt: new Date(clock.now()).toISOString(),
            rawPayload: createSampleSePayPayload({ transferType: 'out' })
        };

        const result = await matchPaymentTransaction(event, repo, clock.now());

        assert.strictEqual(result.status, 'RECONCILIATION_REQUIRED');
        assert.strictEqual(result.reason, 'WRONG_DIRECTION');
        assert.strictEqual(result.writeSet?.createReconciliation?.reason, 'WRONG_DIRECTION');

        console.log('  [PASS] TC-08: Giao dịch chiều rút tiền (OUT) chuyển đối soát WRONG_DIRECTION.');
        passed++;
    } catch (err) {
        console.error('  [FAIL] TC-08:', (err as Error).message);
        failed++;
    }

    // TC-09: Chuyển nhầm vào số tài khoản lạ -> Chuyển đối soát WRONG_RECIPIENT
    try {
        const repo = new FakePaymentRepository();
        repo.setSystemRecipientAccount('0123456789');

        const event: NormalizedWebhookEvent = {
            provider: 'SEPAY',
            providerTxnId: 'txn_wrong_rec_1',
            amountVnd: 50000,
            recipientAccountNumber: '9999999999', // Tài khoản lạ
            direction: 'IN',
            content: 'UNIA1B2C3',
            paymentCode: 'UNIA1B2C3',
            hasMultipleCodes: false,
            bankOccurredAt: new Date(clock.now()).toISOString(),
            signatureVerifiedAt: new Date(clock.now()).toISOString(),
            rawPayload: createSampleSePayPayload({ accountNumber: '9999999999' })
        };

        const result = await matchPaymentTransaction(event, repo, clock.now());

        assert.strictEqual(result.status, 'RECONCILIATION_REQUIRED');
        assert.strictEqual(result.reason, 'WRONG_RECIPIENT');

        console.log('  [PASS] TC-09: Chuyển khoản vào tài khoản lạ chuyển đối soát WRONG_RECIPIENT.');
        passed++;
    } catch (err) {
        console.error('  [FAIL] TC-09:', (err as Error).message);
        failed++;
    }

    // TC-10: Nội dung chứa từ 2 mã đơn khác nhau -> Chuyển đối soát MULTIPLE_PAYMENT_CODES
    try {
        const repo = new FakePaymentRepository();
        const event: NormalizedWebhookEvent = {
            provider: 'SEPAY',
            providerTxnId: 'txn_multi_code_1',
            amountVnd: 100000,
            recipientAccountNumber: TEST_ACCOUNT_NUMBER,
            direction: 'IN',
            content: 'UNIA1B2C3 va UNID4E5F6',
            paymentCode: null,
            hasMultipleCodes: true,
            bankOccurredAt: new Date(clock.now()).toISOString(),
            signatureVerifiedAt: new Date(clock.now()).toISOString(),
            rawPayload: createSampleSePayPayload()
        };

        const result = await matchPaymentTransaction(event, repo, clock.now());

        assert.strictEqual(result.status, 'RECONCILIATION_REQUIRED');
        assert.strictEqual(result.reason, 'MULTIPLE_PAYMENT_CODES');

        console.log('  [PASS] TC-10: Nội dung chứa 2 mã thanh toán chuyển đối soát MULTIPLE_PAYMENT_CODES.');
        passed++;
    } catch (err) {
        console.error('  [FAIL] TC-10:', (err as Error).message);
        failed++;
    }

    // TC-11: Mã thanh toán không tồn tại trong hệ thống -> Chuyển đối soát ORDER_NOT_FOUND
    try {
        const repo = new FakePaymentRepository();
        const event: NormalizedWebhookEvent = {
            provider: 'SEPAY',
            providerTxnId: 'txn_not_found_1',
            amountVnd: 50000,
            recipientAccountNumber: TEST_ACCOUNT_NUMBER,
            direction: 'IN',
            content: 'UNINOTFOUND',
            paymentCode: 'UNINOTFOUND',
            hasMultipleCodes: false,
            bankOccurredAt: new Date(clock.now()).toISOString(),
            signatureVerifiedAt: new Date(clock.now()).toISOString(),
            rawPayload: createSampleSePayPayload()
        };

        const result = await matchPaymentTransaction(event, repo, clock.now());

        assert.strictEqual(result.status, 'RECONCILIATION_REQUIRED');
        assert.strictEqual(result.reason, 'ORDER_NOT_FOUND');

        console.log('  [PASS] TC-11: Mã đơn không tồn tại chuyển đối soát ORDER_NOT_FOUND.');
        passed++;
    } catch (err) {
        console.error('  [FAIL] TC-11:', (err as Error).message);
        failed++;
    }

    // TC-12: Chuyển thiếu tiền (Giá 50.000đ nhưng chỉ chuyển 40.000đ)
    // -> Đối soát AMOUNT_MISMATCH và đổi đơn sang REVIEW_REQUIRED
    try {
        const repo = new FakePaymentRepository();
        const order = createSampleOrder({ amountVnd: 50000 });
        repo.seedOrder(order);

        const event: NormalizedWebhookEvent = {
            provider: 'SEPAY',
            providerTxnId: 'txn_underpaid_1',
            amountVnd: 40000, // Thiếu 10.000đ
            recipientAccountNumber: TEST_ACCOUNT_NUMBER,
            direction: 'IN',
            content: `Thanh toan ${order.paymentCode}`,
            paymentCode: order.paymentCode,
            hasMultipleCodes: false,
            bankOccurredAt: new Date(clock.now()).toISOString(),
            signatureVerifiedAt: new Date(clock.now()).toISOString(),
            rawPayload: createSampleSePayPayload({ transferAmount: 40000 })
        };

        const result = await matchPaymentTransaction(event, repo, clock.now());

        assert.strictEqual(result.status, 'RECONCILIATION_REQUIRED');
        assert.strictEqual(result.reason, 'AMOUNT_MISMATCH');
        assert.strictEqual(result.writeSet?.updateOrder?.status, 'REVIEW_REQUIRED', 'Đơn phải đổi sang REVIEW_REQUIRED');

        console.log('  [PASS] TC-12: Chuyển thiếu tiền không cấp quyền, chuyển đơn sang REVIEW_REQUIRED.');
        passed++;
    } catch (err) {
        console.error('  [FAIL] TC-12:', (err as Error).message);
        failed++;
    }

    // TC-13: Chuyển thừa tiền (Giá 50.000đ nhưng chuyển 60.000đ) -> AMOUNT_MISMATCH
    try {
        const repo = new FakePaymentRepository();
        const order = createSampleOrder({ amountVnd: 50000 });
        repo.seedOrder(order);

        const event: NormalizedWebhookEvent = {
            provider: 'SEPAY',
            providerTxnId: 'txn_overpaid_1',
            amountVnd: 60000, // Thừa 10.000đ
            recipientAccountNumber: TEST_ACCOUNT_NUMBER,
            direction: 'IN',
            content: order.paymentCode,
            paymentCode: order.paymentCode,
            hasMultipleCodes: false,
            bankOccurredAt: new Date(clock.now()).toISOString(),
            signatureVerifiedAt: new Date(clock.now()).toISOString(),
            rawPayload: createSampleSePayPayload({ transferAmount: 60000 })
        };

        const result = await matchPaymentTransaction(event, repo, clock.now());

        assert.strictEqual(result.status, 'RECONCILIATION_REQUIRED');
        assert.strictEqual(result.reason, 'AMOUNT_MISMATCH');

        console.log('  [PASS] TC-13: Chuyển thừa tiền chuyển sang đối soát AMOUNT_MISMATCH.');
        passed++;
    } catch (err) {
        console.error('  [FAIL] TC-13:', (err as Error).message);
        failed++;
    }

    // TC-14: Đơn hàng đã ở trạng thái PAID từ trước -> Khách chuyển lần 2 báo ORDER_ALREADY_PAID
    try {
        const repo = new FakePaymentRepository();
        const paidOrder = createSampleOrder({ status: 'PAID' });
        repo.seedOrder(paidOrder);

        const event: NormalizedWebhookEvent = {
            provider: 'SEPAY',
            providerTxnId: 'txn_second_pay_1',
            amountVnd: 50000,
            recipientAccountNumber: TEST_ACCOUNT_NUMBER,
            direction: 'IN',
            content: paidOrder.paymentCode,
            paymentCode: paidOrder.paymentCode,
            hasMultipleCodes: false,
            bankOccurredAt: new Date(clock.now()).toISOString(),
            signatureVerifiedAt: new Date(clock.now()).toISOString(),
            rawPayload: createSampleSePayPayload()
        };

        const result = await matchPaymentTransaction(event, repo, clock.now());

        assert.strictEqual(result.status, 'RECONCILIATION_REQUIRED');
        assert.strictEqual(result.reason, 'ORDER_ALREADY_PAID');

        console.log('  [PASS] TC-14: Giao dịch chuyển vào đơn đã PAID chuyển đối soát hoàn tiền.');
        passed++;
    } catch (err) {
        console.error('  [FAIL] TC-14:', (err as Error).message);
        failed++;
    }

    return { passed, failed };
}

// ============================================================================
// BỘ KIỂM THỬ P04: TÍNH BẤT BIẾN & CHỐNG PHÁT LẠI (IDEMPOTENCY TEST SUITE)
// ============================================================================

export async function runP04Tests(): Promise<{ passed: number; failed: number }> {
    console.log('\n--- [TEST SUITE P04] TÍNH BẤT BIẾN & IDEMPOTENCY ---');
    let passed = 0;
    let failed = 0;

    const clock = new FakeClock();

    // TC-15: Gửi lặp lại cùng một Webhook (trùng txnKey) -> Trả về DUPLICATE an toàn
    try {
        const repo = new FakePaymentRepository();
        const order = createSampleOrder();
        repo.seedOrder(order);

        const event: NormalizedWebhookEvent = {
            provider: 'SEPAY',
            providerTxnId: 'txn_repeat_999',
            amountVnd: 50000,
            recipientAccountNumber: TEST_ACCOUNT_NUMBER,
            direction: 'IN',
            content: order.paymentCode,
            paymentCode: order.paymentCode,
            hasMultipleCodes: false,
            bankOccurredAt: new Date(clock.now()).toISOString(),
            signatureVerifiedAt: new Date(clock.now()).toISOString(),
            rawPayload: createSampleSePayPayload({ id: 999 })
        };

        // Lần 1: Khớp thành công
        const res1 = await matchPaymentTransaction(event, repo, clock.now());
        assert.strictEqual(res1.status, 'MATCHED');
        assert.strictEqual(res1.writeSet?.updateOrder?.status, 'PAID');
        assert.strictEqual(res1.writeSet?.createEntitlement?.uid, order.buyerId);

        // Giả lập A07 đã commit payment vào repo
        if (res1.writeSet?.createPayment) {
            repo.seedPayment(res1.writeSet.createPayment);
        }

        // Lần 2: Webhook SePay retry hoặc gửi đúp chính txnKey này
        const res2 = await matchPaymentTransaction(event, repo, clock.now());
        assert.strictEqual(res2.status, 'DUPLICATE', 'Lần 2 phải nhận diện là DUPLICATE');
        assert.strictEqual(res2.httpStatus, 200, 'Vẫn phải trả 200 OK để SePay ngừng retry');
        assert.strictEqual(res2.writeSet, undefined, 'Không được phát sinh Write Set cấp quyền lần 2');

        console.log('  [PASS] TC-15: Nhận diện giao dịch trùng lặp chuẩn xác (Idempotent 200 OK).');
        passed++;
    } catch (err) {
        console.error('  [FAIL] TC-15:', (err as Error).message);
        failed++;
    }

    return { passed, failed };
}

// ============================================================================
// BỘ KIỂM THỬ P05: HẠN ĐƠN HÀNG & THỜI GIAN NGÂN HÀNG (EXPIRY TEST SUITE)
// ============================================================================

export async function runP05Tests(): Promise<{ passed: number; failed: number }> {
    console.log('\n--- [TEST SUITE P05] HẠN ĐƠN HÀNG & THỜI ĐIỂM NGÂN HÀNG ---');
    let passed = 0;
    let failed = 0;

    const clock = new FakeClock();

    // TC-16: Giao dịch ngân hàng diễn ra TRONG hạn (phút thứ 10/15) -> Khớp MATCHED
    try {
        const repo = new FakePaymentRepository();
        const order = createSampleOrder(); // Hết hạn lúc 10:15
        repo.seedOrder(order);

        // Ngân hàng ghi nhận lúc 10:10 (vẫn còn 5 phút)
        const bankTime = new Date(1794823800000).toISOString(); // 10:10:00

        const event: NormalizedWebhookEvent = {
            provider: 'SEPAY',
            providerTxnId: 'txn_valid_time_1',
            amountVnd: 50000,
            recipientAccountNumber: TEST_ACCOUNT_NUMBER,
            direction: 'IN',
            content: order.paymentCode,
            paymentCode: order.paymentCode,
            hasMultipleCodes: false,
            bankOccurredAt: bankTime,
            signatureVerifiedAt: new Date(clock.now()).toISOString(),
            rawPayload: createSampleSePayPayload({ transactionDate: '2026-11-17 10:10:00' })
        };

        const result = await matchPaymentTransaction(event, repo, clock.now());

        assert.strictEqual(result.status, 'MATCHED');
        assert.strictEqual(result.writeSet?.updateOrder?.status, 'PAID');
        assert.strictEqual(result.writeSet?.createEntitlement?.docId, order.docId);

        console.log('  [PASS] TC-16: Giao dịch diễn ra trong hạn được kích hoạt đơn PAID và cấp quyền.');
        passed++;
    } catch (err) {
        console.error('  [FAIL] TC-16:', (err as Error).message);
        failed++;
    }

    // TC-17: Giao dịch ngân hàng diễn ra SAU khi đơn hết hạn (10:16 > 10:15) -> ORDER_EXPIRED
    try {
        const repo = new FakePaymentRepository();
        const order = createSampleOrder(); // Hết hạn lúc 10:15
        repo.seedOrder(order);

        // Ngân hàng ghi nhận lúc 10:16 (quá hạn 1 phút)
        const lateBankTime = new Date(1794824160000).toISOString(); // 10:16:00

        const event: NormalizedWebhookEvent = {
            provider: 'SEPAY',
            providerTxnId: 'txn_late_time_1',
            amountVnd: 50000,
            recipientAccountNumber: TEST_ACCOUNT_NUMBER,
            direction: 'IN',
            content: order.paymentCode,
            paymentCode: order.paymentCode,
            hasMultipleCodes: false,
            bankOccurredAt: lateBankTime,
            signatureVerifiedAt: new Date(clock.now()).toISOString(),
            rawPayload: createSampleSePayPayload({ transactionDate: '2026-11-17 10:16:00' })
        };

        const result = await matchPaymentTransaction(event, repo, clock.now());

        assert.strictEqual(result.status, 'RECONCILIATION_REQUIRED');
        assert.strictEqual(result.reason, 'ORDER_EXPIRED');
        assert.strictEqual(result.writeSet?.updateOrder, undefined, 'Không được đổi đơn hết hạn sang PAID');

        console.log('  [PASS] TC-17: Giao dịch ngân hàng quá hạn 15 phút chuyển sang đối soát bảo thủ.');
        passed++;
    } catch (err) {
        console.error('  [FAIL] TC-17:', (err as Error).message);
        failed++;
    }

    // TC-18: Đơn hàng trong hệ thống đã đánh dấu EXPIRED -> ORDER_EXPIRED
    try {
        const repo = new FakePaymentRepository();
        const expiredOrder = createSampleOrder({ status: 'EXPIRED' });
        repo.seedOrder(expiredOrder);

        const event: NormalizedWebhookEvent = {
            provider: 'SEPAY',
            providerTxnId: 'txn_expired_status_1',
            amountVnd: 50000,
            recipientAccountNumber: TEST_ACCOUNT_NUMBER,
            direction: 'IN',
            content: expiredOrder.paymentCode,
            paymentCode: expiredOrder.paymentCode,
            hasMultipleCodes: false,
            bankOccurredAt: new Date(clock.now()).toISOString(),
            signatureVerifiedAt: new Date(clock.now()).toISOString(),
            rawPayload: createSampleSePayPayload()
        };

        const result = await matchPaymentTransaction(event, repo, clock.now());

        assert.strictEqual(result.status, 'RECONCILIATION_REQUIRED');
        assert.strictEqual(result.reason, 'ORDER_EXPIRED');

        console.log('  [PASS] TC-18: Đơn hàng ở trạng thái EXPIRED từ chối khớp tự động.');
        passed++;
    } catch (err) {
        console.error('  [FAIL] TC-18:', (err as Error).message);
        failed++;
    }

    return { passed, failed };
}

// ============================================================================
// HÀM CHẠY TOÀN BỘ TEST SUITES (TEST RUNNER)
// ============================================================================

export async function runAllWebhookTests(): Promise<void> {
    console.log('================================================================');
    console.log('UNISHARE TEST RUNNER - ISSUE #B03 (WEBHOOK VERIFY & MATCH LOGIC)');
    console.log('================================================================');

    const p02 = await runP02Tests();
    const p03 = await runP03Tests();
    const p04 = await runP04Tests();
    const p05 = await runP05Tests();

    const totalPassed = p02.passed + p03.passed + p04.passed + p05.passed;
    const totalFailed = p02.failed + p03.failed + p04.failed + p05.failed;

    console.log('\n================================================================');
    console.log(`TỔNG KẾT KIỂM THỬ: ${totalPassed} PASSED / ${totalFailed} FAILED`);
    console.log('================================================================');

    if (totalFailed > 0) {
        throw new Error(`Có ${totalFailed} ca kiểm thử thất bại!`);
    }
}

