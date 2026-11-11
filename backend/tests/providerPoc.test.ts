import * as crypto from 'crypto';

// Helper sinh chữ ký mô phỏng SePay
export function generateMockSePayHeaders(
  rawBody: string,
  secretKey: string,
  offsetMinutes: number = 0
) {
  // Tạo timestamp (giây hoặc mili giây tùy docs SePay, ở đây giả lập UNIX timestamp)
  const timestamp = Math.floor(Date.now() / 1000) + (offsetMinutes * 60);

  // Băm HMAC-SHA256 chuỗi timestamp + '.' + rawBody
  const signature = crypto
    .createHmac('sha256', secretKey)
    .update(`${timestamp}.${rawBody}`)
    .digest('hex');

  return {
    'X-SePay-Timestamp': timestamp.toString(),
    'X-SePay-Signature': signature
  };
}

// Payload cơ sở hợp lệ (Base Fixture)
export const validWebhookPayload = {
  id: 12345,
  gateway: "MBBank",
  transactionDate: "2026-11-08 15:30:00",
  accountNumber: "0000000000", // KHÔNG dùng dữ liệu ngân hàng thật[cite: 3]
  code: "UNISHARE_ABC123", // Mã tham chiếu duy nhất
  transferType: "in",      // Hướng tiền vào[cite: 1]
  transferAmount: 50000,
  content: "Thanh toan don hang UNISHARE_ABC123"
};

// ------------------------------------------------------------------------
// ------------------------------------------------------------------------
// ------------------------------------------------------------------------


// Import hàm xử lý webhook thuần của bạn (B phụ trách logic này)[cite: 4]
import { verifyWebhook } from '../src/payments/sepayWebhook';

describe('G1a: SePay Webhook Verification Logic', () => {
  const SECRET_KEY = 'test_secret_key_123';

  // Giả lập trạng thái đơn hàng từ DB (đã chụp giá trị từ server)[cite: 1, 6]
  const mockOrderState = {
    orderId: "ORDER_001",
    paymentCode: "UNISHARE_ABC123",
    amountVnd: 50000,
    status: "PENDING",
    recipientId: "MBBank_0000000000"
  };

  describe('P02: Kiểm tra bảo mật & Chữ ký (Security)', () => {
    it('Pass: Chữ ký hợp lệ và trong cửa sổ 5 phút -> Chấp nhận', () => {
      const rawBody = JSON.stringify(validWebhookPayload);
      const headers = generateMockSePayHeaders(rawBody, SECRET_KEY, 0); // 0 phút chênh lệch

      const result = verifyWebhook(headers, rawBody, SECRET_KEY, mockOrderState);
      expect(result.isValid).toBe(true);
      expect(result.action).toBe('GRANT_ENTITLEMENT'); // Ghi PAID và cấp quyền PDF
    });

    it('Fail: Sai chữ ký -> Bị từ chối (401)', () => {
      const rawBody = JSON.stringify(validWebhookPayload);
      const headers = generateMockSePayHeaders(rawBody, 'WRONG_SECRET', 0);

      const result = verifyWebhook(headers, rawBody, SECRET_KEY, mockOrderState);
      expect(result.isValid).toBe(false); // Tuyệt đối không cấp quyền[cite: 3]
    });

    it('Fail: Timestamp cũ quá 5 phút (Replay Attack) -> Bị từ chối', () => {
      const rawBody = JSON.stringify(validWebhookPayload);
      const headers = generateMockSePayHeaders(rawBody, SECRET_KEY, -6); // Trễ 6 phút

      const result = verifyWebhook(headers, rawBody, SECRET_KEY, mockOrderState);
      expect(result.isValid).toBe(false); // Vi phạm cửa sổ 5 phút theo hướng dẫn[cite: 1]
    });
  });

  describe('P03 & P05: Kiểm tra Nghiệp vụ (Business Logic)', () => {
    it('Fail: Thiếu/Thừa số tiền -> Chuyển trạng thái REVIEW_REQUIRED', () => {
      const payload = { ...validWebhookPayload, transferAmount: 10000 }; // Gửi thiếu tiền
      const rawBody = JSON.stringify(payload);
      const headers = generateMockSePayHeaders(rawBody, SECRET_KEY, 0);

      const result = verifyWebhook(headers, rawBody, SECRET_KEY, mockOrderState);
      expect(result.isValid).toBe(true); // Webhook thật
      expect(result.action).toBe('REVIEW_REQUIRED'); // Không mở khóa PDF, không cộng dồn giao dịch lẻ[cite: 1, 3]
    });

    it('Fail: Sai hướng tiền (OUT thay vì IN) -> Bỏ qua', () => {
      const payload = { ...validWebhookPayload, transferType: "out" };
      const rawBody = JSON.stringify(payload);
      const headers = generateMockSePayHeaders(rawBody, SECRET_KEY, 0);

      const result = verifyWebhook(headers, rawBody, SECRET_KEY, mockOrderState);
      expect(result.action).toBe('IGNORE'); // Chỉ tính tiền vào (IN)[cite: 1]
    });

    it('Fail: Đơn hàng đã Hết hạn (EXPIRED) -> Chuyển đối soát, không ghi đè PAID', () => {
      const expiredOrderState = { ...mockOrderState, status: "EXPIRED" };
      const rawBody = JSON.stringify(validWebhookPayload);
      const headers = generateMockSePayHeaders(rawBody, SECRET_KEY, 0);

      const result = verifyWebhook(headers, rawBody, SECRET_KEY, expiredOrderState);
      expect(result.action).toBe('SEND_TO_RECONCILIATION'); // Chuyển đối soát, người dùng phải tạo đơn mới[cite: 1, 3]
    });
  });

  describe('P04: Tính lũy đẳng (Idempotency)', () => {
    it('Pass: Giao dịch trùng lặp (Duplicate) -> Báo thành công 200 nhưng không cấp quyền lần hai', () => {
      // Đơn hàng đã được xử lý trước đó
      const paidOrderState = { ...mockOrderState, status: "PAID" };
      const rawBody = JSON.stringify(validWebhookPayload);
      const headers = generateMockSePayHeaders(rawBody, SECRET_KEY, 0);

      const result = verifyWebhook(headers, rawBody, SECRET_KEY, paidOrderState);
      expect(result.isValid).toBe(true);
      expect(result.action).toBe('ACKNOWLEDGE_ONLY'); // Giao dịch thứ hai có ID mới cho cùng đơn, đi đối soát, không cấp quyền lần hai[cite: 1]
      // Phải trả về 200 success để provider ngừng retry[cite: 6]
    });
  });
});