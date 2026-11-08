# UniShare – Điểm tựa học thuật cho sinh viên

## 1. Chọn đề tài và bối cảnh hình thành ý tưởng

### Tên đề tài

**UniShare – Điểm tựa học thuật cho sinh viên**

### Lý do chọn đề tài

Trong quá trình học đại học, sinh viên thường xuyên phải tìm nhiều loại tài liệu như giáo trình, slide, đề cương, tài liệu tham khảo, bài tập mẫu hoặc các tài liệu hỗ trợ học tập. Tuy nhiên, những tài liệu này thường nằm ở nhiều nơi khác nhau như các nhóm Facebook, Google Drive, bạn bè chia sẻ hoặc các trang web khác nên đôi khi khá khó tìm được tài liệu phù hợp.

Bên cạnh đó, có những sinh viên tự tổng hợp hoặc làm ra những bộ tài liệu khá đầy đủ và có chất lượng, nhưng thường chỉ chia sẻ trong phạm vi bạn bè hoặc nhóm lớp. Nhóm nhận thấy những tài liệu này có thể được chia sẻ đến nhiều sinh viên khác nếu có một nền tảng phù hợp.

Từ thực tế đó, nhóm nảy ra ý tưởng xây dựng một ứng dụng giống như một **“chợ tài liệu học tập”**, nơi những người có tài liệu có thể đăng bán và những người đang cần tài liệu có thể tìm kiếm, xem trước và mua ngay trên ứng dụng.

Nhóm lựa chọn đề tài **UniShare** với mong muốn tạo ra một nơi tập trung để sinh viên có thể trao đổi tài liệu thuận tiện hơn, đồng thời giúp những người tự biên soạn tài liệu có cơ hội chia sẻ công sức của mình và kiếm thêm thu nhập.

Đây cũng là một đề tài phù hợp để nhóm vận dụng những kiến thức đã học trong môn **Lập trình thiết bị di động**, từ thiết kế giao diện, quản lý tài khoản, xử lý dữ liệu đến xây dựng các chức năng mua bán và quản lý tài liệu.

---

## 2. Lên ý tưởng

### 2.1. Cách UniShare hoạt động

UniShare được xây dựng theo hướng giống một marketplace, trong đó người dùng có thể sử dụng ứng dụng với các vai trò khác nhau:

* **Guest:** Người chưa đăng nhập.
* **Buyer:** Người mua và sử dụng tài liệu.
* **Seller:** Người đăng bán tài liệu.

Nhóm lựa chọn cách hoạt động **Guest-first**, tức là khi mở ứng dụng, người dùng không cần đăng nhập ngay mà có thể vào thẳng trang chủ để khám phá tài liệu.

Guest có thể:

* Xem danh sách tài liệu.
* Tìm kiếm và lọc tài liệu.
* Xem thông tin chi tiết.
* Xem hình ảnh preview của tài liệu.
* Xem hồ sơ công khai của người bán.

Khi người dùng muốn thực hiện những chức năng cần tài khoản như mua tài liệu, xem tài liệu đã mua hoặc chuyển sang chế độ người bán thì lúc đó ứng dụng mới yêu cầu đăng nhập hoặc đăng ký.

Sau khi đăng nhập xong, ứng dụng sẽ đưa người dùng quay lại đúng chỗ họ đang thực hiện. Ví dụ, người dùng đang ở trang chi tiết tài liệu và bấm **“Mua ngay”**, hệ thống yêu cầu đăng nhập. Sau khi đăng nhập thành công thì người dùng được quay lại trang đó để tiếp tục mua.

Nhóm chọn cách này vì nếu bắt người dùng đăng nhập ngay khi mới mở ứng dụng thì có thể làm họ cảm thấy bất tiện và rời khỏi ứng dụng trước khi biết UniShare có gì.

### 2.2. Đối với người mua

Người mua có thể tìm tài liệu dựa trên môn học, danh mục hoặc từ khóa.

Quy trình cơ bản dự kiến sẽ là:

**Khám phá → Tìm kiếm → Xem chi tiết → Xem preview → Mua → Thanh toán → Đọc tài liệu**

Sau khi mua, tài liệu sẽ được lưu trong khu vực tài liệu đã mua. Người dùng có thể mở tài liệu bằng trình đọc PDF của ứng dụng.

Nhóm cũng dự định phát triển thêm chức năng **Chat AI với tài liệu**, giúp người mua có thể hỏi những câu hỏi liên quan đến tài liệu đã mua và hỗ trợ việc học.

### 2.3. Đối với người bán

Người bán có thể đăng những tài liệu do mình tự tạo ra hoặc những tài liệu mà mình có quyền chia sẻ.

Quy trình cơ bản:

**Đăng tài liệu → Nhập thông tin → Upload PDF → Đăng bán → Quản lý tài liệu → Theo dõi doanh thu**

Người bán có thể xem lại những tài liệu mình đã đăng, theo dõi doanh thu và xây dựng hồ sơ cá nhân thông qua đánh giá, huy hiệu và các thông tin khác.

Nhóm mong muốn tạo ra lợi ích cho cả hai bên:

* **Người mua:** Dễ tìm tài liệu hơn và có thêm nhiều lựa chọn.
* **Người bán:** Có thể chia sẻ tài liệu do mình tạo ra và có thêm một nguồn thu nhập.
* **UniShare:** Tạo ra một nơi tập trung để sinh viên trao đổi tài liệu thay vì phải tìm kiếm ở nhiều nơi khác nhau.

---

## 3. Nghiên cứu và phân tích

Trong quá trình tìm hiểu về ý tưởng, nhóm nhận thấy rằng việc làm một ứng dụng mua bán tài liệu không chỉ đơn giản là tạo giao diện, đăng file và thanh toán.

Có một số vấn đề mà nhóm nghĩ rằng sẽ khá khó giải quyết nếu UniShare được triển khai thực tế.

### 3.1. Vấn đề bản quyền

Đây là một trong những vấn đề nhóm quan tâm nhiều nhất.

Tài liệu được đăng lên UniShare có thể chứa giáo trình, hình ảnh, bài viết, slide hoặc nội dung được lấy từ những nguồn khác. Nếu người dùng lấy tài liệu của người khác rồi đăng bán thì có thể xảy ra vấn đề về bản quyền.

Một số trường hợp có thể xảy ra:

* Người dùng đăng bán giáo trình hoặc tài liệu có bản quyền.
* Lấy tài liệu của người khác rồi đăng bán lại.
* Lấy nội dung trên Internet nhưng không ghi nguồn.
* Chỉnh sửa một phần tài liệu rồi cho rằng đó là tài liệu của mình.

Nếu không có cách kiểm soát thì UniShare có thể trở thành nơi chia sẻ lại những tài liệu mà người đăng không thực sự có quyền sử dụng.

**Hướng xử lý nhóm đang nghĩ đến:** Người bán cần xác nhận rằng mình có quyền đăng tài liệu. Ngoài ra có thể thêm chức năng báo cáo tài liệu vi phạm và có cách xử lý hoặc gỡ tài liệu khi có khiếu nại.

Tuy nhiên, đây vẫn là một vấn đề khá khó vì nhóm cần tìm hiểu thêm về cách xác định tài liệu nào thực sự vi phạm bản quyền.

### 3.2. Vấn đề chất lượng tài liệu

Một vấn đề khác là không phải tài liệu nào được đăng lên cũng có chất lượng tốt.

Người mua có thể gặp những trường hợp như:

* Nội dung tài liệu bị sai hoặc thiếu.
* Tài liệu không giống với phần mô tả.
* File bị mờ hoặc khó đọc.
* Tài liệu đã quá cũ.
* Preview nhìn khá tốt nhưng nội dung bên trong lại không như mong đợi.

Nếu những trường hợp này xảy ra nhiều thì người dùng có thể mất niềm tin vào UniShare.

Nhóm dự kiến có thể giải quyết một phần bằng cách cho phép người mua:

* Xem preview trước khi mua.
* Đánh giá tài liệu.
* Đánh giá người bán.
* Viết nhận xét.
* Báo cáo tài liệu có vấn đề.

Ngoài ra, hệ thống có thể xây dựng thêm cơ chế kiểm duyệt tài liệu. Tuy nhiên, việc kiểm tra chất lượng của tất cả tài liệu cũng là một vấn đề mà nhóm cần nghiên cứu thêm.

### 3.3. Vấn đề gian lận học thuật và ăn cắp chất xám

Đây là vấn đề mà nhóm cho rằng khá đặc biệt đối với UniShare vì ứng dụng liên quan trực tiếp đến tài liệu học tập.

Ví dụ, một sinh viên có thể mua một bài báo cáo hoặc một đồ án trên UniShare rồi sử dụng gần như nguyên bản để nộp cho giảng viên.

Một trường hợp khác có thể xảy ra là **hai nhóm sinh viên cùng mua một tài liệu hoặc một bài làm rồi sử dụng nó để nộp cho cùng một môn học**. Khi đó, mặc dù hai nhóm đã mua tài liệu nhưng việc sử dụng nguyên bài để nộp vẫn có thể bị xem là sao chép hoặc gian lận học thuật.

Ngoài ra, cũng có trường hợp một người lấy bài làm của sinh viên khác rồi đăng lên UniShare và nhận đó là sản phẩm của mình.

Điều này khiến nhóm phải suy nghĩ về ranh giới giữa:

**“cung cấp tài liệu để tham khảo”**

và

**“cung cấp bài làm để người khác sao chép”.**

Hướng nhóm đang nghĩ đến là UniShare cần nói rõ mục đích của tài liệu là hỗ trợ học tập và tham khảo. Đối với những loại tài liệu có khả năng được sử dụng trực tiếp để nộp như bài tập hoàn chỉnh, báo cáo hoặc đồ án, nhóm có thể cân nhắc việc hạn chế đăng bán hoặc có cảnh báo rõ ràng.

Đây có lẽ sẽ là một trong những vấn đề khó nhất của dự án vì rất khó để hệ thống tự động xác định người mua đang sử dụng tài liệu để học hay để gian lận.

### 3.4. Vấn đề gian lận trong giao dịch

Vì UniShare có hoạt động mua bán nên nhóm cũng nghĩ đến một số trường hợp gian lận có thể xảy ra:

* Người bán đăng tài liệu không đúng với mô tả.
* Một người tạo nhiều tài khoản để tự đánh giá tài liệu của mình.
* Người bán lấy tài liệu của người khác rồi đăng lại.
* Một tài liệu được đăng nhiều lần bởi nhiều tài khoản khác nhau.

Vì vậy, ngoài việc làm chức năng mua bán, nhóm cũng cần quan tâm đến việc quản lý tài khoản, đánh giá, báo cáo và xử lý những trường hợp vi phạm.

### 3.5. Vấn đề bảo mật tài liệu

Sau khi mua tài liệu, người dùng có thể dễ dàng chia sẻ file cho người khác nếu hệ thống chỉ cho phép tải PDF thông thường.

Điều này có thể gây ảnh hưởng đến người bán vì một tài liệu chỉ cần được mua một lần nhưng có thể bị chia sẻ cho nhiều người khác.

Vì vậy, nhóm dự định sử dụng **trình đọc PDF bên trong ứng dụng** để kiểm soát quyền truy cập tốt hơn thay vì chỉ đưa cho người dùng một đường link tải file trực tiếp.

Một số vấn đề nhóm cần tìm hiểu thêm là:

* Làm sao hạn chế việc chia sẻ tài liệu.
* Làm sao bảo vệ file trên hệ thống.
* Người nào được phép xem tài liệu.
* Làm sao để Guest, Buyer và Seller có quyền truy cập khác nhau.

---

## 4. Định hướng phát triển

Trong giai đoạn đầu, nhóm sẽ tập trung làm những chức năng chính trước, bao gồm:

1. Khám phá và tìm kiếm tài liệu.
2. Xem chi tiết và preview tài liệu.
3. Đăng ký/đăng nhập khi cần tài khoản.
4. Mua và thanh toán tài liệu.
5. Đọc tài liệu sau khi mua.
6. Quản lý hồ sơ cá nhân.
7. Chuyển đổi giữa Buyer và Seller.
8. Đăng bán và quản lý tài liệu.
9. Quản lý doanh thu.
10. Đánh giá và báo cáo tài liệu.
11. Chat AI với tài liệu đã mua.

Sau khi hoàn thành các chức năng cơ bản, nhóm sẽ tiếp tục nghiên cứu những vấn đề khó hơn như kiểm duyệt nội dung, phát hiện tài liệu trùng nhau, bảo vệ bản quyền và hạn chế gian lận học thuật.

---

## 5. Kết luận

Qua quá trình tìm hiểu ban đầu, nhóm nhận thấy UniShare có thể giải quyết một nhu cầu khá thực tế của sinh viên là **tìm kiếm và trao đổi tài liệu học tập ở một nơi tập trung hơn**.

Điểm mà nhóm muốn hướng tới không chỉ là tạo một ứng dụng để **“mua bán file PDF”**, mà là xây dựng một nền tảng trong đó người mua có thể tìm được tài liệu phù hợp, người bán có thể chia sẻ những tài liệu do mình tạo ra và cả hai bên đều có trải nghiệm tương đối an toàn.

Tuy nhiên, nhóm cũng nhận thấy UniShare vẫn còn nhiều vấn đề cần nghiên cứu thêm, đặc biệt là **bản quyền, chất lượng tài liệu, gian lận học thuật, gian lận trong giao dịch và bảo mật tài liệu**.

Đây sẽ là những vấn đề nhóm tiếp tục tìm hiểu trong các giai đoạn tiếp theo của dự án.
