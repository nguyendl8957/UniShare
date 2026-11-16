import * as crypto from 'crypto';
import { OrderStatus } from './createOrder';

// ============================================================================
// CHÍNH SÁCH ĐƠN HÀNG VÀ THANH TOÁN (ORDER & PAYMENT POLICY)
// Issue: [B02] Xây dựng nghiệp vụ tạo đơn thanh toán
// ============================================================================

export class OrderPolicy {
    /**
     * Thời gian sống tối đa của đơn hàng (TTL - Time To Live): 15 phút.
     * Sau 15 phút, đơn PENDING sẽ coi như hết hạn và không nhận tiền nữa.
     */
    static readonly ORDER_TTL_MINUTES = 15;

    /**
     * Tiền tố định danh mã thanh toán cho hệ thống UniShare.
     * Cần khớp với cú pháp nhận diện webhook của SePay.
     */
    static readonly PAYMENT_CODE_PREFIX = 'UNI';

    /**
     * Sinh mã tham chiếu thanh toán duy nhất (Payment Code).
     * Định dạng: 'UNI' + 6 ký tự Hex in hoa (Tổng cộng 9 ký tự, ví dụ: 'UNIA1B2C3').
     * Sử dụng crypto.randomBytes để sinh chuỗi ngẫu nhiên an toàn (cryptographically secure).
     */
    static generatePaymentCode(): string {
        const randomHex = crypto.randomBytes(3).toString('hex').toUpperCase();
        return `${this.PAYMENT_CODE_PREFIX}${randomHex}`;
    }

    /**
     * Kiểm tra một mã thanh toán có đúng định dạng chuẩn của UniShare hay không.
     * Quy tắc: Phải bắt đầu bằng 'UNI' và theo sau bởi đúng 6 ký tự Hex [0-9A-F].
     */
    static isValidPaymentCode(code: string): boolean {
        if (!code || typeof code !== 'string') return false;
        const regex = /^UNI[0-9A-F]{6}$/;
        return regex.test(code);
    }

    /**
     * Tính thời điểm hết hạn của đơn hàng theo chuẩn UTC ISO 8601.
     * @param createdAtMs Thời điểm tạo đơn (tính bằng milliseconds tính từ Unix Epoch)
     * @returns Chuỗi thời gian chuẩn ISO 8601 (Ví dụ: "2026-11-16T15:30:00.000Z")
     */
    static calculateExpiryTime(createdAtMs: number): string {
        const ttlMs = this.ORDER_TTL_MINUTES * 60 * 1000;
        const expiryMs = createdAtMs + ttlMs;
        return new Date(expiryMs).toISOString();
    }

    /**
     * Kiểm tra xem đơn hàng đã hết hạn hay chưa tại một thời điểm nhất định.
     * @param expiresAtIso Chuỗi thời gian hết hạn dạng ISO 8601
     * @param currentTimeMs Thời điểm hiện tại cần kiểm tra (mặc định lấy Date.now()).
     *                      Việc cho phép truyền currentTimeMs từ ngoài vào là kỹ thuật
     *                      "Dependency Injection" giúp viết Unit Test dễ dàng mà không phụ thuộc đồng hồ máy tính.
     */
    static isOrderExpired(expiresAtIso: string, currentTimeMs: number = Date.now()): boolean {
        const expiryMs = new Date(expiresAtIso).getTime();
        // Nếu chuỗi thời gian không hợp lệ, coi như đã hết hạn để đảm bảo an toàn
        if (isNaN(expiryMs)) return true;
        return currentTimeMs >= expiryMs;
    }

    /**
     * Kiểm tra xem đơn hàng có đang trong trạng thái "hiệu lực" (Active) hay không.
     * Một đơn hàng được xem là Active khi và chỉ khi:
     * 1. Trạng thái hiện tại là PENDING.
     * 2. Thời điểm kiểm tra chưa vượt quá hạn expiresAt.
     */
    static isOrderActive(
        status: OrderStatus,
        expiresAtIso: string,
        currentTimeMs: number = Date.now()
    ): boolean {
        if (status !== 'PENDING') return false;
        return !this.isOrderExpired(expiresAtIso, currentTimeMs);
    }
}
