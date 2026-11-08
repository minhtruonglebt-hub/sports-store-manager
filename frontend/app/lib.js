export const resources = {
  categories: { label: "Danh mục", icon: "tag", url: "/api/categories",
    fields: [["name", "Tên danh mục", "text", true], ["description", "Mô tả", "textarea"]],
    cols: [["name", "Tên danh mục"], ["description", "Mô tả"]] },
  products: { label: "Sản phẩm", icon: "box", url: "/api/products",
    fields: [["name", "Tên sản phẩm", "text", true], ["categoryId", "Danh mục", "category", true], ["brand", "Thương hiệu", "text"], ["size", "Kích cỡ", "text"], ["color", "Màu sắc", "text"], ["price", "Giá bán (₫)", "number", true], ["quantity", "Tồn kho", "number", true], ["minStock", "Ngưỡng cảnh báo tồn kho", "number", true], ["status", "Trạng thái bán", "productStatus", true]],
    cols: [["name", "Sản phẩm"], ["categoryId", "Danh mục"], ["brand", "Thương hiệu"], ["size", "Kích cỡ"], ["price", "Giá bán"], ["quantity", "Tồn kho"], ["status", "Trạng thái"]] },
  customers: { label: "Khách hàng", icon: "users", url: "/api/customers",
    fields: [["fullName", "Họ và tên", "text", true], ["phone", "Số điện thoại", "tel"], ["email", "Email", "email"], ["address", "Địa chỉ", "textarea"]],
    cols: [["fullName", "Khách hàng"], ["phone", "Điện thoại"], ["email", "Email"], ["address", "Địa chỉ"]] },
  orders: { label: "Đơn hàng", icon: "receipt", url: "/api/orders",
    fields: [["customerId", "Khách hàng", "customer", true], ["orderDate", "Ngày đặt", "datetime-local", true], ["status", "Trạng thái", "orderStatus", true], ["totalAmount", "Tổng tiền (₫)", "number"], ["shippingAddress", "Địa chỉ giao hàng", "textarea"]],
    cols: [["id", "Mã đơn"], ["customerId", "Khách hàng"], ["orderDate", "Ngày đặt"], ["status", "Trạng thái"], ["totalAmount", "Tổng tiền"], ["paymentStatus", "Thanh toán"]] },
  details: { label: "Chi tiết đơn", icon: "list", url: "/api/order-details",
    fields: [["orderId", "Đơn hàng", "order", true], ["productId", "Sản phẩm", "product", true], ["quantity", "Số lượng", "number", true], ["unitPrice", "Đơn giá (₫) — để trống = giá hiện tại", "number"]],
    cols: [["id", "Mã CT"], ["orderId", "Mã đơn"], ["productId", "Sản phẩm"], ["quantity", "Số lượng"], ["unitPrice", "Đơn giá"], ["subtotal", "Thành tiền"]] },
  users: { label: "Tài khoản", icon: "shield", url: "/api/users",
    fields: [["username", "Tên đăng nhập", "text", true], ["password", "Mật khẩu (≥ 12 ký tự)", "password", true], ["role", "Vai trò", "userRole", true], ["enabled", "Trạng thái", "enabled", true]],
    cols: [["username", "Tên đăng nhập"], ["role", "Vai trò"], ["enabled", "Trạng thái"]] },
};

export const orderStatuses = ["Chờ xác nhận", "Đang xử lý", "Đang giao", "Hoàn thành", "Đã hủy"];
export const productStatuses = ["Đang bán", "Sắp hết", "Ngừng bán"];
export const paymentLabels = { COD: "Thanh toán khi nhận hàng", BANK_TRANSFER: "Chuyển khoản", VNPAY: "VNPay", MOMO_DEMO: "MoMo (mô phỏng)", CARD_DEMO: "Thẻ (mô phỏng)" };

export const parseList = (payload) => {
  if (Array.isArray(payload)) return payload;
  if (!payload?._embedded) return [];
  return Object.values(payload._embedded).flatMap((e) => (Array.isArray(e) ? e : []));
};
export const money = (v) => v == null ? "—" : new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND", maximumFractionDigits: 0 }).format(Number(v));
export const shortDate = (v) => v ? new Date(v).toLocaleDateString("vi-VN") : "—";
export const fullDate = (v) => v ? new Date(v).toLocaleString("vi-VN", { dateStyle: "short", timeStyle: "short" }) : "—";
export const localDateTime = () => new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 16);
export const isLow = (p) => p && p.status !== "Ngừng bán" && Number(p.quantity) <= Number(p.minStock ?? 5);

export const paymentDone = (o) => ["Đã xác nhận chuyển khoản", "Đã thu tiền khi giao hàng", "Đã thanh toán VNPay"].includes(o.paymentStatus);
export const canConfirmPayment = (o) => o.status !== "Đã hủy" && !paymentDone(o) &&
  (o.paymentMethod === "BANK_TRANSFER" || (o.paymentMethod === "COD" && o.status === "Hoàn thành"));

const P = {
  home: "M3 11l9-8 9 8M5 10v10h14V10M10 20v-6h4v6",
  tag: "M3 12V3h9l9 9-9 9-9-9zM7.5 7.5h.01",
  box: "M21 8l-9-5-9 5v8l9 5 9-5V8zM3 8l9 5 9-5M12 13v8",
  users: "M16 20v-2a4 4 0 00-4-4H6a4 4 0 00-4 4v2M9 10a4 4 0 100-8 4 4 0 000 8zM22 20v-2a4 4 0 00-3-3.9M16 2.1a4 4 0 010 7.8",
  receipt: "M5 3h14v18l-3-2-2 2-2-2-2 2-2-2-3 2V3zM9 8h6M9 12h6",
  list: "M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01",
  shield: "M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6l8-3z",
  cart: "M3 4h2l2.4 11h10.2L20 7H6M9 20h.01M17 20h.01",
  globe: "M12 21a9 9 0 100-18 9 9 0 000 18zM3 12h18M12 3c3 3 3 15 0 18M12 3c-3 3-3 15 0 18",
  plus: "M12 5v14M5 12h14", edit: "M4 20h4L19 9l-4-4L4 16v4zM14 6l4 4", trash: "M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3",
  x: "M6 6l12 12M18 6L6 18", search: "M11 18a7 7 0 100-14 7 7 0 000 14zM21 21l-5-5",
  plusminus: "M12 5v6M9 8h6M9 17h6", clock: "M12 21a9 9 0 100-18 9 9 0 000 18zM12 7v5l3 2",
  image: "M4 5h16v14H4zM8.5 10a1.5 1.5 0 100-3 1.5 1.5 0 000 3zM20 16l-5-5-8 8",
  out: "M9 21H5V3h4M16 17l5-5-5-5M21 12H9", alert: "M12 3l10 18H2L12 3zM12 10v5M12 18h.01",
  menu: "M4 7h16M4 12h16M4 17h16", down: "M12 4v12M6 12l6 6 6-6M5 21h14", check: "M5 12l5 5 9-10",
  left: "M15 6l-6 6 6 6", right: "M9 6l6 6-6 6", bolt: "M13 2L4 14h7l-1 8 9-12h-7l1-8z",
};
export function Icon({ name, size = 18 }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={P[name] || P.box} /></svg>;
}
