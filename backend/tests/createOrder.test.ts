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
