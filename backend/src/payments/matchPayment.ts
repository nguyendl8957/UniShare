import { NormalizedWebhookEvent } from './verifyWebhook';

// ============================================================================
// ĐẶC TẢ HỢP ĐỒNG ĐỐI SOÁT VÀ GHI DỮ LIỆU (RECONCILIATION & WRITE CONTRACTS)
// Issue: [B03] Xác minh webhook và đối soát giao dịch
// ============================================================================

/**
 * 1. Trạng thái kết quả đối soát giao dịch
 * - MATCHED: Khớp hoàn toàn, đủ điều kiện kích hoạt đơn PAID và cấp quyền
 * - DUPLICATE: Giao dịch đã từng được xử lý trước đó (Idempotent)
 * - RECONCILIATION_REQUIRED: Có điểm bất thường, bắt buộc đưa vào danh sách đối soát thủ công
 */
export type PaymentMatchStatus = 'MATCHED' | 'DUPLICATE' | 'RECONCILIATION_REQUIRED';

/**
 * 2. Danh sách mã lý do đối soát rút gọn (Reconciliation Reasons)
 * Theo đặc tả trong DATABASE_SCHEMA.md và PAYMENT_POC.md §5
 */
export type ReconciliationReason =
    | 'DUPLICATE_TXN'           // Giao dịch bị gửi lặp lại
    | 'WRONG_DIRECTION'          // Giao dịch không phải chiều tiền vào ('IN')
    | 'WRONG_RECIPIENT'          // Chuyển nhầm vào số tài khoản lạ, không phải tài khoản UniShare
    | 'MISSING_PAYMENT_CODE'     // Nội dung chuyển khoản không có mã thanh toán chuẩn UNI...
    | 'MULTIPLE_PAYMENT_CODES'    // Nội dung chứa từ 2 mã đơn khác nhau (nghi vấn gian lận/gộp đơn)
    | 'ORDER_NOT_FOUND'          // Không tìm thấy đơn hàng tương ứng với mã thanh toán
    | 'ORDER_ALREADY_PAID'       // Đơn hàng này đã được thanh toán thành công trước đó (Giao dịch mới cho cùng đơn)
    | 'ORDER_EXPIRED'            // Giao dịch ngân hàng diễn ra sau khi đơn đã hết hạn (quá 15 phút)
    | 'AMOUNT_MISMATCH'          // Số tiền chuyển không khớp chính xác với giá đơn (thừa hoặc thiếu)
    | 'ORDER_UNDER_REVIEW'       // Đơn hàng đang trong trạng thái xem xét đối soát
    | 'ALREADY_ENTITLED';        // Người mua đã sở hữu tài liệu này từ trước

/**
 * 3. Thông tin Snapshot của đơn hàng đọc từ hệ thống
 */
export interface OrderSnapshot {
    orderId: string;
    buyerId: string;
    docId: string;
    amountVnd: number;
    status: 'PENDING' | 'PAID' | 'EXPIRED' | 'REVIEW_REQUIRED';
    paymentCode: string;
    createdAt: string;
    expiresAt: string;
}

/**
 * 4. Bản ghi giao dịch lưu trữ tại collection `payments/{txnKey}`
 * Tuân thủ schema trong DATABASE_SCHEMA.md:
 * payments/{txnKey}: provider, providerTxnId, recipientId, orderId?, bankOccurredAt, amountVnd, result, normalizedCode?, requestId, firstSeenAt
 */
export interface PaymentRecord {
    txnKey: string;                    // Khóa duy nhất theo namespace (ví dụ: 'sepay_123456')
    provider: 'SEPAY';
    providerTxnId: string;
    recipientId: string;
    orderId?: string;
    bankOccurredAt: string;
    amountVnd: number;
    result: PaymentMatchStatus;
    normalizedCode?: string;
    requestId?: string;                // Request ID truy vết theo chuẩn hệ thống
    firstSeenAt: string;
}

/**
 * 5. Bản ghi đối soát lưu trữ tại collection `reconciliation/{txnKey}`
 * Tuân thủ schema trong DATABASE_SCHEMA.md:
 * reconciliation/{txnKey}: reason, orderId?, firstSeenAt, status, operatorNote?
 */
export interface ReconciliationRecord {
    txnKey: string;
    provider: 'SEPAY';
    providerTxnId: string;
    orderId?: string;
    reason: ReconciliationReason;
    firstSeenAt: string;
    status: 'OPEN' | 'RESOLVED';
    details?: {
        transferredAmount?: number;
        expectedAmount?: number;
        content?: string;
        recipientAccount?: string;
        requestId?: string;
    };
}

/**
 * 6. Tập hợp dữ liệu cần ghi xuống Firestore (Expected Write Set)
 * Bàn giao trực tiếp cho Issue A07 để commit trong 1 Transaction duy nhất (Atomic Commit)
 */
export interface ExpectedWriteSet {
    // Cập nhật trạng thái đơn hàng (nếu có)
    updateOrder?: {
        orderId: string;
        status: 'PAID' | 'REVIEW_REQUIRED';
        paidAt?: string;
    };
    // Cấp quyền sở hữu tài liệu cho Buyer
    createEntitlement?: {
        uid: string;
        docId: string;
        orderId: string;
        grantedAt: string;
    };
    // Bản ghi lịch sử thanh toán
    createPayment: PaymentRecord;
    // Bản ghi đối soát nếu giao dịch bất thường
    createReconciliation?: ReconciliationRecord;
}

/**
 * 7. Kết quả trả về của hàm đối soát thanh toán
 */
export interface PaymentMatchResult {
    status: PaymentMatchStatus;
    httpStatus: 200;                   // Webhook luôn trả về 200 khi kết quả đã được xử lý/phân loại
    reason?: ReconciliationReason;
    message: string;
    writeSet?: ExpectedWriteSet;
}

/**
 * 8. Cổng giao tiếp dữ liệu (Port Interface) theo kiến trúc Ports & Adapters
 * Hàm nghiệp vụ chỉ phụ thuộc vào Interface này, giúp dễ dàng viết Unit Test giả lập.
 */
export interface IPaymentRepository {
    /** Kiểm tra xem giao dịch ngân hàng này đã từng xuất hiện chưa */
    getPaymentByTxnKey(txnKey: string): Promise<PaymentRecord | null>;

    /** Tìm thông tin đơn hàng theo mã thanh toán (paymentCode: 'UNI...') */
    getOrderByPaymentCode(paymentCode: string): Promise<OrderSnapshot | null>;

    /** Kiểm tra xem người mua đã có quyền sở hữu tài liệu chưa */
    hasEntitlement(buyerId: string, docId: string): Promise<boolean>;

    /** Lấy số tài khoản ngân hàng chính thức của hệ thống UniShare để kiểm tra */
    getSystemRecipientAccount(): Promise<string>;
}

// ============================================================================
// HÀM NGHIỆP VỤ CHÍNH (PURE BUSINESS LOGIC)
// ============================================================================

/**
 * Đối soát giao dịch từ Webhook đã xác thực với đơn hàng trong hệ thống (Pure Logic).
 * 
 * Các bước kiểm tra tuần tự:
 * 1. Định danh khóa giao dịch duy nhất: `txnKey = sepay_${event.providerTxnId}`.
 * 2. Kiểm tra tính trùng lặp (Idempotency): Nếu txnKey đã có -> trả về DUPLICATE.
 * 3. Kiểm tra hướng tiền: Phải là tiền vào ('IN').
 * 4. Kiểm tra tài khoản nhận: Phải trùng khớp với tài khoản hệ thống.
 * 5. Kiểm tra mã đơn: Không được chứa nhiều mã, phải có mã chuẩn 'UNI...'.
 * 6. Tìm đơn hàng trong kho dữ liệu: Phải tồn tại và đang ở trạng thái PENDING.
 * 7. Kiểm tra thời hạn: Thời gian ngân hàng (bankOccurredAt) phải <= thời hạn đơn (expiresAt).
 * 8. Kiểm tra số tiền: Phải khớp chính xác 100% số nguyên VND.
 * 9. Kiểm tra quyền sở hữu hiện tại của người mua.
 * 10. Đóng gói kết quả và sinh Expected Write Set cho A07.
 * 
 * @param event Sự kiện webhook đã được chuẩn hóa từ verifyWebhook
 * @param repository Cổng kết nối dữ liệu (Repository Port)
 * @param currentTimeMs Thời điểm xử lý hiện tại (mili-giây), mặc định Date.now()
 * @param requestId Mã định danh request (tùy chọn) để phục vụ audit log theo DATABASE_SCHEMA.md
 */
export async function matchPaymentTransaction(
    event: NormalizedWebhookEvent,
    repository: IPaymentRepository,
    currentTimeMs: number = Date.now(),
    requestId?: string
): Promise<PaymentMatchResult> {
    const txnKey = `sepay_${event.providerTxnId}`;
    const nowIso = new Date(currentTimeMs).toISOString();

    // ------------------------------------------------------------------------
    // BƯỚC 1: Kiểm tra Tính Bất Biến / Trùng Lặp (Idempotency Check)
    // ------------------------------------------------------------------------
    const existingPayment = await repository.getPaymentByTxnKey(txnKey);
    if (existingPayment) {
        return {
            status: 'DUPLICATE',
            httpStatus: 200,
            reason: 'DUPLICATE_TXN',
            message: `Giao dịch ${txnKey} đã được ghi nhận trước đó. Bỏ qua không xử lý lại.`
        };
    }

    // ------------------------------------------------------------------------
    // BƯỚC 2: Kiểm tra Hướng chuyển tiền (Direction Check)
    // ------------------------------------------------------------------------
    if (event.direction !== 'IN') {
        const reconciliationRecord: ReconciliationRecord = {
            txnKey,
            provider: 'SEPAY',
            providerTxnId: event.providerTxnId,
            reason: 'WRONG_DIRECTION',
            firstSeenAt: nowIso,
            status: 'OPEN',
            details: { content: event.content, requestId }
        };

        return {
            status: 'RECONCILIATION_REQUIRED',
            httpStatus: 200,
            reason: 'WRONG_DIRECTION',
            message: 'Giao dịch không phải chiều tiền vào (OUT). Chuyển sang đối soát.',
            writeSet: {
                createPayment: {
                    txnKey,
                    provider: 'SEPAY',
                    providerTxnId: event.providerTxnId,
                    recipientId: event.recipientAccountNumber,
                    bankOccurredAt: event.bankOccurredAt,
                    amountVnd: event.amountVnd,
                    result: 'RECONCILIATION_REQUIRED',
                    requestId,
                    firstSeenAt: nowIso
                },
                createReconciliation: reconciliationRecord
            }
        };
    }

    // ------------------------------------------------------------------------
    // BƯỚC 3: Kiểm tra Số Tài Khoản Nhận Tiền (Recipient Check)
    // ------------------------------------------------------------------------
    const expectedRecipientAccount = await repository.getSystemRecipientAccount();
    if (
        expectedRecipientAccount &&
        event.recipientAccountNumber &&
        event.recipientAccountNumber !== expectedRecipientAccount
    ) {
        const reconciliationRecord: ReconciliationRecord = {
            txnKey,
            provider: 'SEPAY',
            providerTxnId: event.providerTxnId,
            reason: 'WRONG_RECIPIENT',
            firstSeenAt: nowIso,
            status: 'OPEN',
            details: {
                recipientAccount: event.recipientAccountNumber,
                content: event.content,
                requestId
            }
        };

        return {
            status: 'RECONCILIATION_REQUIRED',
            httpStatus: 200,
            reason: 'WRONG_RECIPIENT',
            message: 'Tiền được chuyển vào số tài khoản không thuộc quyền quản lý của UniShare.',
            writeSet: {
                createPayment: {
                    txnKey,
                    provider: 'SEPAY',
                    providerTxnId: event.providerTxnId,
                    recipientId: event.recipientAccountNumber,
                    bankOccurredAt: event.bankOccurredAt,
                    amountVnd: event.amountVnd,
                    result: 'RECONCILIATION_REQUIRED',
                    requestId,
                    firstSeenAt: nowIso
                },
                createReconciliation: reconciliationRecord
            }
        };
    }

    // ------------------------------------------------------------------------
    // BƯỚC 4: Kiểm tra Mã Thanh Toán Trong Nội Dung (Payment Code Check)
    // ------------------------------------------------------------------------
    if (event.hasMultipleCodes) {
        // Chứa từ 2 mã đơn trở lên -> Chặn ngay, không tự đoán mã nào đúng
        return createReconciliationResult(
            txnKey,
            event,
            'MULTIPLE_PAYMENT_CODES',
            'Nội dung giao dịch chứa từ 2 mã thanh toán trở lên. Chuyển đối soát thủ công.',
            nowIso,
            undefined,
            requestId
        );
    }

    if (!event.paymentCode) {
        // Không chứa bất kỳ mã 'UNI...' nào
        return createReconciliationResult(
            txnKey,
            event,
            'MISSING_PAYMENT_CODE',
            'Nội dung giao dịch không chứa mã thanh toán UniShare hợp lệ.',
            nowIso,
            undefined,
            requestId
        );
    }

    // ------------------------------------------------------------------------
    // BƯỚC 5: Tìm Kiếm Đơn Hàng Tương Ứng (Order Lookup)
    // ------------------------------------------------------------------------
    const order = await repository.getOrderByPaymentCode(event.paymentCode);
    if (!order) {
        return createReconciliationResult(
            txnKey,
            event,
            'ORDER_NOT_FOUND',
            `Không tìm thấy đơn hàng nào có mã thanh toán: ${event.paymentCode}`,
            nowIso,
            undefined,
            requestId
        );
    }

    // ------------------------------------------------------------------------
    // BƯỚC 6: Kiểm tra Trạng Thái Đơn Hàng (Order Status Check)
    // ------------------------------------------------------------------------
    if (order.status === 'PAID') {
        // Đơn đã thanh toán trước đó -> Khách chuyển thừa hoặc chuyển lặp lần 2
        return createReconciliationResult(
            txnKey,
            event,
            'ORDER_ALREADY_PAID',
            `Đơn hàng ${order.orderId} đã được thanh toán thành công từ trước. Giao dịch mới cần hoàn tiền/đối soát.`,
            nowIso,
            order.orderId,
            requestId
        );
    }

    if (order.status === 'REVIEW_REQUIRED') {
        // Đơn đang bị tạm khóa đối soát
        return createReconciliationResult(
            txnKey,
            event,
            'ORDER_UNDER_REVIEW',
            `Đơn hàng ${order.orderId} đang trong quá trình đối soát. Tạm hoãn xử lý giao dịch mới.`,
            nowIso,
            order.orderId,
            requestId
        );
    }

    // ------------------------------------------------------------------------
    // BƯỚC 7: Kiểm tra Thời Hạn Đơn Hàng (Expiry Check)
    // ------------------------------------------------------------------------
    const bankTimeMs = new Date(event.bankOccurredAt).getTime();
    const expiryTimeMs = new Date(order.expiresAt).getTime();

    if (order.status === 'EXPIRED' || (!isNaN(bankTimeMs) && !isNaN(expiryTimeMs) && bankTimeMs > expiryTimeMs)) {
        return createReconciliationResult(
            txnKey,
            event,
            'ORDER_EXPIRED',
            `Giao dịch diễn ra sau khi đơn hàng đã hết hạn (Quá hạn 15 phút). Chuyển sang đối soát.`,
            nowIso,
            order.orderId,
            requestId
        );
    }

    // ------------------------------------------------------------------------
    // BƯỚC 8: So Khớp Số Tiền Chính Xác (Exact Amount Check)
    // ------------------------------------------------------------------------
    if (event.amountVnd !== order.amountVnd) {
        // Chuyển thiếu hoặc chuyển thừa tiền -> Không mở khóa tài liệu
        // Đồng thời chuyển đơn sang REVIEW_REQUIRED để chặn trả lặp theo PAYMENT_POC.md §5
        const reconciliationRecord: ReconciliationRecord = {
            txnKey,
            provider: 'SEPAY',
            providerTxnId: event.providerTxnId,
            orderId: order.orderId,
            reason: 'AMOUNT_MISMATCH',
            firstSeenAt: nowIso,
            status: 'OPEN',
            details: {
                transferredAmount: event.amountVnd,
                expectedAmount: order.amountVnd,
                requestId
            }
        };

        return {
            status: 'RECONCILIATION_REQUIRED',
            httpStatus: 200,
            reason: 'AMOUNT_MISMATCH',
            message: `Số tiền chuyển (${event.amountVnd} VND) không khớp với giá đơn (${order.amountVnd} VND).`,
            writeSet: {
                updateOrder: {
                    orderId: order.orderId,
                    status: 'REVIEW_REQUIRED'
                },
                createPayment: {
                    txnKey,
                    provider: 'SEPAY',
                    providerTxnId: event.providerTxnId,
                    recipientId: event.recipientAccountNumber,
                    orderId: order.orderId,
                    bankOccurredAt: event.bankOccurredAt,
                    amountVnd: event.amountVnd,
                    result: 'RECONCILIATION_REQUIRED',
                    normalizedCode: event.paymentCode,
                    requestId,
                    firstSeenAt: nowIso
                },
                createReconciliation: reconciliationRecord
            }
        };
    }

    // ------------------------------------------------------------------------
    // BƯỚC 9: Kiểm tra Quyền Sở Hữu Đã Có (Already Entitled Check)
    // ------------------------------------------------------------------------
    const isAlreadyEntitled = await repository.hasEntitlement(order.buyerId, order.docId);
    if (isAlreadyEntitled) {
        return createReconciliationResult(
            txnKey,
            event,
            'ALREADY_ENTITLED',
            `Người mua ${order.buyerId} đã sở hữu tài liệu ${order.docId} này rồi. Cần đối soát hoàn tiền.`,
            nowIso,
            order.orderId,
            requestId
        );
    }

    // ------------------------------------------------------------------------
    // BƯỚC 10: Khớp Thành Công Hoàn Toàn (MATCHED 200 OK)
    // ------------------------------------------------------------------------
    const matchedWriteSet: ExpectedWriteSet = {
        updateOrder: {
            orderId: order.orderId,
            status: 'PAID',
            paidAt: nowIso
        },
        createEntitlement: {
            uid: order.buyerId,
            docId: order.docId,
            orderId: order.orderId,
            grantedAt: nowIso
        },
        createPayment: {
            txnKey,
            provider: 'SEPAY',
            providerTxnId: event.providerTxnId,
            recipientId: event.recipientAccountNumber,
            orderId: order.orderId,
            bankOccurredAt: event.bankOccurredAt,
            amountVnd: event.amountVnd,
            result: 'MATCHED',
            normalizedCode: event.paymentCode,
            requestId,
            firstSeenAt: nowIso
        }
    };

    return {
        status: 'MATCHED',
        httpStatus: 200,
        message: `Khớp đơn hàng ${order.orderId} thành công. Kích hoạt trạng thái PAID và cấp quyền.`,
        writeSet: matchedWriteSet
    };
}

/**
 * Hàm phụ trợ đóng gói bản ghi đối soát nhanh gọn, tái sử dụng code
 */
function createReconciliationResult(
    txnKey: string,
    event: NormalizedWebhookEvent,
    reason: ReconciliationReason,
    message: string,
    nowIso: string,
    orderId?: string,
    requestId?: string
): PaymentMatchResult {
    const reconciliationRecord: ReconciliationRecord = {
        txnKey,
        provider: 'SEPAY',
        providerTxnId: event.providerTxnId,
        orderId,
        reason,
        firstSeenAt: nowIso,
        status: 'OPEN',
        details: {
            transferredAmount: event.amountVnd,
            content: event.content,
            recipientAccount: event.recipientAccountNumber,
            requestId
        }
    };

    return {
        status: 'RECONCILIATION_REQUIRED',
        httpStatus: 200,
        reason,
        message,
        writeSet: {
            createPayment: {
                txnKey,
                provider: 'SEPAY',
                providerTxnId: event.providerTxnId,
                recipientId: event.recipientAccountNumber,
                orderId,
                bankOccurredAt: event.bankOccurredAt,
                amountVnd: event.amountVnd,
                result: 'RECONCILIATION_REQUIRED',
                normalizedCode: event.paymentCode || undefined,
                requestId,
                firstSeenAt: nowIso
            },
            createReconciliation: reconciliationRecord
        }
    };
}
