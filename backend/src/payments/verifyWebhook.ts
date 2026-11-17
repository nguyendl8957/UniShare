import * as crypto from 'crypto';

// ============================================================================
// ĐẶC TẢ HỢP ĐỒNG DỮ LIỆU WEBHOOK (DATA CONTRACTS & INTERFACES)
// Issue: [B03] Xác minh webhook và đối soát giao dịch
// ============================================================================

/**
 * 1. Cấu trúc Headers Webhook gửi từ cổng thanh toán SePay
 * Các trường header trong HTTP request thường không phân biệt hoa/thường,
 * do đó cần hỗ trợ đọc linh hoạt.
 */
export interface SePayWebhookHeaders {
    'x-sepay-signature'?: string;
    'x-sepay-timestamp'?: string;
    [key: string]: string | string[] | undefined;
}

/**
 * 2. Cấu trúc Payload JSON gốc do SePay gửi sang
 * Tham khảo từ đặc tả tích hợp Webhook SePay:
 * https://developer.sepay.vn/vi/sepay-webhooks/tich-hop-webhook
 */
export interface SePayRawPayload {
    id: number | string;            // ID giao dịch duy nhất từ SePay (Ví dụ: 123456)
    gateway: string;                 // Tên ngân hàng nhận (Ví dụ: 'MBBank', 'Vietcombank')
    transactionDate: string;         // Thời gian giao dịch phía ngân hàng (Ví dụ: '2026-11-17 09:30:00')
    accountNumber: string;           // Số tài khoản ngân hàng nhận tiền
    code?: string | null;            // Mã thanh toán nếu SePay tự nhận diện được
    content: string;                 // Toàn bộ nội dung chuyển khoản
    transferType: 'in' | 'out';      // 'in': tiền vào, 'out': tiền ra
    transferAmount: number;          // Số tiền chuyển (VND)
    accumulated?: number;            // Số dư lũy kế (nếu có)
    referenceCode?: string;          // Mã tham chiếu từ ngân hàng
}

/**
 * 3. Sự kiện Webhook đã được chuẩn hóa (Normalized Webhook Event)
 * Tách biệt cấu trúc nhà cung cấp (SePay) thành định dạng chuẩn nội bộ UniShare.
 * Module đối soát (matchPayment) sẽ chỉ làm việc với cấu trúc chuẩn hóa này.
 */
export interface NormalizedWebhookEvent {
    provider: 'SEPAY';
    providerTxnId: string;             // Định danh giao dịch phía provider
    amountVnd: number;                 // Số tiền nguyên VND
    recipientAccountNumber: string;    // Số tài khoản nhận tiền
    direction: 'IN' | 'OUT';           // Hướng chuyển tiền
    content: string;                   // Nội dung chuyển khoản
    paymentCode: string | null;        // Mã thanh toán UniShare (bắt đầu bằng 'UNI...')
    hasMultipleCodes: boolean;         // Cờ cảnh báo nếu nội dung chứa từ 2 mã đơn trở lên
    bankOccurredAt: string;            // Thời điểm giao dịch tại ngân hàng (chuẩn UTC ISO 8601)
    signatureVerifiedAt: string;       // Thời điểm xác thực chữ ký (chuẩn UTC ISO 8601)
    rawPayload: SePayRawPayload;       // Payload gốc để đối soát khi cần
}

/**
 * 4. Các mã lỗi xác thực Webhook theo chuẩn API_CONTRACT.md
 */
export type WebhookVerificationErrorCode =
    | 'MISSING_HEADERS'          // Thiếu header chữ ký hoặc timestamp
    | 'INVALID_TIMESTAMP'        // Timestamp không phải là số hợp lệ
    | 'TIMESTAMP_OUT_OF_WINDOW'  // Timestamp vượt quá cửa sổ trôi thời gian cho phép (5 phút)
    | 'INVALID_SIGNATURE'        // Chữ ký HMAC không khớp (sai secret hoặc dữ liệu bị sửa)
    | 'INVALID_PAYLOAD';         // Body không phải là JSON hợp lệ hoặc thiếu trường bắt buộc

/**
 * 5. Kết quả trả về sau khi xác minh chữ ký Webhook
 */
export interface WebhookVerificationResult {
    isValid: boolean;
    statusCode: 200 | 400 | 401; // 200: Hợp lệ, 400: Dữ liệu hỏng/quá hạn, 401: Chữ ký sai
    event?: NormalizedWebhookEvent;
    errorCode?: WebhookVerificationErrorCode;
    message?: string;
}

// ============================================================================
// CÁC HÀM TIỆN ÍCH PHỤ TRỢ (HELPER FUNCTIONS)
// ============================================================================

/**
 * Ngưỡng trôi thời gian cho phép của chữ ký: 5 phút (300 giây = 300.000 ms).
 * Theo đề xuất trong PAYMENT_POC.md §3 để chống tấn công phát lại (Replay Attack).
 */
export const SIGNATURE_TOLERANCE_SECONDS = 300;

/**
 * Trích xuất mã thanh toán UniShare từ nội dung chuyển khoản.
 * Định dạng chuẩn UniShare: Tiền tố 'UNI' + 6 ký tự Hex in hoa (ví dụ: 'UNIA1B2C3').
 * 
 * @param content Nội dung tin nhắn chuyển khoản từ ngân hàng
 * @returns { code: string | null, hasMultipleCodes: boolean }
 */
export function extractPaymentCodeFromContent(content: string): {
    code: string | null;
    hasMultipleCodes: boolean;
} {
    if (!content || typeof content !== 'string') {
        return { code: null, hasMultipleCodes: false };
    }

    // Biểu thức chính quy: tìm các chuỗi bắt đầu bằng 'UNI' và 6 ký tự Hex không phân biệt hoa thường
    const regex = /UNI[0-9A-Fa-f]{6}/g;
    const matches = content.match(regex);

    if (!matches || matches.length === 0) {
        return { code: null, hasMultipleCodes: false };
    }

    // Chuẩn hóa tất cả các mã tìm được thành chữ in hoa
    const normalizedCodes = Array.from(new Set(matches.map(m => m.toUpperCase())));

    if (normalizedCodes.length > 1) {
        // Cảnh báo: Nội dung chứa từ 2 mã đơn khác nhau -> nghi vấn gian lận hoặc chuyển gộp
        return { code: null, hasMultipleCodes: true };
    }

    return { code: normalizedCodes[0], hasMultipleCodes: false };
}

/**
 * Chuyển đổi chuỗi thời gian của ngân hàng (thường là giờ Việt Nam UTC+7: 'YYYY-MM-DD HH:mm:ss')
 * sang định dạng chuẩn quốc tế UTC ISO 8601 (ví dụ: '2026-11-17T02:30:00.000Z').
 * 
 * @param dateStr Chuỗi ngày giờ từ SePay
 * @returns Chuỗi ISO 8601 hoặc chuỗi thời gian hiện tại nếu chuỗi đầu vào không hợp lệ
 */
export function parseBankTimeToIso(dateStr: string): string {
    if (!dateStr || typeof dateStr !== 'string') {
        return new Date().toISOString();
    }

    // Nếu chuỗi đã có định dạng ISO chuẩn (có ký tự 'T')
    if (dateStr.includes('T')) {
        const parsed = new Date(dateStr);
        if (!isNaN(parsed.getTime())) return parsed.toISOString();
    }

    // Trường hợp SePay trả về dạng 'YYYY-MM-DD HH:mm:ss' (Múi giờ Việt Nam UTC+7)
    // Thay khoảng trắng thành 'T' và bổ sung offset múi giờ +07:00
    const normalizedDateStr = dateStr.trim().replace(' ', 'T');
    const withTimezone = normalizedDateStr.includes('+') ? normalizedDateStr : `${normalizedDateStr}+07:00`;
    const parsedWithTz = new Date(withTimezone);

    if (!isNaN(parsedWithTz.getTime())) {
        return parsedWithTz.toISOString();
    }

    // Fallback an toàn nếu chuỗi ngày giờ bị lỗi định dạng
    return new Date().toISOString();
}

/**
 * Tìm kiếm giá trị header không phân biệt chữ hoa, chữ thường
 */
function getHeaderValue(
    headers: Record<string, string | string[] | undefined>,
    targetKey: string
): string | undefined {
    const lowerKey = targetKey.toLowerCase();
    for (const key of Object.keys(headers)) {
        if (key.toLowerCase() === lowerKey) {
            const val = headers[key];
            if (Array.isArray(val)) return val[0];
            return val;
        }
    }
    return undefined;
}

// ============================================================================
// HÀM NGHIỆP VỤ CHÍNH (PURE BUSINESS LOGIC)
// ============================================================================

/**
 * Xác thực tính hợp lệ của Webhook gửi từ SePay (Pure Logic).
 * 
 * Quy trình thực hiện:
 * 1. Kiểm tra sự tồn tại của header chữ ký (`x-sepay-signature`) và thời gian (`x-sepay-timestamp`).
 * 2. Kiểm tra tính hợp lệ của timestamp và độ lệch thời gian (Timestamp Tolerance Window 5 phút).
 * 3. Tạo chữ ký HMAC-SHA256 trên chuỗi `${timestamp}.${rawBody}` với secretKey.
 * 4. So sánh Constant-Time bằng `crypto.timingSafeEqual` để phòng chống Timing Attack.
 * 5. Parse an toàn nội dung JSON từ `rawBody`.
 * 6. Chuẩn hóa dữ liệu sang `NormalizedWebhookEvent` để bàn giao cho bộ đối soát `matchPayment`.
 * 
 * @param rawBody Chuỗi raw body nguyên bản nhận từ HTTP request (chưa qua parse JSON)
 * @param headers Các HTTP header nhận được từ SePay
 * @param secretKey Khóa bí mật webhook được inject từ runtime
 * @param currentTimeMs Thời điểm hiện tại (mili-giây), mặc định Date.now(), phục vụ kiểm thử dễ dàng
 */
export function verifyWebhookSignature(
    rawBody: string,
    headers: Record<string, string | string[] | undefined>,
    secretKey: string,
    currentTimeMs: number = Date.now()
): WebhookVerificationResult {
    // ------------------------------------------------------------------------
    // BƯỚC 1: Kiểm tra Header bắt buộc
    // ------------------------------------------------------------------------
    const signature = getHeaderValue(headers, 'x-sepay-signature');
    const timestampStr = getHeaderValue(headers, 'x-sepay-timestamp');

    if (!signature || !timestampStr) {
        return {
            isValid: false,
            statusCode: 400,
            errorCode: 'MISSING_HEADERS',
            message: 'Thiếu header x-sepay-signature hoặc x-sepay-timestamp bắt buộc.'
        };
    }

    // ------------------------------------------------------------------------
    // BƯỚC 2: Kiểm tra Timestamp & Cửa sổ trôi thời gian (5 phút)
    // ------------------------------------------------------------------------
    const timestampNum = Number(timestampStr);
    if (isNaN(timestampNum) || timestampNum <= 0) {
        return {
            isValid: false,
            statusCode: 400,
            errorCode: 'INVALID_TIMESTAMP',
            message: 'Header x-sepay-timestamp không phải là số hợp lệ.'
        };
    }

    // Timestamp của SePay thường tính bằng giây (Unix timestamp in seconds)
    // Nếu timestamp là giây (độ dài 10 chữ số), đổi sang mili-giây
    const timestampMs = timestampStr.length <= 10 ? timestampNum * 1000 : timestampNum;
    const timeDiffMs = Math.abs(currentTimeMs - timestampMs);
    const maxToleranceMs = SIGNATURE_TOLERANCE_SECONDS * 1000;

    if (timeDiffMs > maxToleranceMs) {
        return {
            isValid: false,
            statusCode: 400,
            errorCode: 'TIMESTAMP_OUT_OF_WINDOW',
            message: `Timestamp của webhook bị lệch quá ${SIGNATURE_TOLERANCE_SECONDS} giây so với đồng hồ hệ thống.`
        };
    }

    // ------------------------------------------------------------------------
    // BƯỚC 3: Tính toán chữ ký HMAC-SHA256 trên chuỗi `${timestamp}.${rawBody}`
    // ------------------------------------------------------------------------
    if (!secretKey) {
        return {
            isValid: false,
            statusCode: 401,
            errorCode: 'INVALID_SIGNATURE',
            message: 'Secret key chưa được cấu hình trên runtime.'
        };
    }

    const payloadToSign = `${timestampStr}.${rawBody}`;
    const expectedSignature = crypto
        .createHmac('sha256', secretKey)
        .update(payloadToSign)
        .digest('hex');

    // ------------------------------------------------------------------------
    // BƯỚC 4: So sánh Constant-Time (Phòng chống Timing Attack)
    // ------------------------------------------------------------------------
    const expectedBuffer = Buffer.from(expectedSignature, 'utf-8');
    const actualBuffer = Buffer.from(signature, 'utf-8');

    // timingSafeEqual bắt buộc 2 Buffer phải có cùng độ dài (byte length)
    if (expectedBuffer.length !== actualBuffer.length) {
        return {
            isValid: false,
            statusCode: 401,
            errorCode: 'INVALID_SIGNATURE',
            message: 'Chữ ký webhook không hợp lệ (độ dài không khớp).'
        };
    }

    const isMatch = crypto.timingSafeEqual(expectedBuffer, actualBuffer);
    if (!isMatch) {
        return {
            isValid: false,
            statusCode: 401,
            errorCode: 'INVALID_SIGNATURE',
            message: 'Chữ ký webhook không hợp lệ (HMAC signature mismatch).'
        };
    }

    // ------------------------------------------------------------------------
    // BƯỚC 5: Parse an toàn JSON từ rawBody
    // ------------------------------------------------------------------------
    let parsedPayload: SePayRawPayload;
    try {
        parsedPayload = JSON.parse(rawBody);
    } catch {
        return {
            isValid: false,
            statusCode: 400,
            errorCode: 'INVALID_PAYLOAD',
            message: 'Nội dung rawBody không phải là định dạng JSON hợp lệ.'
        };
    }

    // Kiểm tra các trường tối thiểu cần có
    if (!parsedPayload.id || typeof parsedPayload.transferAmount !== 'number') {
        return {
            isValid: false,
            statusCode: 400,
            errorCode: 'INVALID_PAYLOAD',
            message: 'Dữ liệu webhook thiếu trường id hoặc transferAmount.'
        };
    }

    // ------------------------------------------------------------------------
    // BƯỚC 6: Chuẩn hóa dữ liệu sang NormalizedWebhookEvent
    // ------------------------------------------------------------------------
    const { code, hasMultipleCodes } = extractPaymentCodeFromContent(parsedPayload.content);

    const normalizedEvent: NormalizedWebhookEvent = {
        provider: 'SEPAY',
        providerTxnId: String(parsedPayload.id),
        amountVnd: Math.round(parsedPayload.transferAmount),
        recipientAccountNumber: String(parsedPayload.accountNumber || ''),
        direction: (parsedPayload.transferType || '').toLowerCase() === 'in' ? 'IN' : 'OUT',
        content: parsedPayload.content || '',
        paymentCode: code,
        hasMultipleCodes,
        bankOccurredAt: parseBankTimeToIso(parsedPayload.transactionDate),
        signatureVerifiedAt: new Date(currentTimeMs).toISOString(),
        rawPayload: parsedPayload
    };

    return {
        isValid: true,
        statusCode: 200,
        event: normalizedEvent,
        message: 'Xác thực chữ ký webhook SePay thành công.'
    };
}

