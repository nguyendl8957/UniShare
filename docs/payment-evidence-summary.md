# BÁO CÁO TỔNG HỢP BẰNG CHỨNG NGHIỆM THU THANH TOÁN (G4)
## EVIDENCE SUMMARY: ISSUE B07 #18

**Dự án:** UniShare · **Giai đoạn:** G4 (Thanh toán & Cấp quyền PDF) · **Ưu tiên:** P0  
**Người thực hiện:** Hùng (`@hungcoder999911122`)  
**Người nghiệm thu:** Leader (`@nguyendl8957`)

---

## 1. THÔNG SỐ MÔI TRƯỜNG VÀ PHIÊN BẢN

| Thông Số | Giá Trị Thực Nghiệm | Ghi Chú |
| :--- | :--- | :--- |
| **Nhánh Git (Branch)** | `feature/b07-payment-e2e-tests` | Khởi tạo từ `main` |
| **Commit SHA** | `5cc0080` | SHA bản build nghiệm thu (trên nhánh `feature/b07-payment-e2e-tests`) |
| **Bản build APK** | `app-debug.apk` (Variant: `debug`) | Đã kích hoạt kết nối backend nghiệm thu |
| **Thiết bị Android thật** | `VIVO V2190` | Thiết bị chạy APK thật |
| **Ngày giờ chạy thực tế** | `28/11/2026 01:32:22` | Thời điểm nghiệm thu trên máy thật |
| **Cổng thanh toán** | SePay (Chuyển khoản ngân hàng nhập tay) | Webhook xác thực HMAC-SHA256. **Lưu ý VietinBank:** nội dung chuyển khoản phải bắt đầu bằng `SEVQR ` trước `paymentCode` |
| **Bằng chứng & dữ liệu nhạy cảm** | Lưu trữ nội bộ | Bằng chứng chi tiết được lưu nội bộ và bàn giao trực tiếp cho Leader khi review PR |

---

## 2. TỔNG HỢP KẾT QUẢ CÁC CA KIỂM THỬ (TEST RESULTS)

| Mã Ca | Mô Tả Kịch Bản | Môi Trường | Kết Quả Mong Đợi | Kết Quả Thực Tế | Đánh Giá |
| :---: | :--- | :---: | :--- | :--- | :---: |
| **P01** | Trước mua bị chặn đọc PDF | Emulator | HTTP 403 `ENTITLEMENT_REQUIRED`, không tải được file | Đã xác nhận bị chặn | **PASS** |
| **P02** | Webhook sai chữ ký / quá hạn timestamp | Emulator | HTTP 400/401, không thay đổi database | Đã chặn thành công | **PASS** |
| **P03** | Sai số tài khoản / sai mã / thiếu-thừa tiền / nhiều mã | Emulator | HTTP 200, lưu vào `reconciliation`, không cấp quyền | Đã đối soát cách ly 5 ca âm | **PASS** |
| **P04** | Webhook bắn lặp / đồng thời (Idempotency) | Emulator | Đúng 1 bản ghi `PAID`, 1 `entitlement`, request 2 báo `DUPLICATE` | Đúng tính chất Idempotent | **PASS** |
| **P05** | Thanh toán sau khi đơn hết hạn 15 phút | Emulator | Lưu `reconciliation` (`ORDER_EXPIRED`), không mở PDF | Đã đối soát quá hạn | **PASS** |
| **P06** | Atomic transaction & chu trình E2E mở PDF (kèm P06b lỗi DB 503 & retry) | Emulator | Commit đồng thời `PAID` + `entitlements` trong 1 transaction; lỗi DB trả `503` và retry thành công | Giao dịch nguyên tử an toàn | **PASS** |
| **E03** | Tạo đơn & hiển thị Checkout trên máy thật | Máy thật (`VIVO V2190`) | Màn hình hiển thị đầy đủ STK, số tiền, `paymentCode` hợp lệ | Hiển thị đúng thông tin chuyển khoản trên `VIVO V2190` | **PASS** |
| **E04** | Chuyển tiền thật $\rightarrow$ Webhook $\rightarrow$ Mở PDF | Máy thật (`VIVO V2190`) | Webhook tự động kích hoạt `PAID`, App tự động mở PDF rõ nét | Đã chuyển tiền thật (kèm tiền tố `SEVQR ` cho VietinBank), nhận `PAID` và mở PDF | **PASS** |
| **E07** | Android mất mạng khi `PENDING` & đồng bộ lại khi online | Máy thật (`VIVO V2190`) | Không nhảy sai trạng thái khi offline; online đồng bộ lại đúng từ server | Giữ nguyên `PENDING` khi offline/reopen và đồng bộ đúng khi có mạng | **PASS** |
| **S02** | Đổi sang Buyer 2 bị chặn đọc PDF | Máy thật (`VIVO V2190`) | Buyer 2 không có quyền, app từ chối kết xuất | Buyer 2 không thấy tài liệu trong thư viện và bị chặn đọc | **PASS** |

---

## 3. BẰNG CHỨNG THỰC NGHIỆM ĐÃ CHE (MASKED EVIDENCE)

> [!IMPORTANT]
> Toàn bộ thông tin tài khoản cá nhân, mã giao dịch thô, chữ ký bảo mật và token đã được che chắn (masking) theo đúng quy tắc bảo mật của dự án. Không lưu raw webhook payload hay secret lên repository công khai.

### 3.1. Dữ liệu Đơn hàng & Đối soát (Đã che)
* **Mã đơn hàng (Order ID):** `ord_****`
* **Mã thanh toán (Payment Code):** `UNISHARE ****`
* **Mã giao dịch SePay (Transaction Reference):** `sepay_****`
* **Số tài khoản ngân hàng thụ hưởng:** `0123****789`
* **Tên ngân hàng:** `MBBank`
* **Trạng thái trước thanh toán:** `PENDING`
* **Trạng thái sau thanh toán:** `PAID` (Commit cùng lúc với `entitlements/{buyerUid}/items/{docId}`)

### 3.2. Liên kết Video & Log chi tiết nội bộ
* **Video nghiệm thu chu trình E2E trên máy thật & Log giao dịch Backend:** Bằng chứng chi tiết được lưu nội bộ và bàn giao trực tiếp cho Leader khi review PR.
  - *Nội dung video:* Thể hiện rõ thao tác trước mua bị chặn $\rightarrow$ Tạo đơn $\rightarrow$ Chuyển khoản ngân hàng $\rightarrow$ Polling tự động chuyển `PAID` $\rightarrow$ Mở các trang PDF rõ ràng $\rightarrow$ Chuyển sang tài khoản Buyer 2 bị chặn 403.

---

## 4. KẾT LUẬN NGHIỆM THU

* **Kết luận:** Giai đoạn **G4** (Nghiệp vụ thanh toán tiền thật và cấp quyền PDF nguyên tử) đạt toàn bộ tiêu chuẩn Definition of Done (DoD).
* **Cam kết an toàn:** Không có kẽ hở cho phép client tự sửa trạng thái đơn thành `PAID` hoặc tự tạo quyền `entitlements`. Quyền sở hữu được bảo vệ 100% bởi Backend và Firestore Security Rules.

