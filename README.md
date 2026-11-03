# SPORTIVA — Quản lý cửa hàng thể thao

Dự án gồm giao diện **Next.js**, backend **Spring Boot REST API** và database **MariaDB/XAMPP**.

## Mở và chạy

1. Giải nén project và mở thư mục `sports-store-manager` trong VS Code.
2. Mở XAMPP và khởi động MySQL. Nếu chưa có database, nhập `database/sportiva.sql` trong phpMyAdmin.
3. Mở terminal thứ nhất tại thư mục project, chạy `mvn spring-boot:run`.
4. Mở terminal thứ hai tại thư mục `frontend`, chạy `npm install` lần đầu, sau đó `npm run dev`.
5. Vào **http://localhost:3000** để dùng giao diện Next.js. Spring Boot API chạy ở **http://localhost:8081**.

Tài khoản demo: **admin / Admin@123** (quản trị) hoặc **staff / Staff@123** (nhân viên). Đây là tài khoản trình diễn đồ án, không dùng cho hệ thống thật.

Ứng dụng chỉ tạo hai tài khoản demo trong database khi bảng tài khoản còn trống. Khi triển khai, hãy đặt `SPORTSTORE_BOOTSTRAP_ADMIN_USERNAME`, `SPORTSTORE_BOOTSTRAP_ADMIN_PASSWORD`, `SPORTSTORE_BOOTSTRAP_STAFF_USERNAME` và `SPORTSTORE_BOOTSTRAP_STAFF_PASSWORD` trước lần khởi chạy đầu tiên; đổi mật khẩu demo ngay nếu đã dùng. Có thể cấu hình `SPORTSTORE_DB_URL`, `SPORTSTORE_DB_USERNAME`, `SPORTSTORE_DB_PASSWORD`, `SPORTSTORE_UPLOAD_DIR` và `PORT` qua môi trường để tránh ghi thông tin riêng vào mã nguồn.

## Kiểm thử

Chạy `mvn test` tại thư mục project để kiểm tra luồng thanh toán, cập nhật tồn kho, từ chối đơn vượt tồn kho, đăng nhập/phân quyền API và bảo vệ CSRF. Kiểm thử dùng cơ sở dữ liệu H2 riêng, không sửa dữ liệu MariaDB đang chạy.

## VNPay sandbox

Khách hàng có thể chọn VNPay khi cửa hàng cấu hình đủ thông tin merchant. Mặc định lựa chọn này bị vô hiệu hóa; mã thanh toán mô phỏng MoMo/thẻ không thu tiền thật. Để dùng sandbox, lấy `TMN_CODE` và `HASH_SECRET` từ tài khoản VNPay sandbox, rồi đặt biến môi trường trong PowerShell trước khi chạy ứng dụng:

```powershell
$env:SPORTSTORE_VNPAY_TMN_CODE = "TMN_CODE_SANDBOX"
$env:SPORTSTORE_VNPAY_HASH_SECRET = "HASH_SECRET_SANDBOX"
$env:SPORTSTORE_VNPAY_PAYMENT_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html"
$env:SPORTSTORE_VNPAY_RETURN_URL = "https://<địa-chỉ-công-khai-của-bạn>/api/payments/vnpay/return"
mvn spring-boot:run
```

VNPay cần URL trả kết quả công khai có thể truy cập từ Internet; URL localhost chỉ phù hợp chạy và kiểm thử trên máy, không đủ để nhận callback từ sandbox. IPN được cấu hình tại VNPay trỏ đến cùng máy chủ với `/api/payments/vnpay/ipn`. Không gửi hoặc commit `HASH_SECRET`; chỉ xác nhận thanh toán sau khi chữ ký, mã merchant, mã đơn và số tiền được kiểm tra. Giao dịch production cần tài khoản merchant production và URL HTTPS thực tế.

Tìm kiếm/lọc danh sách sản phẩm quản trị dùng truy vấn phân trang ở cơ sở dữ liệu; giới hạn trang tối đa 100 dòng. Kiểm thử tích hợp có kịch bản trên 100 sản phẩm, kiểm tra phân quyền/CSRF, chữ ký callback VNPay sai/đúng và xử lý callback lặp.

## Chức năng

- CRUD danh mục, sản phẩm, khách hàng, đơn hàng và chi tiết đơn hàng.
- Bán hàng tại quầy: giỏ hàng, kiểm tra tồn kho, tạo đơn và cập nhật tồn kho.
- Lọc sản phẩm theo từ khóa, danh mục, giá và trạng thái; có phân trang.
- Ngưỡng cảnh báo tồn kho tùy chỉnh theo từng sản phẩm; dashboard hiển thị giá trị tồn kho và danh sách cảnh báo.
- Nhập/xuất kho có lý do, lịch sử 100 lần thay đổi gần nhất; đơn bán, sửa và hủy cũng được lưu vào lịch sử.
- Dashboard doanh thu, đơn chờ xử lý, cảnh báo tồn kho và số lượng dữ liệu.
- Trạng thái backend và kết nối cơ sở dữ liệu được kiểm tra tự động trên dashboard.
- Đăng nhập theo phiên, phân quyền Admin/Staff, BCrypt và bảo vệ CSRF.
- Thanh toán COD, chuyển khoản chờ xác nhận, VNPay qua sandbox khi có cấu hình merchant, MoMo demo và thẻ demo. Lựa chọn demo không thu tiền thật.

## Cấu trúc

- `frontend/` — Next.js App Router; gọi API qua proxy cùng nguồn.
- `src/` — Spring Boot, JPA/Hibernate, REST API và bảo mật.
- `database/sportiva.sql` — bản SQL có cấu trúc và dữ liệu mẫu.

Các API chính: `/api/health`, `/api/auth/*`, `/api/categories`, `/api/products`, `/api/products/low-stock`, `/api/products/{id}/stock-adjustments`, `/api/products/{id}/stock-movements`, `/api/customers`, `/api/orders`, `/api/order-details`, `/api/checkout`, `/api/dashboard`.

`database/sportiva.sql` là file SQL đầy đủ cho database mới. Khi nâng cấp database đang dùng, Spring Boot với `ddl-auto=update` sẽ thêm cột ngưỡng tồn kho và bảng lịch sử tồn kho khi khởi động.
