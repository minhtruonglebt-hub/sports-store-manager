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

## Chức năng

- CRUD danh mục, sản phẩm, khách hàng, đơn hàng và chi tiết đơn hàng.
- Bán hàng tại quầy: giỏ hàng, kiểm tra tồn kho, tạo đơn và cập nhật tồn kho.
- Lọc sản phẩm theo từ khóa, danh mục, giá và trạng thái; có phân trang.
- Dashboard doanh thu, đơn chờ xử lý, cảnh báo tồn kho và số lượng dữ liệu.
- Trạng thái backend và kết nối cơ sở dữ liệu được kiểm tra tự động trên dashboard.
- Đăng nhập theo phiên, phân quyền Admin/Staff, BCrypt và bảo vệ CSRF.
- Thanh toán COD, chuyển khoản chờ xác nhận, MoMo demo và thẻ demo. Lựa chọn demo không thu tiền thật.

## Cấu trúc

- `frontend/` — Next.js App Router; gọi API qua proxy cùng nguồn.
- `src/` — Spring Boot, JPA/Hibernate, REST API và bảo mật.
- `database/sportiva.sql` — bản SQL có cấu trúc và dữ liệu mẫu.

Các API chính: `/api/health`, `/api/auth/*`, `/api/categories`, `/api/products`, `/api/customers`, `/api/orders`, `/api/order-details`, `/api/checkout`, `/api/dashboard`.
