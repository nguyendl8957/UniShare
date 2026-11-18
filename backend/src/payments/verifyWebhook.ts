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
    | 'INVALID_PAYLOAD'          // Body không phải là JSON hợp lệ hoặc thiếu trường bắt buộc
    | 'SERVER_CONFIG_ERROR';     // Thiếu cấu hình secret key trên server runtime (503)

/**
 * 5. Kết quả trả về sau khi xác minh chữ ký Webhook
 */
export interface WebhookVerificationResult {
    isValid: boolean;
    statusCode: 200 | 400 | 401 | 503; // 200: Hợp lệ, 400: Dữ liệu hỏng/quá hạn, 401: Chữ ký sai, 503: Lỗi runtime
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
 * Định dạng chuẩn UniShare: Tiền tố 'UNI' + đúng 6 ký tự Hex in hoa (tổng cộng 9 ký tự, ví dụ: 'UNIA1B2C3').
 * Sử dụng ranh giới ký tự (word boundary) để không nhận nhầm chuỗi dài hơn.
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

    // Biểu thức chính quy với ranh giới từ: bắt buộc UNI theo sau bởi đúng 6 ký tự hex,
    // không bị dính liền với ký tự chữ hoặc số khác ở hai đầu.
    const regex = /(?<![0-9A-Za-z])UNI[0-9A-Fa-f]{6}(?![0-9A-Za-z])/gi;
    const matches = content.match(regex);

    if (!matches || matches.length === 0) {
        return { code: null, hasMultipleCodes: false };
    }

    // Chuẩn hóa tất cả các mã tìm được thành chữ in hoa và lọc trùng lặp
    const normalizedCodes = Array.from(new Set(matches.map(m => m.toUpperCase())));

    if (normalizedCodes.length > 1) {
        // Cảnh báo: Nội dung chứa từ 2 mã đơn khác nhau -> nghi vấn gian lận hoặc chuyển gộp
        return { code: null, hasMultipleCodes: true };
    }

    return { code: normalizedCodes[0], hasMultipleCodes: false };
}

/**
 * Chuyển đổi chuỗi thời gian của ngân hàng (thường là giờ Việt Nam UTC+7: 'YYYY-MM-DD HH:mm:ss')
 * sang định dạng chuẩn quốc tế UTC ISO 8601 (ví dụ: '2026-11-17T03:05:00.000Z').
 * 
 * Quy tắc an toàn:
 * 1. Phải kiểm tra cấu trúc chuỗi ngày tháng chính xác.
 * 2. Xác thực tính có thực của ngày (ví dụ: không cho phép 31/02 hay 31/04).
 * 3. Chuẩn hóa theo múi giờ Việt Nam (UTC+7) nếu không có chỉ định múi giờ.
 * 4. TUYỆT ĐỐI KHÔNG sử dụng new Date() hoặc thời gian hiện tại làm fallback khi chuỗi không hợp lệ,
 *    để tránh làm sai lệch việc kiểm tra thời hạn TTL 15 phút của đơn hàng.
 * 
 * @param dateStr Chuỗi ngày giờ từ SePay
 * @returns Chuỗi ISO 8601 UTC hợp lệ hoặc null nếu không thể parse / không hợp lệ
 */
export function parseBankTimeToIso(dateStr: string): string | null {
    if (!dateStr || typeof dateStr !== 'string') {
        return null;
    }

    const trimmed = dateStr.trim();
    if (!trimmed) {
        return null;
    }

    // Trường hợp chuỗi đã có chỉ định múi giờ rõ ràng (chứa 'Z' hoặc offset '+HH:mm'/'-HH:mm')
    if (trimmed.includes('Z') || /[0-9]{2}:[0-9]{2}:[0-9]{2}[+-]/.test(trimmed)) {
        const parsed = new Date(trimmed);
        if (!isNaN(parsed.getTime())) {
            return parsed.toISOString();
        }
        return null;
    }

    // Trường hợp SePay trả về dạng 'YYYY-MM-DD HH:mm:ss' hoặc 'YYYY-MM-DDTHH:mm:ss' (Giờ Việt Nam UTC+7)
    const match = trimmed.match(/^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2}):(\d{2})$/);
    if (!match) {
        return null;
    }

    const year = parseInt(match[1], 10);
    const month = parseInt(match[2], 10);
    const day = parseInt(match[3], 10);
    const hour = parseInt(match[4], 10);
    const minute = parseInt(match[5], 10);
    const second = parseInt(match[6], 10);

    // Kiểm tra giới hạn giờ, phút, giây, tháng, năm
    if (month < 1 || month > 12 || day < 1 || day > 31 || hour > 23 || minute > 59 || second > 59) {
        return null;
    }

    // Xác thực số ngày tối đa trong tháng (bao gồm năm nhuận) để ngăn chặn bug ngày cuộn (Date Rollover)
    const daysInMonth = new Date(Date.UTC(year, month, 0)).getUTCDate();
    if (day > daysInMonth) {
        return null;
    }

    // Định dạng ISO với múi giờ Việt Nam +07:00
    const withVnTimezone = `${match[1]}-${match[2]}-${match[3]}T${match[4]}:${match[5]}:${match[6]}+07:00`;
    const parsedWithTz = new Date(withVnTimezone);

    if (!isNaN(parsedWithTz.getTime())) {
        return parsedWithTz.toISOString();
    }

    return null;
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
 * 1. Kiểm tra secret key trên runtime server. Nếu thiếu -> trả về 503 để SePay retry.
 * 2. Kiểm tra sự tồn tại của header chữ ký (`x-sepay-signature`) và thời gian (`x-sepay-timestamp`).
 * 3. Kiểm tra tính hợp lệ của timestamp và độ lệch thời gian (Timestamp Tolerance Window 5 phút).
 * 4. Tạo chữ ký HMAC-SHA256 trên chuỗi `${timestamp}.${rawBody}` với secretKey.
 * 5. So sánh Constant-Time bằng `crypto.timingSafeEqual` để phòng chống Timing Attack.
 * 6. Parse an toàn nội dung JSON từ `rawBody`.
 * 7. Chuẩn hóa dữ liệu sang `NormalizedWebhookEvent` để bàn giao cho bộ đối soát `matchPayment`.
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
    // BƯỚC 1: Kiểm tra Secret Key trên Runtime
    // ------------------------------------------------------------------------
    if (!secretKey || typeof secretKey !== 'string' || !secretKey.trim()) {
        return {
            isValid: false,
            statusCode: 503,
            errorCode: 'SERVER_CONFIG_ERROR',
            message: 'Secret key chưa được cấu hình trên server runtime. Vui lòng thử lại sau.'
        };
    }

    // ------------------------------------------------------------------------
    // BƯỚC 2: Kiểm tra Header bắt buộc
    // ------------------------------------------------------------------------
    const signatureHeader = getHeaderValue(headers, 'x-sepay-signature');
    const timestampStr = getHeaderValue(headers, 'x-sepay-timestamp');

    if (
        !signatureHeader ||
        typeof signatureHeader !== 'string' ||
        !signatureHeader.trim() ||
        !timestampStr ||
        typeof timestampStr !== 'string' ||
        !timestampStr.trim()
    ) {
        return {
            isValid: false,
            statusCode: 400,
            errorCode: 'MISSING_HEADERS',
            message: 'Thiếu header x-sepay-signature hoặc x-sepay-timestamp bắt buộc.'
        };
    }

    // ------------------------------------------------------------------------
    // BƯỚC 3: Kiểm tra Timestamp & Cửa sổ trôi thời gian (5 phút)
    // ------------------------------------------------------------------------
    const cleanTimestampStr = timestampStr.trim();
    const timestampNum = Number(cleanTimestampStr);
    if (isNaN(timestampNum) || !Number.isFinite(timestampNum) || timestampNum <= 0) {
        return {
            isValid: false,
            statusCode: 400,
            errorCode: 'INVALID_TIMESTAMP',
            message: 'Header x-sepay-timestamp không phải là số hợp lệ.'
        };
    }

    // Timestamp của SePay thường tính bằng giây (Unix timestamp in seconds)
    // Nếu timestamp là giây (< 100 tỷ), đổi sang mili-giây
    const timestampMs = timestampNum < 100_000_000_000 ? timestampNum * 1000 : timestampNum;
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
    // BƯỚC 4: Tính toán chữ ký HMAC-SHA256 trên chuỗi `${cleanTimestampStr}.${rawBody}`
    // ------------------------------------------------------------------------
    // Hỗ trợ cả định dạng chữ ký chuẩn tài liệu SePay: 'sha256={hex}' hoặc '{hex}' thuần (không phân biệt hoa thường)
    let actualSignature = signatureHeader.trim();
    if (actualSignature.toLowerCase().startsWith('sha256=')) {
        actualSignature = actualSignature.slice(7).trim();
    }
    actualSignature = actualSignature.toLowerCase();

    const payloadToSign = `${cleanTimestampStr}.${rawBody}`;
    const expectedSignature = crypto
        .createHmac('sha256', secretKey.trim())
        .update(payloadToSign)
        .digest('hex');

    // ------------------------------------------------------------------------
    // BƯỚC 5: So sánh Constant-Time (Phòng chống Timing Attack)
    // ------------------------------------------------------------------------
    const expectedBuffer = Buffer.from(expectedSignature, 'utf-8');
    const actualBuffer = Buffer.from(actualSignature, 'utf-8');

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
    // BƯỚC 6: Parse an toàn JSON từ rawBody
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

    if (!parsedPayload || typeof parsedPayload !== 'object' || Array.isArray(parsedPayload)) {
        return {
            isValid: false,
            statusCode: 400,
            errorCode: 'INVALID_PAYLOAD',
            message: 'Dữ liệu payload của webhook phải là một JSON Object hợp lệ.'
        };
    }

    // ------------------------------------------------------------------------
    // BƯỚC 7: Xác thực chặt chẽ các trường nghiệp vụ bắt buộc
    // ------------------------------------------------------------------------
    // 7.1. Kiểm tra ID giao dịch
    if (
        parsedPayload.id === undefined ||
        parsedPayload.id === null ||
        String(parsedPayload.id).trim() === ''
    ) {
        return {
            isValid: false,
            statusCode: 400,
            errorCode: 'INVALID_PAYLOAD',
            message: 'Dữ liệu webhook thiếu trường id giao dịch bắt buộc.'
        };
    }

    // 7.2. Kiểm tra số tiền chuyển (transferAmount):
    // Phải là số nguyên dương VND (> 0), không chấp nhận float, số âm, 0, NaN, Infinity.
    // TUYỆT ĐỐI KHÔNG dùng Math.round để biến dữ liệu sai thành dữ liệu hợp lệ.
    const rawTransferAmount = parsedPayload.transferAmount;
    if (
        typeof rawTransferAmount !== 'number' ||
        !Number.isFinite(rawTransferAmount) ||
        !Number.isInteger(rawTransferAmount) ||
        rawTransferAmount <= 0 ||
        rawTransferAmount > Number.MAX_SAFE_INTEGER
    ) {
        return {
            isValid: false,
            statusCode: 400,
            errorCode: 'INVALID_PAYLOAD',
            message: 'Số tiền giao dịch (transferAmount) không hợp lệ. Phải là số nguyên dương VND.'
        };
    }

    // 7.3. Kiểm tra thời gian giao dịch phía ngân hàng (transactionDate):
    // Bắt buộc phải có và phải đúng định dạng chuẩn ngân hàng / SePay.
    // TUYỆT ĐỐI KHÔNG tự tiện lấy thời gian hiện tại làm fallback.
    if (!parsedPayload.transactionDate || typeof parsedPayload.transactionDate !== 'string') {
        return {
            isValid: false,
            statusCode: 400,
            errorCode: 'INVALID_PAYLOAD',
            message: 'Dữ liệu webhook thiếu trường transactionDate bắt buộc.'
        };
    }

    const bankOccurredAtIso = parseBankTimeToIso(parsedPayload.transactionDate);
    if (!bankOccurredAtIso) {
        return {
            isValid: false,
            statusCode: 400,
            errorCode: 'INVALID_PAYLOAD',
            message: 'Thời gian giao dịch ngân hàng (transactionDate) không hợp lệ hoặc sai định dạng.'
        };
    }

    // ------------------------------------------------------------------------
    // BƯỚC 8: Chuẩn hóa dữ liệu sang NormalizedWebhookEvent
    // ------------------------------------------------------------------------
    const { code, hasMultipleCodes } = extractPaymentCodeFromContent(parsedPayload.content || '');

    const normalizedEvent: NormalizedWebhookEvent = {
        provider: 'SEPAY',
        providerTxnId: String(parsedPayload.id).trim(),
        amountVnd: rawTransferAmount, // Giữ nguyên số nguyên VND hợp lệ, không qua Math.round
        recipientAccountNumber: String(parsedPayload.accountNumber || '').trim(),
        direction: (parsedPayload.transferType || '').trim().toLowerCase() === 'in' ? 'IN' : 'OUT',
        content: String(parsedPayload.content || ''),
        paymentCode: code,
        hasMultipleCodes,
        bankOccurredAt: bankOccurredAtIso,
        signatureVerifiedAt: new Date(currentTimeMs).toISOString(),
        rawPayload: parsedPayload
    };

    return {
        isValid: true,
        statusCode: 200,
        event: normalizedEvent,
        message: 'Xác thực chữ ký webhook SePay thành công.'
    };
};