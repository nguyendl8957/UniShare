# Bàn giao UniShare trước phát hành

Ngày 28/11/2026. Nhánh `fix/release-readiness`, mốc `829f370`. PR #56 đưa dữ liệu thật vào Seller Dashboard. PR #57 tích hợp VietQR động và đã có biên bản thanh toán thật cho bản trước. Endpoint production `GET /health` trả HTTP 200 trong đợt này. [PR #56](https://github.com/nguyendl8957/UniShare/pull/56) · [PR #57](https://github.com/nguyendl8957/UniShare/pull/57)

## Nghiệp vụ và nguyên nhân đã xác định

| Hạng mục | Kết quả |
| --- | --- |
| Guest, tìm kiếm và chi tiết | Trang chủ dùng catalog Firestore, phân trang mỗi truy vấn theo giới hạn 20 của Rules. Tìm theo tiêu đề hoặc môn học và hỗ trợ tiếng Việt có hoặc không dấu. Giữ kết quả khi quay lại chi tiết. Hiển thị đúng lựa chọn đọc tài liệu đã sở hữu hoặc xem tài liệu của chính mình. API công khai của Seller trả các trường cho phép và danh sách đang đăng bán. |
| Xác thực | Đăng nhập Google dùng Credential Manager và Firebase. Trạng thái quay lại lưu trong Navigation/SavedStateHandle. Bấm mua khi là Guest mở Auth, hủy quay về chi tiết, đăng nhập xong vẫn bấm mua để xác nhận trước khi tạo đơn. Thao tác đăng xuất có hộp thoại xác nhận và xóa ngăn xếp màn hình tài khoản cũ. Cấu hình local có OAuth Web Client ID. [Tài liệu Firebase Android](https://firebase.google.com/docs/auth/android/google-signin) |
| Thanh toán và lịch sử | Checkout dùng giá, thông tin chuyển khoản, mã đơn và trạng thái từ backend. Tiếp tục thanh toán từ lịch sử mở đúng đơn. VietinBank dùng nội dung `SEVQR <paymentCode>` thống nhất với QR và clipboard. Khi hết hạn, thao tác chính kiểm tra lại đơn hiện có. Sau `PAID`, hiện xác nhận, cập nhật Đã mua và tự mở Reader nếu Checkout đang ở màn hình hiện hành. |
| Purchased Library và PDF | Luồng cũ của Library loại mục khi thiếu document cha, còn Reader dựa trên entitlement và version nên vẫn có thể mở. Backend hiện chia sẻ kiểm tra quyền và phiên bản. Metadata cha thiếu dùng tiêu đề dự phòng. Fixture xác minh webhook tạo `PAID`, Library hiện đúng phiên bản khi archive hoặc thiếu metadata, Buyer khác và quyền đã thu hồi bị chặn. Nguyên nhân của đơn lỗi cụ thể trên production còn cần đối chiếu bản ghi thật. |
| Seller | Trang chủ Seller, Quản lý, Cá nhân và Doanh thu đi đúng luồng. Dashboard kiểm tra lại khi trở về và hiển thị dữ liệu từ API. Wizard ba bước xem trước ảnh đã chọn, giữ dữ liệu khi quay bước và xác nhận khi bỏ dữ liệu hoặc dừng upload. Listing đã ẩn vẫn có thể chỉnh sửa hoặc mở lại từ Quản lý. |
| Avatar | Photo Picker chọn ảnh JPG, PNG hoặc WebP tối đa 5 MB. App giảm kích thước và lưu JPEG riêng vào đường dẫn theo UID. Firestore lưu tham chiếu sau khi upload thành công. Hồ sơ Buyer và Seller dùng chung ảnh. Hồ sơ công khai dùng ảnh mặc định vì ảnh tài khoản chỉ chủ tài khoản được đọc. |
| AI | Reader chuyển đúng tên tài liệu sang chat. Bấm citation chuyển về trang PDF nếu số trang nằm trong tài liệu. Backend kiểm tra entitlement và quota, áp dụng cờ bật và allowlist. Model trong source là `gemini-3.5-flash-lite`. [Danh mục model của Google](https://ai.google.dev/gemini-api/docs/pricing/) |

## Giao diện và phiên bản Android

- Buyer dùng màu xanh lá và Seller dùng màu xanh dương từ theme trong `core/ui/theme`. Tận dụng wordmark, biểu tượng và thành phần Compose hiện có. Các mục thiếu dữ liệu như sắp xếp theo đánh giá được ẩn khỏi catalog thật.
- `UniBottomBar` giao vùng thanh điều hướng hệ thống cho Material 3. Các màn hình nhập liệu tiêu thụ padding của Scaffold trước khi áp dụng vùng bàn phím. Figma được đối chiếu ở Trang chủ, kết quả tìm kiếm, Auth, Checkout, Reader, AI, Seller Dashboard và hồ sơ Seller công khai. Checkout dùng VietQR, như nghiệp vụ đã nghiệm thu. [Figma UniShare](https://www.figma.com/design/ETtHZVXDVCTXZ15aF6ihi9/UniShare_UI_Design?node-id=0-1)
- Sáu lời gọi `InputStream.readNBytes` cũ chỉ chạy từ API 33. Các adapter đã chuyển sang đọc có giới hạn tương thích minSdk 26.
- Catalog production còn có listing thử nghiệm cũ trong dữ liệu live. Chủ sở hữu có thể dùng chức năng **Ẩn tài liệu** cho listing đã xác nhận chỉ dùng để thử.

## Kết quả kiểm thử

- Android `:app:assembleDebug`, `:app:assembleRelease`, `:app:assembleDebugAndroidTest`, `:app:testDebugUnitTest` và `:app:lintDebug` PASS. Unit test 90/90, lint 0 lỗi và 59 cảnh báo. APK Release chưa ký, package `vn.unishare`, version `1.0`, minSdk 26, targetSdk 37. SHA-256 `3F12A32E3FDF1E0E46BEA6E8A9F505E111F54A183772943E0DE2B0192B874D14`.
- Backend `tsc --noEmit` và `tsc` PASS. `tsx --test --test-concurrency=1` chạy 121/121 ca backend trên `demo-unishare`, gồm B07 và ca Library/PDF sau archive. `node --test --test-concurrency=1` chạy 43/43 ca Firestore và Storage Rules. Bộ local tách ca `aiLiveE2E.test.ts` vì ca đó cần Gemini API key thật.
- `adb shell am instrument -w vn.unishare.test/androidx.test.runner.AndroidJUnitRunner` PASS 58/58 trên Pixel 10 Pro giả lập. Chạy cùng Firebase Emulator và Express local với tài khoản và tệp tổng hợp. Bao phủ Compose, Order API, upload/publish/archive, hủy upload, Reader hai trang và cách ly Buyer.
- Android Emulator kiểm tra trực tiếp Guest → chi tiết → Auth → hủy → trở về chi tiết. Kiểm tra 412dp và 360dp, font 1,3× và điều hướng ba nút. Ảnh chụp và log kiểm thử local nằm trong `android/app/build/release-check`.
- Gemini live, Google Sign-In với chứng chỉ Release, giao dịch thật và bố cục trên điện thoại vật lý của bản cuối còn chờ nghiệm thu. Backend và Rules mới hiện nằm trong workspace để Leader duyệt và triển khai.

## Nghiệm thu trên điện thoại thật

1. Mở app khi chưa đăng nhập. Tìm theo tiếng Việt, thay bộ lọc, xem preview và hồ sơ Seller công khai. Bấm **Mua ngay**, hủy Auth rồi xác minh Back trở về đúng tài liệu. Lặp lại với đăng nhập Email và Google. Tài liệu vẫn hiện và chưa có đơn mới cho tới khi bấm **Mua ngay** sau đăng nhập.
2. Trên APK đã ký, dùng tài liệu nghiệm thu và tài khoản Buyer được phép. Đối chiếu số tiền, người nhận và nội dung VietQR với nút sao chép. Nếu nhóm thực hiện giao dịch thật, dùng giao dịch giá trị nhỏ do Leader xác nhận. Đợi `PAID`, kiểm tra mở PDF, Đã mua và lịch sử giao dịch. Mở lại app và lặp lại với Buyer đối chứng. Khi mất mạng, quay lại đơn hiện hành và kiểm tra trạng thái trước khi chuyển thêm tiền.
3. Chuyển Seller trên cùng tài khoản, đăng một tài liệu đã được phép dùng, kiểm tra dashboard và catalog, sửa metadata và ẩn listing. Với tài liệu đã mua trước khi ẩn, Buyer vẫn mở đúng phiên bản. Đối chiếu doanh thu ghi nhận từ đơn `PAID`.
4. Chọn rồi hủy ảnh đại diện, lưu một ảnh hợp lệ, đổi mode và mở lại ứng dụng để kiểm tra ảnh. Hỏi AI bằng UID đã được bật và thử citation. Kiểm tra bàn phím, chữ lớn và vùng hệ thống khi dùng điều hướng cử chỉ và ba nút.
5. Nếu đơn cũ còn thiếu trong Đã mua, Leader đối chiếu `orders/{orderId}` và `entitlements/{buyerUid}/items/{docId}`, sau đó so `versionId`, `pdfPath`, `pdfGeneration`, `sizeBytes` và `revokedAt`. Dùng dữ liệu đã che định danh khi báo lỗi. Kiểm tra revision backend đang phục vụ cả hai endpoint trước khi quyết định xử lý dữ liệu.

## Triển khai và ký APK

Các lệnh triển khai bên dưới dành cho Leader khi đã duyệt diff và xác nhận đúng dự án. Lần này cần triển khai Cloud Function `api` và Storage Rules. Firestore Rules và Indexes giữ nguyên. Nếu từng đăng nhập Google bằng bản Debug, thêm fingerprint của **keystore Release** ở Firebase Console → Project settings → Your apps → Android app `vn.unishare`. Kiểm tra Google trong Authentication → Sign-in method. Tải cấu hình Firebase cập nhật nếu OAuth client thay đổi, rồi build lại. Để nghiệm thu AI thật, kiểm tra cờ `UNISHARE_AI_ENABLED`, binding secret `GEMINI_API_KEY` và UID trong `UNISHARE_AI_TEST_UIDS` trên revision phục vụ ứng dụng. [Google Sign-In cho Firebase](https://firebase.google.com/docs/auth/android/google-signin)

```powershell
$repo = 'D:\AndroidStudio\UniShare'
if (!(Test-Path -LiteralPath "$repo\firebase.json")) { throw 'Thiếu cấu hình Firebase' }
Set-Location -LiteralPath $repo
npm --prefix "$repo\backend" run build
if ($LASTEXITCODE -ne 0) { throw 'Backend build thất bại' }
firebase deploy --only 'functions:api,storage' --project unishare-db44a
if ($LASTEXITCODE -ne 0) { throw 'Firebase deploy thất bại' }
$health = Invoke-WebRequest -Uri 'https://asia-southeast1-unishare-db44a.cloudfunctions.net/api/health'
if ($health.StatusCode -ne 200) { throw "Health trả HTTP $($health.StatusCode)" }
```

Ký bằng keystore của nhóm. Lệnh ký tự hỏi mật khẩu ở console. Có thể dùng **Build → Generate Signed Bundle / APK → APK → release** trong Android Studio nếu muốn làm qua giao diện. [Android App Signing](https://developer.android.com/studio/publish/app-signing)

```powershell
$repo = 'D:\AndroidStudio\UniShare'
$tools = 'D:\AndroidStudio\SDK\build-tools\36.0.0'
$keystore = Read-Host 'Đường dẫn đầy đủ tới keystore Release'
if (!(Test-Path -LiteralPath $keystore -PathType Leaf)) { throw 'Không tìm thấy keystore' }
Set-Location -LiteralPath "$repo\android"
.\gradlew.bat :app:assembleRelease
if ($LASTEXITCODE -ne 0) { throw 'Release build thất bại' }
$dir = "$repo\android\app\build\outputs\apk\release"
& "$tools\zipalign.exe" -f -P 16 4 "$dir\app-release-unsigned.apk" "$dir\app-release-aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'zipalign thất bại' }
& "$tools\apksigner.bat" sign --ks $keystore --out "$dir\UniShare-release-signed.apk" "$dir\app-release-aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'Ký APK thất bại' }
& "$tools\apksigner.bat" verify --verbose --print-certs "$dir\UniShare-release-signed.apk"
if ($LASTEXITCODE -ne 0) { throw 'Chữ ký không hợp lệ' }
```

## Các điểm cần nắm khi vấn đáp

- Firebase Auth dùng chung tài khoản cho Buyer và Seller. Mode quyết định điểm đến và màu giao diện, backend xác minh quyền Seller trước khi cho đăng bán.
- Navigation giữ route hoặc màn hình gốc khi mở Auth. Checkout chỉ được tạo khi người dùng xác nhận lại từ chi tiết sau đăng nhập. Màn hình Đã mua đọc API theo UID đăng nhập.
- Webhook HMAC báo về giao dịch. Transaction Firestore ghi `PAID` và entitlement cùng lúc. Reader dùng quyền đó để chọn đúng version và generation của PDF.
- Photo Picker cấp URI cho ảnh người dùng chọn. App giảm kích thước, ghi Storage theo UID và cập nhật tham chiếu Firestore. Google ID token được đổi sang phiên Firebase qua SDK.
- Insets dành chỗ cho thanh hệ thống và bàn phím. Chat chuyển số trang về Reader qua SavedStateHandle. Gemini secret nằm ở Cloud Function.

## Phạm vi file

Thay đổi hiện ở working tree của `fix/release-readiness`, HEAD `829f370`. Thêm `ResponseBody.kt` để đọc phản hồi có giới hạn trên Android 26 và báo cáo này để bàn giao.

- `android/app/build.gradle.kts`
- `android/app/src/androidTest/java/vn/unishare/feature/auth/AuthFlowTest.kt`
- `android/app/src/androidTest/java/vn/unishare/feature/payment/CheckoutScreenTest.kt`
- `android/app/src/androidTest/java/vn/unishare/feature/profile/ProfileFlowTest.kt`
- `android/app/src/androidTest/java/vn/unishare/feature/seller/C04FirebaseIntegrationTest.kt`
- `android/app/src/main/java/vn/unishare/MainActivity.kt`
- `android/app/src/main/java/vn/unishare/core/di/AppContainer.kt`
- `android/app/src/main/java/vn/unishare/core/navigation/AppNavHost.kt`
- `android/app/src/main/java/vn/unishare/core/ui/components/UniBottomBar.kt`
- `android/app/src/main/java/vn/unishare/data/firebase/FirebaseAuthRepository.kt`
- `android/app/src/main/java/vn/unishare/data/firebase/FirestoreProfileRepository.kt`
- `android/app/src/main/java/vn/unishare/domain/repository/AuthRepository.kt`
- `android/app/src/main/java/vn/unishare/domain/repository/ProfileRepository.kt`
- `android/app/src/main/java/vn/unishare/feature/ai/ui/AiChatScreen.kt`
- `android/app/src/main/java/vn/unishare/feature/auth/ui/AuthScreen.kt`
- `android/app/src/main/java/vn/unishare/feature/auth/ui/AuthViewModel.kt`
- `android/app/src/main/java/vn/unishare/feature/explore/ui/DocumentDetailScreen.kt`
- `android/app/src/main/java/vn/unishare/feature/explore/ui/ExploreScreen.kt`
- `android/app/src/main/java/vn/unishare/feature/explore/ui/ExploreUiState.kt`
- `android/app/src/main/java/vn/unishare/feature/explore/ui/ExploreViewModel.kt`
- `android/app/src/main/java/vn/unishare/feature/explore/ui/FirestoreExploreCatalogRepository.kt`
- `android/app/src/main/java/vn/unishare/feature/library/ui/PdfReaderScreen.kt`
- `android/app/src/main/java/vn/unishare/feature/library/ui/PurchasedLibraryScreen.kt`
- `android/app/src/main/java/vn/unishare/feature/library/ui/PurchasedLibraryViewModel.kt`
- `android/app/src/main/java/vn/unishare/feature/library/ui/RealPdfReaderScreen.kt`
- `android/app/src/main/java/vn/unishare/feature/payment/ui/CheckoutScreen.kt`
- `android/app/src/main/java/vn/unishare/feature/payment/ui/CheckoutViewModel.kt`
- `android/app/src/main/java/vn/unishare/feature/profile/ui/BuyerProfileRoleContract.kt`
- `android/app/src/main/java/vn/unishare/feature/profile/ui/ProfileScreen.kt`
- `android/app/src/main/java/vn/unishare/feature/profile/ui/ProfileViewModel.kt`
- `android/app/src/main/java/vn/unishare/feature/seller/ui/A04SellerRepositoryAdapter.kt`
- `android/app/src/main/java/vn/unishare/feature/seller/ui/SellerDocumentsScreen.kt`
- `android/app/src/main/java/vn/unishare/feature/seller/ui/UploadWizardScreen.kt`
- `android/app/src/test/java/vn/unishare/feature/auth/AuthViewModelTest.kt`
- `android/app/src/test/java/vn/unishare/feature/payment/CheckoutViewModelTest.kt`
- `android/gradle/libs.versions.toml`
- `backend/src/documents/content.ts`
- `backend/src/documents/entitlement.ts`
- `backend/src/documents/library.ts`
- `backend/src/documents/manage.ts`
- `backend/src/documents/metadata.ts`
- `backend/src/documents/publish.ts`
- `backend/src/seller/publicProfile.ts`
- `backend/tests/paymentE2E.test.ts`
- `backend/tests/phase1SellerLifecycle.test.ts`
- `firebase/storage.rules`
- `firebase/tests/rules/storage.test.cjs`
- `android/app/src/main/java/vn/unishare/data/remote/ResponseBody.kt`

## Nghiệm thu lần 2 — Sửa lỗi sau kiểm thử trên điện thoại

Đợt sửa dựa trên commit `7208a97` của nhánh `fix/release-readiness`. Các ca tích hợp dùng Firebase Emulator với project `demo-unishare`. Thông tin revision production và Cloud Logging được kiểm tra ở chế độ đọc.

### Đăng bán PDF và Gemini

- **BUG-01.** `publish.ts` trả HTTP 409 `Invalid PDF header` khi byte đầu đối tượng staging khác chữ ký `%PDF-`. Bộ đọc cũ bắt buộc chữ ký ở offset 0. Ca local tái hiện sự khác biệt bằng PDF hai trang có BOM và khoảng trắng trước header, sau đó kiểm tra byte của bản lưu riêng tư. Backend mới kiểm tra header trong 1.024 byte đầu, giới hạn phần đệm ở BOM và ký tự trắng, kiểm tra kết thúc và cấu trúc PDF, đồng thời tải đúng generation. Android đọc đầu luồng SAF khi chọn tệp, mở luồng riêng cho upload và kiểm tra byte bằng hash. Wizard hiển thị giới hạn 25 MiB từ cùng hằng số dùng để kiểm tra dung lượng. Request ID `c0bb8f9c-1daf-4277-a403-83f6b4265d94` không có bản ghi khớp trong truy vấn log và tệp gốc chưa được đính kèm. Cần thử lại chính tệp gốc sau khi deploy để xác định trường hợp trên điện thoại.
- **BUG-02.** Revision `api-00009-moj` tại `asia-southeast1` có binding secret Gemini, cờ AI chưa bật và allowlist có 0 UID. Hai request AI 503 hoàn tất trong khoảng 36–47 ms. Cấu hình khiến gateway trả lỗi trước bước gọi Gemini. Backend và Android tách rõ tắt tính năng, UID chưa được cấp quyền, cấu hình thiếu, PDF không đọc được, hết lượt, lỗi upstream và timeout. Client chờ 65 giây, Function có giới hạn 60 giây và adapter Gemini chờ 15 giây. Retry thủ công dùng lại mã tin nhắn. Gateway hoàn lượt khi upstream xác nhận 429 và bảo toàn reservation khi kết quả upstream chưa rõ. Gemini live, quota và billing của tài khoản vẫn chờ nghiệm thu. [Tài liệu Gemini 3.5 Flash-Lite](https://aistudio.google.com/docs/models/gemini-3.5-flash-lite)
- UniShare dùng `gemini-3.5-flash-lite`. Gateway lấy văn bản theo trang từ PDF đã được cấp quyền rồi gửi đoạn văn bản có giới hạn sang Gemini. Giới hạn AI là 2 MiB và 20 trang.

### Hồi quy theo lỗi

| Lỗi | Thay đổi và bằng chứng local |
| --- | --- |
| BUG-03 | `CheckoutScreen.kt` đặt insets ở header. Kiểm thử PAID đo nút quay lại nằm dưới status bar và kiểm tra nút đọc ở 412dp và 360dp. |
| BUG-04 | `ExploreScreen.kt` chọn palette theo `UniAppMode`. `UniBottomBar.kt` dùng màu Seller cho icon đang chọn. Kiểm thử ảnh của nút lọc xác nhận xanh Buyer và Seller trên hai cấu hình. |
| BUG-05 | `SellerDocumentsScreen.kt` dùng vector xanh dương, một CTA trong trạng thái rỗng và icon hệ thống sáng trên header xanh đậm. Kiểm thử xác nhận màu icon được phục hồi khi rời màn hình. |
| BUG-06 | `DocumentUploadRepositoryImpl.kt`, `UploadWizardViewModel.kt`, Checkout và AI chuyển lỗi HTTP thành thông báo tiếng Việt. Kiểm thử PDF lỗi xác nhận nút retry bị tắt khi cần chọn tệp khác. Log Android `UniShareUpload` lưu mã HTTP và request ID hợp lệ. |
| BUG-07 | Backend kiểm tra publish và retry, sửa metadata, ẩn và mở bán lại, quyền đọc version đã mua, doanh thu từ đơn PAID và webhook lặp. Dashboard và Revenue dùng cùng API tổng hợp. |
| BUG-08 | Fixture xác nhận citation gửi đúng tài liệu và số trang. Reader lấy trang từ SavedStateHandle sau khi PDF tải xong, bỏ qua trang vượt phạm vi và chặn Buyer chưa có entitlement. Ca Gemini live chờ cấu hình production. |

Hai cấu hình giao diện được kiểm tra trên Android Emulator. 412dp dùng điều hướng cử chỉ và cỡ chữ mặc định. 360dp dùng điều hướng ba nút và font scale 1,3. Ảnh minh họa dùng dữ liệu test.

- [Checkout PAID 360dp](D:/AndroidStudio/UniShare/android/app/build/release-check/checkout-paid-360-three-button.png)
- [Quản lý Seller trống 360dp](D:/AndroidStudio/UniShare/android/app/build/release-check/seller-management-empty-360-three-button.png)
- [Document Picker và nhãn 25 MiB trên APK bàn giao](D:/AndroidStudio/UniShare/android/app/build/release-check/seller-upload-pdf-412-gesture.png)

### Kết quả kiểm thử

- Android Debug và Release build thành công. Lint ghi nhận 0 lỗi và 59 cảnh báo. Android unit test đạt 93/93, thêm ba ca về phân loại lỗi AI và thay PDF sau publish thất bại.
- Bộ instrumentation 412dp kiểm tra 61 ca. Lượt đầy đủ đạt 59 ca. Hai ca còn lại đạt khi chạy riêng với thao tác cuộn tới mã chuyển khoản và chờ Document Picker trả trạng thái. Lượt chạy lại trên APK bàn giao đạt 2/2, xác nhận PDF hai trang do Android xuất qua Content URI, nhãn 25 MiB, publish, Catalog, Seller Management và archive. Ba ca giao diện trọng điểm đạt trên cấu hình 360dp với font scale 1,3. So với baseline, bộ đầy đủ có thêm ca theme Explore, Quản lý Seller rỗng và nạp citation vào Reader.
- Backend typecheck và build thành công. Backend đạt 131/131 trên Firebase Emulator, thêm ca kiểm tra PDF, cấu hình AI, quota và timeout. Rules đạt 43/43. Gemini live cần UID và cấu hình production.
- File bàn giao [APK Debug](D:/AndroidStudio/UniShare/android/app/build/outputs/apk/debug/app-debug.apk) đã qua kiểm tra chữ ký. SHA-256 `F065ED68B56F69813AB9FE8026FDE1FD2A1786EAE5148D8994F96AA6D84869D5`.

### Triển khai và kiểm lại trên điện thoại

1. Firebase Console → Authentication → Users. Lấy UID của tài khoản Buyer đã mua tài liệu dùng để thử AI.
2. Trong `backend/.env.unishare-db44a`, đặt `UNISHARE_AI_ENABLED=true` và `UNISHARE_AI_TEST_UIDS=<UID Buyer>`. Nhiều UID phân tách bằng dấu phẩy. Kiểm tra binding `GEMINI_API_KEY` trong Google Cloud Console → Cloud Run functions → `api` → cấu hình revision. Nếu lỗi upstream tiếp diễn sau deploy, mở Google AI Studio để kiểm tra project của key và trang Usage/Rate Limits, sau đó xem log với request ID.
3. Chạy từ thư mục gốc sau khi duyệt diff. Lệnh chỉ triển khai Function `api`. Các Rules hiện hành đã qua regression local.

```powershell
npm --prefix backend run build
if ($LASTEXITCODE -eq 0) {
    firebase deploy --only functions:api --project unishare-db44a
}
```

4. Kiểm tra `/health` trả 200 trên revision mới. Cài APK Debug mới rồi thử lại chính PDF từng lỗi và một PDF hai trang khác. Xác nhận tài liệu xuất hiện trong Quản lý và Catalog. Sửa tên, ẩn, mở lại và kiểm tra quyền đọc của Buyer đã mua.
5. Mở đơn đã thanh toán để kiểm tra header và nút Đọc tài liệu. Chuyển Buyer/Seller và so màu Khám phá, mở Quản lý với Seller trống để kiểm tra hình minh họa, icon hệ thống và CTA. Mở PDF có văn bản dưới giới hạn AI, hỏi câu gợi ý, bấm citation và xác nhận Reader mở đúng trang. Đối chiếu doanh thu với đơn đã ghi nhận.

### Tệp thay đổi trong đợt sửa

Git ghi nhận 39 tệp đã chỉnh sửa. Ba tệp bổ sung được liệt kê bên dưới danh sách này.
- `android/app/src/androidTest/java/vn/unishare/feature/ai/AiChatScreenTest.kt`
- `android/app/src/androidTest/java/vn/unishare/feature/explore/ExploreFlowTest.kt`
- `android/app/src/androidTest/java/vn/unishare/feature/library/PdfReaderControllerIntegrationTest.kt`
- `android/app/src/androidTest/java/vn/unishare/feature/payment/CheckoutScreenTest.kt`
- `android/app/src/androidTest/java/vn/unishare/feature/seller/C04FirebaseIntegrationTest.kt`
- `android/app/src/androidTest/java/vn/unishare/feature/seller/SellerUploadFlowTest.kt`
- `android/app/src/main/java/vn/unishare/core/ui/components/UniBottomBar.kt`
- `android/app/src/main/java/vn/unishare/core/ui/feedback/UiFeedback.kt`
- `android/app/src/main/java/vn/unishare/data/remote/PurchasedPdfApi.kt`
- `android/app/src/main/java/vn/unishare/data/remote/ResponseBody.kt`
- `android/app/src/main/java/vn/unishare/data/repository/DocumentUploadRepositoryImpl.kt`
- `android/app/src/main/java/vn/unishare/feature/ai/ui/AiChatScreen.kt`
- `android/app/src/main/java/vn/unishare/feature/ai/ui/AiChatUiState.kt`
- `android/app/src/main/java/vn/unishare/feature/ai/ui/AiChatViewModel.kt`
- `android/app/src/main/java/vn/unishare/feature/ai/ui/HttpDocumentAiRepository.kt`
- `android/app/src/main/java/vn/unishare/feature/explore/ui/ExploreScreen.kt`
- `android/app/src/main/java/vn/unishare/feature/payment/ui/CheckoutScreen.kt`
- `android/app/src/main/java/vn/unishare/feature/payment/ui/CheckoutViewModel.kt`
- `android/app/src/main/java/vn/unishare/feature/seller/ui/SellerDocumentsScreen.kt`
- `android/app/src/main/java/vn/unishare/feature/seller/ui/UploadWizardScreen.kt`
- `android/app/src/main/java/vn/unishare/feature/seller/ui/UploadWizardViewModel.kt`
- `android/app/src/test/java/vn/unishare/feature/ai/AiHttpStatusTest.kt`
- `android/app/src/test/java/vn/unishare/feature/seller/UploadWizardViewModelTest.kt`
- `backend/src/ai/chatRoute.ts`
- `backend/src/ai/geminiProvider.ts`
- `backend/src/ai/pdfSource.ts`
- `backend/src/ai/pdfText.ts`
- `backend/src/ai/provider.ts`
- `backend/src/ai/quotaLifecycle.ts`
- `backend/src/documents/content.ts`
- `backend/src/documents/publish.ts`
- `backend/src/shared/httpErrors.ts`
- `backend/tests/aiChat.test.ts`
- `backend/tests/aiChatHttp.test.ts`
- `backend/tests/aiDisabled.test.ts`
- `backend/tests/aiGeminiProvider.test.ts`
- `backend/tests/documentPublish.test.ts`
- `backend/tests/paymentE2E.test.ts`
- `docs/RELEASE_READINESS.md`

Các tệp mới


- `backend/src/documents/pdfValidation.ts` chứa kiểm tra header dùng chung cho publish, Reader và AI, cùng kiểm tra dấu kết thúc cho publish.
- `android/app/src/main/res/drawable/ic_seller_empty_illustration.xml` có màu Seller ở từng nét và mảng của vector.
- `android/app/src/androidTest/java/vn/unishare/RegressionScreenshot.kt` chỉ chụp fixture trên emulator khi test runner yêu cầu.
