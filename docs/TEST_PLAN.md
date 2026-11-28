# KẾ HOẠCH VÀ MA TRẬN KIỂM THỬ (TEST PLAN)
## ISSUE B07 #18: KIỂM THỬ E2E TIỀN THẬT, WEBHOOK VÀ MỞ PDF TRÊN ANDROID

**Dự án:** UniShare · **Giai đoạn:** G4 · **Độ ưu tiên:** P0  
**Người phụ trách:** Hùng (`@hungcoder999911122`)  
**Tài liệu liên quan:** `PROGRESS.md §2 B07, §3 G4, §5 E03–E08/E12`, `DECISIONS.md D19`, PR #33 (B04), PR #37 (A07), PR #40 (A06), PR #38 (C06).

---

## 1. MỤC TIÊU VÀ PHẠM VI KIỂM THỬ

### 1.1. Mục tiêu cốt lõi
Nghiệm thu toàn diện giai đoạn **G4** trên bản build **APK Debug chạy trên thiết bị Android thật**:
1. Chứng minh trước khi mua, mọi tài khoản (kể cả Buyer hợp lệ) đều bị từ chối truy cập PDF (`403 Forbidden` / `404 Not Found`).
2. Giao dịch chuyển khoản tiền thật qua ngân hàng được cổng thanh toán (SePay) ghi nhận tự động và gửi Webhook có chữ ký bảo mật về Backend.
3. Backend thực hiện đối soát tự động và cập nhật dữ liệu nguyên tử (Atomic Transaction): Đơn hàng chuyển sang `PAID` và quyền `entitlement` được cấp trong cùng 1 lần commit duy nhất.
4. Trên ứng dụng Android, sau khi polling nhận trạng thái `PAID`, Buyer mở và đọc được đầy đủ các trang PDF thông qua trình đọc an toàn (`PdfReaderController` kết hợp native `PdfRenderer`).
5. Các ca tiêu cực (chữ ký sai, thiếu tiền, sai tài khoản, quá hạn) và truy cập từ tài khoản khác (Buyer B) tuyệt đối không được cấp quyền đọc tài liệu.

### 1.2. Phân loại môi trường kiểm thử
* **Môi trường Giả lập (Firebase Emulator Suite & Local Test Runner):** Dùng để kiểm thử tự động, lặp lại các ca kiểm thử tích hợp (P01–P06) với dữ liệu mẫu (fixture), kiểm tra tính tương tranh, timeout và các ca tiêu cực mà không làm phát sinh chi phí hoặc rủi ro tài chính.
* **Môi trường Thật (Real Device & Real Money Gateway):** Bắt buộc dùng cho nghiệm thu G4 ca dương (E03/E04). Sử dụng thiết bị Android vật lý kết nối Backend thử nghiệm và thực hiện chuyển khoản tiền thật qua SePay.

---

## 2. MA TRẬN KIỂM THỬ (TEST MATRIX)

| Mã Ca | Tên Ca Kiểm Thử | Mục Tiêu & Kịch Bản | Môi Trường | Điều Kiện Đầu Vào (Pre-conditions) | Các Bước Thực Hiện (Steps) | Kết Quả Mong Đợi (Expected Output) | Trạng Thái |
| :---: | :--- | :--- | :---: | :--- | :--- | :--- | :---: |
| **P01** | **Pre-purchase Deny Check** | Xác nhận trước khi thanh toán, người dùng không thể xem nội dung PDF | Emulator / Máy thật | Đã có tài liệu `doc_test` (status: PUBLISHED). Buyer 1 chưa mua. | 1. Đăng nhập Buyer 1.<br/>2. Gửi request `GET /v1/documents/doc_test/content`. | Trả về `403 FORBIDDEN` (`ENTITLEMENT_REQUIRED`). Không có byte PDF nào được giải phóng. | PASS (Emulator) |
| **P02** | **Webhook Signature & Tolerance** | Chặn webhook giả mạo hoặc có timestamp bị lệch quá dung sai | Emulator | Server cấu hình `SEPAY_WEBHOOK_SECRET`. | 1. Gửi webhook với chữ ký sai.<br/>2. Gửi webhook với timestamp lệch > 300 giây. | Backend từ chối với HTTP 400/401 (`INVALID_SIGNATURE` / `TIMESTAMP_OUT_OF_TOLERANCE`). Database không đổi. | PASS (Emulator) |
| **P03a** | **Wrong Recipient Account** | Không cấp quyền khi tiền chuyển vào sai số tài khoản thụ hưởng | Emulator | Đơn hàng `ord_1` đang `PENDING`. | Gửi webhook đúng mã `paymentCode` nhưng `accountNumber = "WRONG_ACC"`. | Trả về 200, tạo bản ghi `reconciliation` với lý do `WRONG_RECIPIENT`. Đơn vẫn `PENDING`, không cấp `entitlement`. | PASS (Emulator) |
| **P03b** | **Amount Mismatch (Thiếu tiền)** | Không cấp quyền khi chuyển thiếu tiền so với giá trị đơn hàng | Emulator | Đơn hàng 15.000 VNĐ đang `PENDING`. | Gửi webhook với `transferAmount = 10000` (thiếu 5.000 VNĐ). | Trả về 200, đưa vào `reconciliation` với lý do `AMOUNT_MISMATCH`. Tuyệt đối không nâng `PAID`. | PASS (Emulator) |
| **P03c** | **Amount Mismatch (Thừa tiền)** | Không cấp quyền khi chuyển thừa tiền so với giá trị đơn hàng | Emulator | Đơn hàng 15.000 VNĐ đang `PENDING`. | Gửi webhook với `transferAmount = 25000` (thừa 10.000 VNĐ). | Trả về 200, đưa vào `reconciliation` với lý do `AMOUNT_MISMATCH`. Tuyệt đối không nâng `PAID`. | PASS (Emulator) |
| **P03d** | **Missing Payment Code** | Không cấp quyền khi nội dung chuyển khoản không có mã đơn | Emulator | Đơn hàng `ord_1` đang `PENDING`. | Gửi webhook với nội dung chuyển khoản không chứa mã `paymentCode`. | Trả về 200, tạo bản ghi `reconciliation` với lý do `MISSING_PAYMENT_CODE`. Không cấp quyền. | PASS (Emulator) |
| **P03e** | **Multiple Payment Codes** | Chặn gian lận gộp đơn khi nội dung chứa từ 2 mã đơn khác nhau | Emulator | Đơn hàng `ord_1` đang `PENDING`. | Gửi webhook chứa mã `paymentCode` hợp lệ kèm thêm một mã `UNI...` khác. | Trả về 200, tạo bản ghi `reconciliation` với lý do `MULTIPLE_PAYMENT_CODES`. Không cấp quyền. | PASS (Emulator) |
| **P04** | **Idempotency & Concurrency** | Chống xử lý trùng lặp và xung đột khi nhận nhiều webhook đồng thời | Emulator | Đơn hàng `ord_race` đang `PENDING`. | Gửi đồng thời 2 webhook giống hệt nhau (cùng `txnId`, `paymentCode`, `amountVnd`). | Một request trả về `MATCHED`, một trả về `DUPLICATE`. Đơn chỉ đổi sang `PAID` 1 lần, chỉ có đúng 1 `entitlement`. | PASS (Emulator) |
| **P05** | **Expired Order Payment** | Đối soát khi giao dịch phát sinh sau khi đơn hàng đã hết hạn TTL 15 phút | Emulator | Đơn hàng `ord_expired` có `expiresAt < now`. | Gửi webhook chuyển khoản đúng số tiền và mã của đơn hàng đã quá hạn. | Trả về 200, đưa vào `reconciliation` với lý do `ORDER_EXPIRED`. Đơn không đổi sang `PAID`, không cấp quyền. | PASS (Emulator) |
| **P06** | **Atomic Commit & Transient Error** | Đảm bảo tính nguyên tử, không ghi nhận dở dang khi database bị lỗi | Emulator | Đơn hàng hợp lệ đang `PENDING`. | Mô phỏng ngắt kết nối Firestore trong quá trình xử lý transaction. | Trả về HTTP 503 (`PAYMENT_PERSISTENCE_UNAVAILABLE`) để SePay retry. Không có dữ liệu ghi dở dang. | PASS (Emulator) |
| **E03** | **Real-money Android Checkout** | Kiểm thử tạo đơn và hiển thị thông tin chuyển khoản trên thiết bị Android thật | Máy thật | Bản build APK Debug cài trên máy thật kết nối Backend thử nghiệm. | 1. Mở màn hình Chi tiết tài liệu.<br/>2. Bấm "Mua tài liệu" $\rightarrow$ mở màn hình Checkout. | Hiển thị chính xác STK, Tên ngân hàng, Chủ TK, Số tiền và `paymentCode`. Đếm ngược 15 phút hoạt động. | **PASS** (VIVO V2190) |
| **E04** | **Real-money Webhook & PDF Render** | Chuyển tiền thật $\rightarrow$ Webhook SePay thật $\rightarrow$ App tự động mở PDF | Máy thật | Đã tạo đơn E03. Có app ngân hàng thật. | 1. Dùng app ngân hàng chuyển đúng tiền/mã (với VietinBank cần thêm tiền tố `SEVQR ` trước `paymentCode`).<br/>2. App Android polling nhận `PAID`.<br/>3. Bấm "Đọc tài liệu". | Checkout tự động chuyển sang trạng thái thành công. Mở màn hình Reader kết xuất rõ nét các trang PDF. | **PASS** (VIVO V2190) |
| **E07** | **Android Offline / Reopen Recovery** | Đảm bảo đơn `PENDING` không tự đổi thành `FAILED`/`PAID` khi mất mạng và đồng bộ lại khi online | Máy thật | Đơn hàng đang ở trạng thái `PENDING` trên màn Checkout. | 1. Bật chế độ máy bay / tắt Wi-Fi & 4G.<br/>2. Đóng và mở lại app (hoặc bấm làm mới).<br/>3. Bật lại mạng và đối chiếu trạng thái từ server. | Khi mất mạng giữ nguyên `PENDING` + thông báo mạng, không cấp PDF. Khi online lại, `GET /v1/orders/:orderId` phản ánh đúng trạng thái server và cập nhật Thư viện nếu đã `PAID`. | **PASS** (VIVO V2190) |
| **S02** | **Buyer Isolation & Account Switch** | Đảm bảo tài khoản khác không đọc được tài liệu, tự hủy khi đổi tài khoản | Máy thật | Buyer 1 đã mua tài liệu thành công ở ca E04. | 1. Đăng xuất Buyer 1.<br/>2. Đăng nhập Buyer 2.<br/>3. Thử mở tài liệu đã mua của Buyer 1. | Buyer 2 bị chặn 403. Bộ điều khiển `PdfReaderController` tự động xóa bộ nhớ cache và giải phóng tệp. | **PASS** (VIVO V2190) |
| **B05-Note** | **Order History API / UI Check** | Kiểm tra lịch sử giao dịch phản ánh đúng một đơn `PAID` (nếu B05 đã có trên `main`) | Máy thật / API | Buyer 1 đã hoàn tất ca E04. | 1. Kiểm tra API/UI lịch sử mua của Buyer 1.<br/>2. Nếu B05 chưa merge vào `main`, ghi nhận để kiểm lại tại G5. | Hiển thị đúng 1 đơn `PAID` cho giao dịch E04; hoặc ghi chú chuyển sang kiểm lại ở G5 nếu B05 chưa sẵn sàng. | Ghi nhận kiểm lại ở G5 (B05 chưa có UI trên `main`) |

---

## 3. QUY TRÌNH THỰC HIỆN CHI TIẾT (RUNBOOK)

### Bước 1: Chạy kiểm thử tự động trên Firebase Emulator
```bash
# 1. Khởi động Firebase Emulator Suite tại thư mục gốc:
firebase emulators:start --only firestore,auth,storage

# 2. Tại thư mục backend/, chạy bộ test tích hợp E2E:
npm test tests/paymentE2E.test.ts
```
*Yêu cầu:* 100% các ca kiểm thử P01–P06 phải đạt trạng thái `PASS`.

### Bước 2: Thiết lập môi trường chạy thử nghiệm trên máy thật
1. Kết nối điện thoại Android qua cáp USB, bật USB Debugging. Kiểm tra qua lệnh:
   ```bash
   adb devices
   ```
2. Khởi chạy công cụ quay màn hình độ nét cao (khuyến nghị dùng `scrcpy`):
   ```bash
   scrcpy --record demo_b07_real_money.mp4
   ```
3. Cài đặt bản build APK mới nhất lên thiết bị:
   ```bash
   ./gradlew :app:installDebug
   ```

### Bước 3: Thao tác kịch bản E2E tiền thật (E03 + E04 + S02)
1. **Kiểm tra trước mua (Deny Check):**
   - Mở ứng dụng UniShare trên điện thoại.
   - Đăng nhập tài khoản `buyer1@unishare.test`.
   - Vào Thư viện hoặc Chi tiết tài liệu mẫu `doc_g4_demo` $\rightarrow$ Xác nhận không thể đọc file PDF.
2. **Tạo đơn hàng:**
   - Tại trang chi tiết tài liệu, nhấn nút **"Mua ngay"**.
   - Màn hình Checkout xuất hiện: Đối chiếu số tài khoản, tên ngân hàng, số tiền (ví dụ 10.000 VNĐ) và mã thanh toán dạng `UNISHARE XXXXXXXX`.
3. **Thực hiện chuyển khoản:**
   - Mở ứng dụng ngân hàng thật trên điện thoại cá nhân (hoặc quét mã VietQR nếu có).
   - Nhập chính xác số tài khoản thụ hưởng, số tiền và nội dung chuyển tiền chứa đúng mã `paymentCode`.
   - Xác nhận chuyển khoản thành công từ ngân hàng.
4. **Đối soát tự động & Mở tài liệu:**
   - Quay lại màn hình Checkout trên UniShare.
   - Quan sát Polling: Sau 3–10 giây khi SePay bắn webhook, màn hình tự động chuyển sang màu xanh với thông báo **"Thanh toán thành công"**.
   - Nhấn nút **"Đọc tài liệu"** $\rightarrow$ Trình đọc PDF native mở ra, hiển thị rõ ràng trang 1 và trang 2 của tài liệu.
5. **Kiểm chứng tài khoản đối chứng (Buyer 2):**
   - Quay ra màn hình chính, nhấn **"Đăng xuất"**.
   - Đăng nhập tài khoản `buyer2@unishare.test` (chưa mua).
   - Vào lại tài liệu mẫu trên $\rightarrow$ Xác nhận bị chặn và hiển thị nút "Mua tài liệu".

---

## 4. BIÊN BẢN GHI NHẬN KẾT QUẢ THỰC TẾ

* **Commit SHA kiểm thử:** `5cc0080` (nhánh `feature/b07-payment-e2e-tests`)
* **Thiết bị kiểm thử:** `VIVO V2190`
* **Bản build APK:** `app-debug.apk`
* **Ngày giờ thực hiện:** `28/11/2026 01:32:22`
* **Lưu ý cấu hình ngân hàng VietinBank:** Khi chuyển khoản thật qua VietinBank vào tài khoản liên kết SePay, nội dung chuyển khoản **bắt buộc phải bắt đầu bằng từ khóa `SEVQR `** (ví dụ: `SEVQR <paymentCode>`), nếu không VietinBank/SePay sẽ không ghi nhận biến động số dư để kích hoạt webhook.
* **Bằng chứng kiểm thử:** Bằng chứng chi tiết được lưu nội bộ và bàn giao trực tiếp cho Leader khi review PR.

