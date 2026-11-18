import * as crypto from 'crypto';
import { OrderPolicy, type OrderStatus } from './orderPolicy';

export type { OrderStatus };

// ============================================================================
// ĐẶC TẢ KIỂU DỮ LIỆU VÀ GIAO DIỆN HỢP ĐỒNG (DOMAIN INTERFACES & CONTRACTS)
// Issue: [B02] Xây dựng nghiệp vụ tạo đơn thanh toán
// ============================================================================

/**
 * 1. Dữ liệu từ App Android gửi lên khi gọi POST /v1/orders
 * Bắt buộc phải có buyerId (đã xác thực qua Firebase Token), docId và idempotencyKey.
 */
export interface CreateOrderRequest {
    buyerId: string;
    docId: string;
    idempotencyKey: string; // Khóa chống bấm đúp từ client
}

/**
 * 2. Dữ liệu Tài liệu (Document) nằm trên Server
 * B02 TUYỆT ĐỐI KHÔNG tin giá từ client gửi lên, giá phải được chụp từ snapshot này.
 */
export interface DocumentSnapshot {
    docId: string;
    sellerId: string;
    priceVnd: number;       // Bắt buộc là số nguyên dương (> 0)
    activeVersionId: string;
    status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
    title: string;
}

/**
 * 3. Trạng thái của đơn hàng (được import và re-export từ orderPolicy.ts)
 */

/**
 * 4. Cấu trúc Đơn hàng (Order DTO) trả về khi tạo hoặc tái sử dụng đơn thành công
 * Chứa đầy đủ thông tin để lưu trữ, phục vụ đối soát và cấp quyền đọc tài liệu.
 */
export interface OrderDTO {
    orderId: string;
    buyerId: string;        // ID người mua (UID Firebase Auth, bắt buộc cho B03 cấp entitlement & kiểm quyền)
    sellerId: string;       // ID người bán (UID Firebase Auth, bắt buộc cho B06 doanh thu)
    docId: string;          // ID tài liệu
    versionId: string;      // ID phiên bản tài liệu bán (bất biến, bắt buộc cho A06 tải PDF)
    documentTitle: string;
    amountVnd: number;      // Giá chụp từ snapshot server (VND)
    currency: 'VND';
    status: OrderStatus;
    paymentCode: string;    // Mã tham chiếu chuyển khoản (Ví dụ: UNIA1B2C3)
    createdAt: string;      // Thời điểm tạo theo chuẩn UTC ISO 8601
    expiresAt: string;      // Thời điểm hết hạn theo chuẩn UTC ISO 8601 (TTL 15 phút)
}

/**
 * 5. Danh sách các mã lỗi nghiệp vụ chuẩn hóa theo API_CONTRACT.md
 */
export type OrderErrorCode =
    | 'ALREADY_OWNED'          // Khách đã mua tài liệu này rồi
    | 'SELF_PURCHASE'           // Tác giả tự mua tài liệu của chính mình
    | 'IDEMPOTENCY_CONFLICT'    // Dùng lại Idempotency Key nhưng với docId khác
    | 'ORDER_UNDER_REVIEW'      // Đơn hàng đang trong trạng thái đối soát thủ công
    | 'DOCUMENT_NOT_FOUND'      // Không tìm thấy tài liệu trên hệ thống
    | 'INVALID_DOCUMENT_STATUS' // Tài liệu chưa PUBLISHED (DRAFT hoặc ARCHIVED)
    | 'INVALID_PRICE'           // Giá tài liệu <= 0 hoặc không phải số nguyên
    | 'INVALID_REQUEST';        // Dữ liệu đầu vào bị thiếu hoặc rỗng

/**
 * 6. Kết quả trả về của hàm xử lý nghiệp vụ tạo đơn (processOrderCreation)
 */
export interface OrderResult {
    statusCode: 201 | 200 | 400 | 404 | 409;
    // 201: Đơn mới được tạo thành công
    // 200: Đơn cũ được tái sử dụng (Idempotency hoặc đơn PENDING còn hạn)
    // 400: Dữ liệu không hợp lệ (sai giá, thiếu trường)
    // 404: Không tìm thấy tài liệu
    // 409: Xung đột nghiệp vụ (đã sở hữu, tự mua, xung đột key)
    order?: OrderDTO;
    errorCode?: OrderErrorCode;
    message?: string;
}

/**
 * 7. Cổng giao tiếp dữ liệu (Port Interface) theo kiến trúc Ports & Adapters
 * Hàm nghiệp vụ processOrderCreation chỉ phụ thuộc vào Interface này,
 * không gọi trực tiếp Firebase Admin SDK hay cơ sở dữ liệu bên ngoài.
 */
export interface IOrderRepository {
    /** Tìm tài liệu theo ID để chụp giá và thông tin thật từ server */
    getDocument(docId: string): Promise<DocumentSnapshot | null>;

    /** Kiểm tra xem khách đã có quyền sở hữu tài liệu này chưa */
    hasEntitlement(buyerId: string, docId: string): Promise<boolean>;

    /** Kiểm tra xem Idempotency Key này đã từng gắn với đơn hàng nào chưa */
    getOrderByKey(buyerId: string, idempotencyKey: string): Promise<OrderDTO | null>;

    /** Lấy đơn hàng PENDING còn hiệu lực hoặc đơn REVIEW_REQUIRED hiện tại cho cặp (buyerId, docId) */
    getActiveOrderForDoc(buyerId: string, docId: string): Promise<OrderDTO | null>;

    /** 
     * Lưu đơn hàng mới vào kho lưu trữ dữ liệu một cách nguyên tử (Atomic Check-and-Save).
     * Adapter A07 (Firestore) thực thi hàm này trong 1 Firestore Transaction duy nhất:
     * - Khóa và lưu idempotencyKey vào orderRequests
     * - Khóa và cập nhật activePurchases cho cặp (buyerId, docId)
     * - Lưu paymentCode vào paymentCodes
     * - Lưu order vào orders
     * Nếu xảy ra xung đột đồng thời (Race Condition), repository trả về đơn đã được tạo
     * hoặc ném lỗi xung đột để bảo toàn tính nhất quán ACID.
     */
    saveOrder(order: OrderDTO, buyerId: string, idempotencyKey: string): Promise<OrderDTO | void>;
}

/**
 * Hàm nghiệp vụ chính xử lý tạo đơn thanh toán (Pure Business Logic).
 * 
 * Quy trình thực hiện:
 * 1. Kiểm tra tính hợp lệ của dữ liệu đầu vào (Input Validation).
 * 2. Kiểm tra Idempotency Key (chống bấm đúp checkout hoặc retry mạng).
 * 3. Lấy thông tin tài liệu từ Server (Document Snapshot) và chụp giá thật.
 * 4. Kiểm tra Tự mua (Self-purchase: buyerId == sellerId).
 * 5. Kiểm tra Đã sở hữu (Already Owned: hasEntitlement == true).
 * 6. Kiểm tra đơn hàng PENDING còn hiệu lực của cặp (buyerId, docId).
 * 7. Sinh mã thanh toán duy nhất, tính hạn TTL 15 phút và lưu đơn hàng mới (201 Created).
 * 
 * @param request Yêu cầu tạo đơn từ client
 * @param repository Cổng lưu trữ dữ liệu (IOrderRepository)
 * @param currentTimeMs Thời điểm xử lý (mặc định Date.now(), cho phép inject trong unit test)
 */
export async function processOrderCreation(
    request: CreateOrderRequest,
    repository: IOrderRepository,
    currentTimeMs: number = Date.now()
): Promise<OrderResult> {
    // ------------------------------------------------------------------------
    // BƯỚC 1: Kiểm tra tính hợp lệ của dữ liệu đầu vào
    // ------------------------------------------------------------------------
    if (
        !request ||
        !request.buyerId?.trim() ||
        !request.docId?.trim() ||
        !request.idempotencyKey?.trim()
    ) {
        return {
            statusCode: 400,
            errorCode: 'INVALID_REQUEST',
            message: 'Thiếu thông tin bắt buộc: buyerId, docId hoặc idempotencyKey không được để trống.'
        };
    }

    const buyerId = request.buyerId.trim();
    const docId = request.docId.trim();
    const idempotencyKey = request.idempotencyKey.trim();

    // ------------------------------------------------------------------------
    // BƯỚC 2: Kiểm tra Idempotency Key (Chống bấm đúp)
    // ------------------------------------------------------------------------
    const existingOrderByKey = await repository.getOrderByKey(buyerId, idempotencyKey);
    if (existingOrderByKey) {
        // Nếu cùng key nhưng yêu cầu cho tài liệu khác -> Báo lỗi xung đột
        if (existingOrderByKey.docId !== docId) {
            return {
                statusCode: 409,
                errorCode: 'IDEMPOTENCY_CONFLICT',
                message: 'Khóa Idempotency này đã được sử dụng cho một tài liệu khác.'
            };
        }

        // Cùng key và cùng docId -> Trả lại đơn cũ đã tạo (Idempotent 200 OK)
        return {
            statusCode: 200,
            order: existingOrderByKey,
            message: 'Tái sử dụng đơn hàng thành công từ khóa Idempotency.'
        };
    }

    // ------------------------------------------------------------------------
    // BƯỚC 3: Lấy thông tin tài liệu từ Server (Chụp giá và phiên bản)
    // ------------------------------------------------------------------------
    const document = await repository.getDocument(docId);
    if (!document) {
        return {
            statusCode: 404,
            errorCode: 'DOCUMENT_NOT_FOUND',
            message: `Không tìm thấy tài liệu có mã: ${docId}`
        };
    }

    // Kiểm tra trạng thái xuất bản: Chỉ tài liệu PUBLISHED mới được phép mua
    if (document.status !== 'PUBLISHED') {
        return {
            statusCode: 400,
            errorCode: 'INVALID_DOCUMENT_STATUS',
            message: `Tài liệu đang ở trạng thái ${document.status}, không thể mua bán.`
        };
    }

    // Chụp giá server và kiểm tra tính hợp lệ: phải là số nguyên dương
    if (
        typeof document.priceVnd !== 'number' ||
        !Number.isInteger(document.priceVnd) ||
        document.priceVnd <= 0
    ) {
        return {
            statusCode: 400,
            errorCode: 'INVALID_PRICE',
            message: 'Giá tài liệu trên server không hợp lệ (phải là số nguyên dương VND).'
        };
    }

    // ------------------------------------------------------------------------
    // BƯỚC 4: Kiểm tra hành vi Tự mua (Self-purchase)
    // ------------------------------------------------------------------------
    if (document.sellerId === buyerId) {
        return {
            statusCode: 409,
            errorCode: 'SELF_PURCHASE',
            message: 'Tác giả không thể tự mua tài liệu do chính mình đăng bán.'
        };
    }

    // ------------------------------------------------------------------------
    // BƯỚC 5: Kiểm tra quyền sở hữu hiện tại (Already Owned)
    // ------------------------------------------------------------------------
    const isAlreadyOwned = await repository.hasEntitlement(buyerId, docId);
    if (isAlreadyOwned) {
        return {
            statusCode: 409,
            errorCode: 'ALREADY_OWNED',
            message: 'Bạn đã mua và sở hữu tài liệu này từ trước.'
        };
    }

    // ------------------------------------------------------------------------
    // BƯỚC 6: Kiểm tra đơn PENDING còn hiệu lực cho cặp (buyerId, docId)
    // ------------------------------------------------------------------------
    const activeOrder = await repository.getActiveOrderForDoc(buyerId, docId);
    if (activeOrder) {
        // Kiểm tra xem đơn cũ có đang cần đối soát thủ công không
        if (activeOrder.status === 'REVIEW_REQUIRED') {
            return {
                statusCode: 409,
                errorCode: 'ORDER_UNDER_REVIEW',
                message: 'Đơn hàng trước đó của bạn đang trong quá trình đối soát, vui lòng chờ xử lý.'
            };
        }

        // Nếu đơn PENDING cũ vẫn còn hạn -> Tái sử dụng đơn này, không tạo thêm đơn mới
        if (OrderPolicy.isOrderActive(activeOrder.status, activeOrder.expiresAt, currentTimeMs)) {
            return {
                statusCode: 200,
                order: activeOrder,
                message: 'Bạn đang có một đơn thanh toán còn hiệu lực cho tài liệu này.'
            };
        }
    }

    // ------------------------------------------------------------------------
    // BƯỚC 7: Sinh đơn hàng mới (201 Created)
    // ------------------------------------------------------------------------
    const orderId = `ord_${crypto.randomBytes(8).toString('hex')}`;
    const paymentCode = OrderPolicy.generatePaymentCode();
    const createdAt = new Date(currentTimeMs).toISOString();
    const expiresAt = OrderPolicy.calculateExpiryTime(currentTimeMs);

    const newOrder: OrderDTO = {
        orderId,
        buyerId,
        sellerId: document.sellerId,
        docId: document.docId,
        versionId: document.activeVersionId,
        documentTitle: document.title,
        amountVnd: document.priceVnd, // Chụp trực tiếp từ server
        currency: 'VND',
        status: 'PENDING',
        paymentCode,
        createdAt,
        expiresAt
    };

    // Lưu vào kho dữ liệu một cách nguyên tử (Atomic Check-and-Save)
    try {
        const savedOrder = (await repository.saveOrder(newOrder, buyerId, idempotencyKey)) || newOrder;
        const isReused = savedOrder.orderId !== newOrder.orderId;

        return {
            statusCode: isReused ? 200 : 201,
            order: savedOrder,
            message: isReused
                ? 'Tái sử dụng đơn hàng thành công từ khóa Idempotency hoặc đơn đang có hiệu lực.'
                : 'Tạo đơn hàng thanh toán mới thành công.'
        };
    } catch (error: any) {
        if (error?.errorCode === 'IDEMPOTENCY_CONFLICT') {
            return {
                statusCode: 409,
                errorCode: 'IDEMPOTENCY_CONFLICT',
                message: error.message || 'Khóa Idempotency này đã được sử dụng cho một tài liệu khác.'
            };
        }
        if (error?.errorCode === 'ORDER_UNDER_REVIEW') {
            return {
                statusCode: 409,
                errorCode: 'ORDER_UNDER_REVIEW',
                message: error.message || 'Đơn hàng trước đó của bạn đang trong quá trình đối soát, vui lòng chờ xử lý.'
            };
        }
        throw error;
    }
}