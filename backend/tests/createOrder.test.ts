import {
    CreateOrderRequest,
    DocumentSnapshot,
    IOrderRepository,
    OrderDTO,
    OrderResult,
    OrderStatus,
    processOrderCreation
} from '../src/orders/createOrder';
import { OrderPolicy } from '../src/orders/orderPolicy';

// ============================================================================
// HẠ TẦNG GIẢ LẬP PHỤC VỤ KIỂM THỬ (FAKE TEST INFRASTRUCTURE)
// Issue: [B02] Xây dựng nghiệp vụ tạo đơn thanh toán
// ============================================================================

/**
 * 1. FakeClock: Bộ đếm thời gian giả lập
 * Cho phép can thiệp và dịch chuyển mốc thời gian (tua thời gian tới hoặc lùi)
 * giúp kiểm tra các kịch bản hết hạn TTL 15 phút mà không cần đợi thời gian thực.
 */
export class FakeClock {
    private currentTimeMs: number;

    constructor(initialTimeMs: number = Date.now()) {
        this.currentTimeMs = initialTimeMs;
    }

    /** Lấy thời điểm hiện tại của đồng hồ giả lập */
    now(): number {
        return this.currentTimeMs;
    }

    /** Tua nhanh thời gian thêm số phút quy định */
    advanceMinutes(minutes: number): void {
        this.currentTimeMs += minutes * 60 * 1000;
    }

    /** Thiết lập mốc thời gian tuyệt đối */
    setTime(timeMs: number): void {
        this.currentTimeMs = timeMs;
    }
}

/**
 * 2. FakeOrderRepository: Kho lưu trữ in-memory giả lập
 * Triển khai đầy đủ Port Interface IOrderRepository hoàn toàn trong bộ nhớ RAM.
 * Không kết nối Firebase, không gọi mạng.
 */
export class FakeOrderRepository implements IOrderRepository {
    // Lưu danh sách tài liệu mẫu trên server: key = docId
    private documents: Map<string, DocumentSnapshot> = new Map();

    // Lưu quyền sở hữu của Buyer: key = `${buyerId}:${docId}`
    private entitlements: Set<string> = new Set();

    // Lưu danh sách đơn hàng theo orderId: key = orderId
    private ordersById: Map<string, OrderDTO> = new Map();

    // Lưu vết đơn theo Idempotency Key: key = `${buyerId}:${idempotencyKey}`
    private ordersByIdempotencyKey: Map<string, OrderDTO> = new Map();

    // Lưu đơn hàng active hiện tại của cặp (buyerId, docId): key = `${buyerId}:${docId}` -> orderId
    private activeOrdersByPair: Map<string, string> = new Map();

    private clock: FakeClock;

    constructor(clock: FakeClock) {
        this.clock = clock;
    }

    // --- Các hàm tiện ích để nạp dữ liệu mẫu (Test Fixture Helpers) ---

    /** Nạp tài liệu mẫu vào kho */
    seedDocument(doc: DocumentSnapshot): void {
        this.documents.set(doc.docId, doc);
    }

    /** Cấp quyền sở hữu mẫu cho Buyer đối với tài liệu */
    seedEntitlement(buyerId: string, docId: string): void {
        this.entitlements.add(`${buyerId}:${docId}`);
    }

    /** Nạp đơn hàng mẫu sẵn có */
    seedOrder(order: OrderDTO, buyerId: string, idempotencyKey: string): void {
        this.ordersById.set(order.orderId, order);
        this.ordersByIdempotencyKey.set(`${buyerId}:${idempotencyKey}`, order);
        if (order.status === 'PENDING') {
            this.activeOrdersByPair.set(`${buyerId}:${order.docId}`, order.orderId);
        }
    }

    // --- Triển khai các phương thức của IOrderRepository ---

    async getDocument(docId: string): Promise<DocumentSnapshot | null> {
        return this.documents.get(docId) || null;
    }

    async hasEntitlement(buyerId: string, docId: string): Promise<boolean> {
        return this.entitlements.has(`${buyerId}:${docId}`);
    }

    async getOrderByKey(buyerId: string, idempotencyKey: string): Promise<OrderDTO | null> {
        return this.ordersByIdempotencyKey.get(`${buyerId}:${idempotencyKey}`) || null;
    }

    async getActiveOrderForDoc(buyerId: string, docId: string): Promise<OrderDTO | null> {
        const orderId = this.activeOrdersByPair.get(`${buyerId}:${docId}`);
        if (!orderId) return null;

        const order = this.ordersById.get(orderId);
        if (!order) return null;

        // Nếu đơn hàng đã hết hạn hoặc không còn PENDING, không còn là Active
        if (!OrderPolicy.isOrderActive(order.status, order.expiresAt, this.clock.now())) {
            return null;
        }

        return order;
    }

    async saveOrder(order: OrderDTO, buyerId: string, idempotencyKey: string): Promise<void> {
        this.ordersById.set(order.orderId, order);
        this.ordersByIdempotencyKey.set(`${buyerId}:${idempotencyKey}`, order);
        this.activeOrdersByPair.set(`${buyerId}:${order.docId}`, order.orderId);
    }
}

// ============================================================================
// BỘ KIỂM THỬ ĐƠN VỊ TOÀN DIỆN (UNIT TEST SUITE: P01 & P05)
// ============================================================================

import * as assert from 'assert';

/**
 * Hàm trợ giúp tạo tài liệu mẫu chuẩn
 */
function createSampleDocument(overrides?: Partial<DocumentSnapshot>): DocumentSnapshot {
    return {
        docId: 'doc-lap-trinh-android',
        sellerId: 'seller-nguyen-van-a',
        priceVnd: 50000,
        activeVersionId: 'ver-1.0.0',
        status: 'PUBLISHED',
        title: 'Giáo trình Lập trình Thiết bị Di động với Jetpack Compose',
        ...overrides
    };
}

/**
 * Bộ ca kiểm thử P01: Kiểm tra tính đúng đắn và các bất biến nghiệp vụ
 */
export async function runP01Tests(): Promise<{ passed: number; failed: number }> {
    let passed = 0;
    let failed = 0;

    console.log('\n--- BẮT ĐẦU CHẠY BỘ KIỂM THỬ P01: RÀNG BUỘC VÀ BẤT BIẾN NGHIỆP VỤ ---');

    // TC-01: Tạo đơn thành công với tài liệu hợp lệ
    try {
        const clock = new FakeClock(1700000000000);
        const repo = new FakeOrderRepository(clock);
        const doc = createSampleDocument();
        repo.seedDocument(doc);

        const request: CreateOrderRequest = {
            buyerId: 'buyer-tran-van-b',
            docId: doc.docId,
            idempotencyKey: 'key-req-001'
        };

        const result = await processOrderCreation(request, repo, clock.now());

        assert.strictEqual(result.statusCode, 201, 'Mã HTTP phải là 201 Created');
        assert.ok(result.order, 'Đơn hàng phải tồn tại');
        assert.strictEqual(result.order.docId, doc.docId);
        assert.strictEqual(result.order.documentTitle, doc.title);
        assert.strictEqual(result.order.amountVnd, 50000, 'Giá phải chụp đúng từ server (50.000 đ)');
        assert.strictEqual(result.order.currency, 'VND');
        assert.strictEqual(result.order.status, 'PENDING');
        assert.ok(OrderPolicy.isValidPaymentCode(result.order.paymentCode), 'Mã thanh toán phải đúng chuẩn UNIxxxxxx');

        // Kiểm tra TTL 15 phút: expiresAt - createdAt = 15 phút
        const createdMs = new Date(result.order.createdAt).getTime();
        const expiresMs = new Date(result.order.expiresAt).getTime();
        assert.strictEqual(expiresMs - createdMs, 15 * 60 * 1000, 'Hạn đơn phải cách đúng 15 phút');

        console.log('  [PASS] TC-01: Tạo đơn hợp lệ thành công (201 Created, TTL 15p, giá server).');
        passed++;
    } catch (err: any) {
        console.error('  [FAIL] TC-01:', err.message);
        failed++;
    }

    // TC-02: Bất biến giá server (Client không thể tự ép giá)
    try {
        const clock = new FakeClock();
        const repo = new FakeOrderRepository(clock);
        const doc = createSampleDocument({ priceVnd: 100000 });
        repo.seedDocument(doc);

        const request: CreateOrderRequest = {
            buyerId: 'buyer-002',
            docId: doc.docId,
            idempotencyKey: 'key-002'
        };

        const result = await processOrderCreation(request, repo, clock.now());

        assert.strictEqual(result.statusCode, 201);
        assert.strictEqual(result.order?.amountVnd, 100000, 'Giá phải luôn là 100.000 đ từ server');

        console.log('  [PASS] TC-02: Bất biến giá server bảo đảm toàn vẹn.');
        passed++;
    } catch (err: any) {
        console.error('  [FAIL] TC-02:', err.message);
        failed++;
    }

    // TC-03: Chặn tự mua tài liệu (buyerId === sellerId)
    try {
        const clock = new FakeClock();
        const repo = new FakeOrderRepository(clock);
        const doc = createSampleDocument({ sellerId: 'seller-same-id' });
        repo.seedDocument(doc);

        const request: CreateOrderRequest = {
            buyerId: 'seller-same-id', // Trùng với người bán
            docId: doc.docId,
            idempotencyKey: 'key-003'
        };

        const result = await processOrderCreation(request, repo, clock.now());

        assert.strictEqual(result.statusCode, 409);
        assert.strictEqual(result.errorCode, 'SELF_PURCHASE');

        console.log('  [PASS] TC-03: Chặn thành công tác giả tự mua tài liệu của mình (409 SELF_PURCHASE).');
        passed++;
    } catch (err: any) {
        console.error('  [FAIL] TC-03:', err.message);
        failed++;
    }

    // TC-04: Chặn mua tài liệu đã sở hữu (ALREADY_OWNED)
    try {
        const clock = new FakeClock();
        const repo = new FakeOrderRepository(clock);
        const doc = createSampleDocument();
        repo.seedDocument(doc);
        repo.seedEntitlement('buyer-vip', doc.docId); // Đã có quyền sở hữu

        const request: CreateOrderRequest = {
            buyerId: 'buyer-vip',
            docId: doc.docId,
            idempotencyKey: 'key-004'
        };

        const result = await processOrderCreation(request, repo, clock.now());

        assert.strictEqual(result.statusCode, 409);
        assert.strictEqual(result.errorCode, 'ALREADY_OWNED');

        console.log('  [PASS] TC-04: Chặn thành công mua tài liệu đã sở hữu (409 ALREADY_OWNED).');
        passed++;
    } catch (err: any) {
        console.error('  [FAIL] TC-04:', err.message);
        failed++;
    }

    // TC-05: Tài liệu không tồn tại
    try {
        const clock = new FakeClock();
        const repo = new FakeOrderRepository(clock);

        const request: CreateOrderRequest = {
            buyerId: 'buyer-005',
            docId: 'doc-khong-ton-tai',
            idempotencyKey: 'key-005'
        };

        const result = await processOrderCreation(request, repo, clock.now());

        assert.strictEqual(result.statusCode, 404);
        assert.strictEqual(result.errorCode, 'DOCUMENT_NOT_FOUND');

        console.log('  [PASS] TC-05: Xử lý chính xác tài liệu không tồn tại (404 DOCUMENT_NOT_FOUND).');
        passed++;
    } catch (err: any) {
        console.error('  [FAIL] TC-05:', err.message);
        failed++;
    }

    // TC-06: Chặn mua tài liệu DRAFT hoặc ARCHIVED
    try {
        const clock = new FakeClock();
        const repo = new FakeOrderRepository(clock);
        const docDraft = createSampleDocument({ docId: 'doc-draft', status: 'DRAFT' });
        const docArchived = createSampleDocument({ docId: 'doc-archived', status: 'ARCHIVED' });
        repo.seedDocument(docDraft);
        repo.seedDocument(docArchived);

        const resDraft = await processOrderCreation(
            { buyerId: 'buyer-006', docId: 'doc-draft', idempotencyKey: 'k-1' },
            repo,
            clock.now()
        );
        assert.strictEqual(resDraft.statusCode, 400);
        assert.strictEqual(resDraft.errorCode, 'INVALID_DOCUMENT_STATUS');

        const resArchived = await processOrderCreation(
            { buyerId: 'buyer-006', docId: 'doc-archived', idempotencyKey: 'k-2' },
            repo,
            clock.now()
        );
        assert.strictEqual(resArchived.statusCode, 400);
        assert.strictEqual(resArchived.errorCode, 'INVALID_DOCUMENT_STATUS');

        console.log('  [PASS] TC-06: Chặn tài liệu DRAFT và ARCHIVED (400 INVALID_DOCUMENT_STATUS).');
        passed++;
    } catch (err: any) {
        console.error('  [FAIL] TC-06:', err.message);
        failed++;
    }

    // TC-07: Chặn giá tài liệu không hợp lệ (<= 0 hoặc số thực)
    try {
        const clock = new FakeClock();
        const repo = new FakeOrderRepository(clock);
        const docZero = createSampleDocument({ docId: 'doc-zero', priceVnd: 0 });
        const docNegative = createSampleDocument({ docId: 'doc-neg', priceVnd: -10000 });
        const docFloat = createSampleDocument({ docId: 'doc-float', priceVnd: 25.5 });
        repo.seedDocument(docZero);
        repo.seedDocument(docNegative);
        repo.seedDocument(docFloat);

        const resZero = await processOrderCreation({ buyerId: 'b', docId: 'doc-zero', idempotencyKey: 'k' }, repo, clock.now());
        assert.strictEqual(resZero.statusCode, 400);
        assert.strictEqual(resZero.errorCode, 'INVALID_PRICE');

        const resNeg = await processOrderCreation({ buyerId: 'b', docId: 'doc-neg', idempotencyKey: 'k' }, repo, clock.now());
        assert.strictEqual(resNeg.statusCode, 400);
        assert.strictEqual(resNeg.errorCode, 'INVALID_PRICE');

        const resFloat = await processOrderCreation({ buyerId: 'b', docId: 'doc-float', idempotencyKey: 'k' }, repo, clock.now());
        assert.strictEqual(resFloat.statusCode, 400);
        assert.strictEqual(resFloat.errorCode, 'INVALID_PRICE');

        console.log('  [PASS] TC-07: Chặn giá <= 0 hoặc số không nguyên (400 INVALID_PRICE).');
        passed++;
    } catch (err: any) {
        console.error('  [FAIL] TC-07:', err.message);
        failed++;
    }

    // TC-08: Kiểm tra dữ liệu đầu vào rỗng
    try {
        const clock = new FakeClock();
        const repo = new FakeOrderRepository(clock);

        const resEmptyBuyer = await processOrderCreation({ buyerId: '', docId: 'doc-1', idempotencyKey: 'k' }, repo, clock.now());
        assert.strictEqual(resEmptyBuyer.statusCode, 400);
        assert.strictEqual(resEmptyBuyer.errorCode, 'INVALID_REQUEST');

        const resEmptyDoc = await processOrderCreation({ buyerId: 'b', docId: '  ', idempotencyKey: 'k' }, repo, clock.now());
        assert.strictEqual(resEmptyDoc.statusCode, 400);
        assert.strictEqual(resEmptyDoc.errorCode, 'INVALID_REQUEST');

        console.log('  [PASS] TC-08: Kiểm tra chặt chẽ dữ liệu đầu vào (400 INVALID_REQUEST).');
        passed++;
    } catch (err: any) {
        console.error('  [FAIL] TC-08:', err.message);
        failed++;
    }

    return { passed, failed };
}

/**
 * Bộ ca kiểm thử P05: Kiểm tra Idempotency, Concurrency và Hết hạn TTL
 */
export async function runP05Tests(): Promise<{ passed: number; failed: number }> {
    let passed = 0;
    let failed = 0;

    console.log('\n--- BẮT ĐẦU CHẠY BỘ KIỂM THỬ P05: IDEMPOTENCY, TTL VÀ TRẠNG THÁI ĐƠN ---');

    // TC-09: Idempotency Replay (Bấm đúp cùng key -> trả cùng đơn)
    try {
        const clock = new FakeClock();
        const repo = new FakeOrderRepository(clock);
        const doc = createSampleDocument();
        repo.seedDocument(doc);

        const request: CreateOrderRequest = {
            buyerId: 'buyer-double-click',
            docId: doc.docId,
            idempotencyKey: 'idempotency-key-same'
        };

        // Lần 1: Tạo mới thành công
        const res1 = await processOrderCreation(request, repo, clock.now());
        assert.strictEqual(res1.statusCode, 201);
        assert.ok(res1.order);

        // Lần 2: Bấm lại y hệt
        const res2 = await processOrderCreation(request, repo, clock.now());
        assert.strictEqual(res2.statusCode, 200, 'Lần 2 phải trả về 200 OK');
        assert.strictEqual(res2.order?.orderId, res1.order.orderId, 'orderId phải giống hệt đơn lần 1');
        assert.strictEqual(res2.order?.paymentCode, res1.order.paymentCode, 'Mã chuyển khoản phải giữ nguyên');

        console.log('  [PASS] TC-09: Idempotency replay khi bấm đúp thành công (200 OK cùng orderId).');
        passed++;
    } catch (err: any) {
        console.error('  [FAIL] TC-09:', err.message);
        failed++;
    }

    // TC-10: Idempotency Conflict (Cùng key nhưng gửi mua tài liệu khác)
    try {
        const clock = new FakeClock();
        const repo = new FakeOrderRepository(clock);
        const doc1 = createSampleDocument({ docId: 'doc-mon-a' });
        const doc2 = createSampleDocument({ docId: 'doc-mon-b' });
        repo.seedDocument(doc1);
        repo.seedDocument(doc2);

        // Lần 1: Tạo đơn cho doc-mon-a với key-duplicate
        await processOrderCreation({ buyerId: 'buyer-conflict', docId: 'doc-mon-a', idempotencyKey: 'key-duplicate' }, repo, clock.now());

        // Lần 2: Dùng lại key-duplicate nhưng gửi cho doc-mon-b
        const resConflict = await processOrderCreation({ buyerId: 'buyer-conflict', docId: 'doc-mon-b', idempotencyKey: 'key-duplicate' }, repo, clock.now());

        assert.strictEqual(resConflict.statusCode, 409);
        assert.strictEqual(resConflict.errorCode, 'IDEMPOTENCY_CONFLICT');

        console.log('  [PASS] TC-10: Phát hiện chính xác xung đột Idempotency (409 IDEMPOTENCY_CONFLICT).');
        passed++;
    } catch (err: any) {
        console.error('  [FAIL] TC-10:', err.message);
        failed++;
    }

    // TC-11: Tái sử dụng đơn PENDING còn hiệu lực (chưa hết 15 phút)
    try {
        const clock = new FakeClock();
        const repo = new FakeOrderRepository(clock);
        const doc = createSampleDocument();
        repo.seedDocument(doc);

        // Lần 1: Tạo đơn với key-1
        const res1 = await processOrderCreation({ buyerId: 'buyer-active', docId: doc.docId, idempotencyKey: 'key-1' }, repo, clock.now());
        assert.strictEqual(res1.statusCode, 201);

        // 5 phút sau: Khách lại bấm mua với key-2 mới toanh
        clock.advanceMinutes(5);
        const res2 = await processOrderCreation({ buyerId: 'buyer-active', docId: doc.docId, idempotencyKey: 'key-2' }, repo, clock.now());

        assert.strictEqual(res2.statusCode, 200, 'Phải trả về 200 OK đơn PENDING đang hoạt động');
        assert.strictEqual(res2.order?.orderId, res1.order?.orderId);

        console.log('  [PASS] TC-11: Khóa Active Order hoạt động chuẩn xác (tái sử dụng đơn PENDING còn hạn).');
        passed++;
    } catch (err: any) {
        console.error('  [FAIL] TC-11:', err.message);
        failed++;
    }

    // TC-12: Đơn PENDING cũ đã hết hạn (sau 15 phút) -> Cho phép tạo đơn mới
    try {
        const clock = new FakeClock();
        const repo = new FakeOrderRepository(clock);
        const doc = createSampleDocument();
        repo.seedDocument(doc);

        // Lần 1: Tạo đơn
        const res1 = await processOrderCreation({ buyerId: 'buyer-expired', docId: doc.docId, idempotencyKey: 'key-expired-1' }, repo, clock.now());
        assert.strictEqual(res1.statusCode, 201);

        // 16 phút sau: Đơn cũ đã quá 15 phút
        clock.advanceMinutes(16);

        // Lần 2: Khách bấm tạo đơn lại với key mới
        const res2 = await processOrderCreation({ buyerId: 'buyer-expired', docId: doc.docId, idempotencyKey: 'key-new-after-expire' }, repo, clock.now());

        assert.strictEqual(res2.statusCode, 201, 'Sau khi đơn cũ hết hạn, phải cho tạo đơn mới (201)');
        assert.notStrictEqual(res2.order?.orderId, res1.order?.orderId, 'Đơn mới phải có orderId khác đơn cũ');
        assert.notStrictEqual(res2.order?.paymentCode, res1.order?.paymentCode, 'Mã thanh toán mới phải khác mã cũ');

        console.log('  [PASS] TC-12: Đơn quá hạn 15 phút được giải phóng, tạo thành công đơn mới.');
        passed++;
    } catch (err: any) {
        console.error('  [FAIL] TC-12:', err.message);
        failed++;
    }

    // TC-13: Đơn hàng ở trạng thái REVIEW_REQUIRED -> Chặn không cho mua đè
    try {
        const clock = new FakeClock();
        const repo = new FakeOrderRepository(clock);
        const doc = createSampleDocument();
        repo.seedDocument(doc);

        // Nạp đơn REVIEW_REQUIRED sẵn có
        const reviewOrder: OrderDTO = {
            orderId: 'ord-review-123',
            docId: doc.docId,
            documentTitle: doc.title,
            amountVnd: doc.priceVnd,
            currency: 'VND',
            status: 'REVIEW_REQUIRED',
            paymentCode: 'UNIREV999',
            createdAt: new Date(clock.now()).toISOString(),
            expiresAt: OrderPolicy.calculateExpiryTime(clock.now())
        };
        repo.seedOrder(reviewOrder, 'buyer-review', 'key-review');

        const resReview = await processOrderCreation({ buyerId: 'buyer-review', docId: doc.docId, idempotencyKey: 'key-new' }, repo, clock.now());

        assert.strictEqual(resReview.statusCode, 409);
        assert.strictEqual(resReview.errorCode, 'ORDER_UNDER_REVIEW');

        console.log('  [PASS] TC-13: Đơn đang đối soát bị chặn đúng quy định (409 ORDER_UNDER_REVIEW).');
        passed++;
    } catch (err: any) {
        console.error('  [FAIL] TC-13:', err.message);
        failed++;
    }

    // TC-14: Kiểm tra tính đúng đắn của định dạng mã thanh toán
    try {
        // Sinh 50 mã và kiểm tra
        for (let i = 0; i < 50; i++) {
            const code = OrderPolicy.generatePaymentCode();
            assert.ok(OrderPolicy.isValidPaymentCode(code), `Mã ${code} phải hợp lệ`);
        }

        // Kiểm tra các trường hợp mã sai
        assert.strictEqual(OrderPolicy.isValidPaymentCode(''), false);
        assert.strictEqual(OrderPolicy.isValidPaymentCode('UNI123'), false, 'Thiếu độ dài');
        assert.strictEqual(OrderPolicy.isValidPaymentCode('uni123456'), false, 'Chữ thường');
        assert.strictEqual(OrderPolicy.isValidPaymentCode('UNIZZZZZZ'), false, 'Ký tự ngoài hex');
        assert.strictEqual(OrderPolicy.isValidPaymentCode('VND123456'), false, 'Sai tiền tố');

        console.log('  [PASS] TC-14: Kiểm tra định dạng Payment Code chuẩn xác.');
        passed++;
    } catch (err: any) {
        console.error('  [FAIL] TC-14:', err.message);
        failed++;
    }

    return { passed, failed };
}

/**
 * Runner thực thi tất cả các bài kiểm thử
 */
export async function runAllTests(): Promise<void> {
    console.log('================================================================');
    console.log('UNISHARE TEST RUNNER - ISSUE #B02 (ORDER CREATION LOGIC)');
    console.log('================================================================');

    const p01 = await runP01Tests();
    const p05 = await runP05Tests();

    const totalPassed = p01.passed + p05.passed;
    const totalFailed = p01.failed + p05.failed;

    console.log('\n================================================================');
    console.log(`KẾT QUẢ KIỂM THỬ: ${totalPassed} PASSED / ${totalFailed} FAILED`);
    console.log('================================================================');

    if (totalFailed > 0) {
        throw new Error(`Có ${totalFailed} bài kiểm thử thất bại!`);
    }
}

// Tự động chạy nếu file được gọi trực tiếp bằng Node/TS-Node
if (require.main === module) {
    runAllTests().catch((err) => {
        console.error('Lỗi khi chạy test suite:', err);
        process.exit(1);
    });
}

