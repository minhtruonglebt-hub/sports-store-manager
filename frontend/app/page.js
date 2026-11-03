"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";

const resources = {
  categories: { label: "Danh mục", icon: "▦", url: "/api/categories", fields: [["name", "Tên danh mục", "text", true], ["description", "Mô tả", "textarea"]], cols: [["name", "Tên danh mục"], ["description", "Mô tả"]] },
  products: { label: "Sản phẩm", icon: "◈", url: "/api/products", fields: [["name", "Tên sản phẩm", "text", true], ["categoryId", "Danh mục", "category", true], ["brand", "Thương hiệu", "text"], ["size", "Kích cỡ", "text"], ["color", "Màu sắc", "text"], ["price", "Giá bán (₫)", "number", true], ["quantity", "Tồn kho", "number", true], ["minStock", "Ngưỡng cảnh báo tồn kho", "number", true], ["status", "Trạng thái", "productStatus", true]], cols: [["name", "Sản phẩm"], ["categoryId", "Danh mục"], ["brand", "Thương hiệu"], ["size", "Kích cỡ"], ["price", "Giá bán"], ["quantity", "Tồn kho"], ["minStock", "Cảnh báo từ"], ["status", "Trạng thái"]] },
  customers: { label: "Khách hàng", icon: "♙", url: "/api/customers", fields: [["fullName", "Họ và tên", "text", true], ["phone", "Số điện thoại", "tel"], ["email", "Email", "email"], ["address", "Địa chỉ", "textarea"]], cols: [["fullName", "Khách hàng"], ["phone", "Điện thoại"], ["email", "Email"], ["address", "Địa chỉ"]] },
  orders: { label: "Đơn hàng", icon: "▤", url: "/api/orders", fields: [["customerId", "Khách hàng", "customer", true], ["orderDate", "Ngày đặt", "datetime-local", true], ["status", "Trạng thái", "orderStatus", true], ["totalAmount", "Tổng tiền (₫)", "number", true], ["shippingAddress", "Địa chỉ giao hàng", "textarea"], ["paymentMethod", "Thanh toán", "text"], ["paymentStatus", "Trạng thái thanh toán", "text"]], cols: [["id", "Mã đơn"], ["customerId", "Khách hàng"], ["orderDate", "Ngày đặt"], ["status", "Trạng thái"], ["totalAmount", "Tổng tiền"], ["paymentStatus", "Thanh toán"]] },
  details: { label: "Chi tiết đơn", icon: "≡", url: "/api/order-details", fields: [["orderId", "Đơn hàng", "order", true], ["productId", "Sản phẩm", "product", true], ["quantity", "Số lượng", "number", true], ["unitPrice", "Đơn giá (₫)", "number", true], ["subtotal", "Thành tiền (₫)", "number", true]], cols: [["id", "Mã CT"], ["orderId", "Mã đơn"], ["productId", "Sản phẩm"], ["quantity", "Số lượng"], ["unitPrice", "Đơn giá"], ["subtotal", "Thành tiền"]] },
  users: { label: "Tài khoản", icon: "♙", url: "/api/users", fields: [["username", "Tên đăng nhập", "text", true], ["password", "Mật khẩu mới", "password", true], ["role", "Vai trò", "userRole", true], ["enabled", "Trạng thái", "enabled", true]], cols: [["username", "Tên đăng nhập"], ["role", "Vai trò"], ["enabled", "Trạng thái"]] },
};
const navItems = [{ key: "dashboard", label: "Tổng quan", icon: "⌂" }, ...Object.entries(resources).map(([key, value]) => ({ key, ...value })), { key: "checkout", label: "Bán hàng", icon: "🛒" }];
const statusOptions = { productStatus: ["Đang bán", "Sắp hết", "Ngừng bán"], orderStatus: ["Chờ xác nhận", "Đang xử lý", "Đang giao", "Hoàn thành", "Đã hủy"] };
const parseList = (payload) => {
  if (Array.isArray(payload)) return payload;
  if (!payload?._embedded) return [];
  return Object.values(payload._embedded).flatMap((entry) => Array.isArray(entry) ? entry : []);
};
const money = (value) => value == null ? "—" : new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND", maximumFractionDigits: 0 }).format(Number(value));
const localDateTime = () => { const d = new Date(Date.now() - new Date().getTimezoneOffset() * 60000); return d.toISOString().slice(0, 16); };

export default function Home() {
  const [account, setAccount] = useState(null);
  const [backendStatus, setBackendStatus] = useState("checking");
  const isAdmin = account?.roles?.includes("ADMIN");
  const [page, setPage] = useState("dashboard");
  const [data, setData] = useState({ categories: [], products: [], customers: [], orders: [], details: [], users: [] });
  const [metrics, setMetrics] = useState(null);
  const [revenueReport, setRevenueReport] = useState([]);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [toast, setToast] = useState("");
  const [error, setError] = useState("");
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("all");
  const [categoryFilter, setCategoryFilter] = useState("");
  const [minPrice, setMinPrice] = useState("");
  const [maxPrice, setMaxPrice] = useState("");
  const [pageIndex, setPageIndex] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [filteredProducts, setFilteredProducts] = useState(null);
  const [modal, setModal] = useState(null);
  const [stockProduct, setStockProduct] = useState(null);
  const [stockHistory, setStockHistory] = useState(null);
  const [cart, setCart] = useState({});
  const [saleCustomer, setSaleCustomer] = useState("");
  const [saleAddress, setSaleAddress] = useState("");
  const [paymentMethod, setPaymentMethod] = useState("COD");
  const [saleQuery, setSaleQuery] = useState("");
  const csrf = useRef("");
  const toastTimer = useRef(null);

  const notify = useCallback((message) => {
    setToast(message);
    clearTimeout(toastTimer.current);
    toastTimer.current = setTimeout(() => setToast(""), 3000);
  }, []);

  const api = useCallback(async (url, options = {}) => {
    const method = (options.method || "GET").toUpperCase();
    const headers = new Headers(options.headers || {});
    const isFormData = typeof FormData !== "undefined" && options.body instanceof FormData;
    if (options.body && !isFormData && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
    if (!["GET", "HEAD", "OPTIONS", "TRACE"].includes(method) && csrf.current) headers.set("X-XSRF-TOKEN", csrf.current);
    const response = await fetch(url, { credentials: "same-origin", ...options, headers });
    const text = await response.text();
    let payload = null;
    if (text) { try { payload = JSON.parse(text); } catch { payload = { message: text }; } }
    if (!response.ok) {
      if (response.status === 401 && !url.includes("/api/auth/login")) setAccount(null);
      throw new Error(payload?.message || `Lỗi máy chủ (${response.status})`);
    }
    return payload;
  }, []);

  useEffect(() => {
    let active = true;
    const checkBackend = async () => {
      try {
        const response = await fetch("/api/health", { cache: "no-store" });
        const health = response.ok ? await response.json() : null;
        if (active) setBackendStatus(health?.status === "UP" ? "up" : "down");
      } catch {
        if (active) setBackendStatus("down");
      }
    };
    checkBackend();
    const interval = setInterval(checkBackend, 15000);
    return () => { active = false; clearInterval(interval); };
  }, []);

  const loadCsrf = useCallback(async () => {
    const result = await api("/api/auth/csrf");
    csrf.current = result.token;
  }, [api]);

  const refreshData = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const entries = await Promise.all(Object.entries(resources)
        .filter(([key]) => key !== "users" || isAdmin)
        .map(async ([key, config]) => [key, parseList(await api(config.url))]));
      setData(Object.fromEntries(entries));
      const [summary, report] = await Promise.all([
        api("/api/dashboard"),
        api("/api/dashboard/revenue?days=30"),
      ]);
      setMetrics(summary);
      setRevenueReport(report.items || []);
    } catch (e) {
      setError(e.message || "Không tải được dữ liệu.");
    } finally { setLoading(false); }
  }, [api, isAdmin]);

  useEffect(() => {
    let active = true;
    (async () => {
      try {
        await loadCsrf();
        const current = await api("/api/auth/me");
        if (!active) return;
        setAccount(current);
        await refreshData();
      } catch { if (active) setAccount(null); }
    })();
    return () => { active = false; clearTimeout(toastTimer.current); };
  }, [api, loadCsrf, refreshData]);

  const runProductFilter = useCallback(async () => {
    const params = new URLSearchParams({ q: query, page: String(pageIndex), size: String(pageSize) });
    if (categoryFilter) params.set("categoryId", categoryFilter);
    if (minPrice) params.set("minPrice", minPrice);
    if (maxPrice) params.set("maxPrice", maxPrice);
    if (statusFilter !== "all") params.set("status", statusFilter);
    try { setFilteredProducts(await api(`/api/products/filter?${params}`)); }
    catch (e) { setError(e.message); }
  }, [api, query, pageIndex, pageSize, categoryFilter, minPrice, maxPrice, statusFilter]);

  useEffect(() => {
    if (account && page === "products") {
      const timer = setTimeout(runProductFilter, 220);
      return () => clearTimeout(timer);
    }
  }, [account, page, runProductFilter]);

  const changePage = (key) => { document.body.classList.remove("menu-open"); setPage(key); setQuery(""); setPageIndex(0); setError(""); setFilteredProducts(null); };

  async function login(event) {
    event.preventDefault(); setBusy(true); setError("");
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    try {
      await loadCsrf();
      const info = await api("/api/auth/login", { method: "POST", body: JSON.stringify(Object.fromEntries(form)) });
      setAccount(info); formElement.reset(); setPage("dashboard");
      await refreshData(); notify("Đăng nhập thành công");
    } catch (e) { setError(e.message || "Đăng nhập thất bại"); }
    finally { setBusy(false); }
  }

  async function logout() {
    try { await api("/api/auth/logout", { method: "POST" }); } catch { /* Session may have expired already. */ }
    setAccount(null); setPage("dashboard"); setData({ categories: [], products: [], customers: [], orders: [], details: [], users: [] });
    setMetrics(null); setRevenueReport([]); setCart({}); setError("");
    try { await loadCsrf(); } catch { /* Login can request a fresh CSRF token. */ }
  }

  function openEditor(key, row = null) {
    setError("");
    const value = row ? { ...row } : {};
    if (key === "orders" && !row) { value.orderDate = localDateTime(); value.status = "Chờ xác nhận"; value.totalAmount = 0; }
    if (key === "products" && !row) { value.status = "Đang bán"; value.minStock = 5; }
    if (key === "details" && !row) value.quantity = 1;
    if (key === "users" && !row) { value.role = "STAFF"; value.enabled = true; }
    if (key === "users") value.password = "";
    setModal({ key, row, value });
  }

  async function saveEntity(event) {
    event.preventDefault();
    if (!modal) return;
    setBusy(true); setError("");
    const config = resources[modal.key];
    const form = new FormData(event.currentTarget);
    const body = {};
    config.fields.forEach(([key, , type]) => {
      let value = form.get(key);
      if (type === "number" || ["category", "customer", "product", "order"].includes(type)) value = value === "" ? null : Number(value);
      else if (type === "enabled") value = value === "true";
      else if (value === "") value = null;
      body[key] = value;
    });
    try {
      const endpoint = `${config.url}${modal.row ? `/${modal.row.id}` : ""}`;
      await api(endpoint, { method: modal.row ? "PUT" : "POST", body: JSON.stringify(body) });
      setModal(null); notify(modal.row ? "Đã cập nhật dữ liệu" : "Đã thêm dữ liệu"); await refreshData();
    } catch (e) { setError(e.message || "Không lưu được. Hãy kiểm tra thông tin nhập."); }
    finally { setBusy(false); }
  }

  async function deleteEntity(key, row) {
    if (!window.confirm(`Bạn muốn xóa ${resources[key].label.toLowerCase()} này?`)) return;
    setBusy(true);
    try { await api(`${resources[key].url}/${row.id}`, { method: "DELETE" }); notify("Đã xóa dữ liệu"); await refreshData(); }
    catch (e) { setError(e.message || "Không thể xóa dữ liệu do còn liên kết nghiệp vụ."); }
    finally { setBusy(false); }
  }

  async function uploadProductImage(product, file) {
    const body = new FormData();
    body.append("image", file);
    setBusy(true);
    setError("");
    try {
      await api(`/api/products/${product.id}/image`, { method: "POST", body });
      notify(`Đã tải ảnh cho ${product.name}`);
      await refreshData();
    } catch (e) { setError(e.message || "Không tải được ảnh sản phẩm."); }
    finally { setBusy(false); }
  }

  async function adjustStock(event) {
    event.preventDefault();
    if (!stockProduct) return;
    const form = new FormData(event.currentTarget);
    setBusy(true); setError("");
    try {
      await api(`/api/products/${stockProduct.id}/stock-adjustments`, {
        method: "POST",
        body: JSON.stringify({ quantityChange: Number(form.get("quantityChange")), reason: form.get("reason") }),
      });
      setStockProduct(null);
      notify("Đã cập nhật tồn kho và lưu lịch sử.");
      await refreshData();
    } catch (e) { setError(e.message || "Không thể điều chỉnh tồn kho."); }
    finally { setBusy(false); }
  }

  async function openStockHistory(product) {
    setBusy(true); setError("");
    try {
      const movements = await api(`/api/products/${product.id}/stock-movements`);
      setStockHistory({ product, movements });
    } catch (e) { setError(e.message || "Không tải được lịch sử tồn kho."); }
    finally { setBusy(false); }
  }

  async function checkout() {
    const items = Object.entries(cart).filter(([, quantity]) => quantity > 0).map(([productId, quantity]) => ({ productId: Number(productId), quantity: Number(quantity) }));
    if (!saleCustomer || !items.length) { notify("Chọn khách hàng và thêm sản phẩm trước khi tạo đơn"); return; }
    setBusy(true);
    try {
      const result = await api("/api/checkout", { method: "POST", body: JSON.stringify({ customerId: Number(saleCustomer), shippingAddress: saleAddress, paymentMethod, items }) });
      setCart({}); notify(`Đã tạo đơn #${result.order.id} · ${money(result.order.totalAmount)}`);
      if (result.paymentNotice) setTimeout(() => notify(result.paymentNotice), 2300);
      await refreshData();
    } catch (e) { notify(e.message || "Không tạo được đơn hàng"); }
    finally { setBusy(false); }
  }

  const matchingRows = useMemo(() => {
    if (page === "products" && filteredProducts) return filteredProducts.items || [];
    const list = data[page] || [];
    const q = query.trim().toLocaleLowerCase("vi");
    return list.filter((item) => {
      const matchesQuery = !q || JSON.stringify(item).toLocaleLowerCase("vi").includes(q);
      const matchesStatus = page !== "products" || statusFilter === "all" || (statusFilter === "Sắp hết" ? Number(item.quantity) <= Number(item.minStock ?? 5) : item.status === statusFilter);
      const matchesCategory = page !== "products" || !categoryFilter || String(item.categoryId) === categoryFilter;
      const matchesMin = page !== "products" || !minPrice || Number(item.price) >= Number(minPrice);
      const matchesMax = page !== "products" || !maxPrice || Number(item.price) <= Number(maxPrice);
      return matchesQuery && matchesStatus && matchesCategory && matchesMin && matchesMax;
    });
  }, [data, page, query, filteredProducts, statusFilter, categoryFilter, minPrice, maxPrice]);
  const totalRows = page === "products" && filteredProducts ? filteredProducts.total : matchingRows.length;
  const visibleRows = page === "products" && filteredProducts
    ? matchingRows
    : matchingRows.slice(pageIndex * pageSize, (pageIndex + 1) * pageSize);

  const heading = page === "dashboard" ? "Tổng quan" : page === "checkout" ? "Bán hàng" : resources[page]?.label;
  const lowStock = data.products.filter((product) => Number(product.quantity) <= Number(product.minStock ?? 5) && product.status !== "Ngừng bán");

  if (!account) return <LoginPage onSubmit={login} error={error} busy={busy} />;

  return (
    <main className="app-shell">
      <aside className="sidebar">
        <div className="brand"><span className="brand-mark">S</span><span>SPORTIVA<span className="brand-dot">.</span><small>QUẢN LÝ CỬA HÀNG</small></span></div>
        <div className="nav-label">QUẢN LÝ</div>
        <nav className="nav-list" aria-label="Điều hướng chính">
          {navItems.filter((item) => item.key !== "users" || isAdmin).map((item) => <button key={item.key} className={`nav-button ${page === item.key ? "active" : ""}`} onClick={() => changePage(item.key)}><span className="nav-icon">{item.icon}</span>{item.label}</button>)}
        </nav>
        <div className="sidebar-bottom"><div className="avatar">{account.username.slice(0, 2).toUpperCase()}</div><div className="profile-copy"><strong>{account.username}</strong><span>{isAdmin ? "Quản trị viên" : "Nhân viên bán hàng"}</span></div><button className="logout-button" onClick={logout} title="Đăng xuất">↗</button></div>
      </aside>
      <section className="main-area">
        <header className="topbar"><button className="mobile-menu" aria-label="Mở menu" onClick={() => document.body.classList.toggle("menu-open")}>☰</button><div className="breadcrumbs">Cửa hàng <span>/</span> <strong>{heading}</strong></div><div className={`top-status ${backendStatus}`} role="status"><i /> {backendStatus === "up" ? "Backend hoạt động" : backendStatus === "down" ? "Backend mất kết nối" : "Đang kiểm tra backend"}</div></header>
        <div className="content-wrap">
          <div className="page-heading"><div><div className="eyebrow">SPORTIVA · QUẢN LÝ CỬA HÀNG</div><h1>{heading}</h1><p>{page === "dashboard" ? "Theo dõi hoạt động kinh doanh của cửa hàng thể thao." : page === "checkout" ? "Tạo đơn hàng, cập nhật tồn kho và chọn phương thức thanh toán." : `Quản lý thông tin ${heading.toLowerCase()} trong hệ thống.`}</p></div>{page !== "dashboard" && page !== "checkout" && isAdmin && <button className="button primary" onClick={() => openEditor(page)}>＋ <span>Thêm mới</span></button>}</div>
          {error && <div className="alert error-alert" role="alert"><span>!</span>{error}<button onClick={() => setError("")} aria-label="Đóng thông báo">×</button></div>}
          {page === "dashboard" && <Dashboard data={data} metrics={metrics} revenueReport={revenueReport} lowStock={lowStock} loading={loading} onOpenProducts={() => changePage("products")} />}
          {page === "checkout" && <Checkout data={data} cart={cart} setCart={setCart} saleCustomer={saleCustomer} setSaleCustomer={setSaleCustomer} saleAddress={saleAddress} setSaleAddress={setSaleAddress} paymentMethod={paymentMethod} setPaymentMethod={setPaymentMethod} saleQuery={saleQuery} setSaleQuery={setSaleQuery} busy={busy} onCheckout={checkout} notify={notify} />}
          {resources[page] && <section className="panel">
            <div className="panel-header"><div><h2>{page === "products" ? "Danh sách sản phẩm" : `Danh sách ${heading.toLowerCase()}`}</h2><span className="panel-caption">Dữ liệu đồng bộ trực tiếp với MariaDB</span></div><div className="table-tools"><input className="search-input" value={query} onChange={(e) => { setQuery(e.target.value); setPageIndex(0); }} placeholder="⌕ Tìm kiếm..." aria-label="Tìm kiếm" />
              {page === "products" && <><select className="select-filter" aria-label="Lọc trạng thái" value={statusFilter} onChange={(e) => { setStatusFilter(e.target.value); setPageIndex(0); }}><option value="all">Mọi trạng thái</option>{statusOptions.productStatus.map((s) => <option key={s}>{s}</option>)}</select><select className="select-filter" aria-label="Lọc danh mục" value={categoryFilter} onChange={(e) => { setCategoryFilter(e.target.value); setPageIndex(0); }}><option value="">Mọi danh mục</option>{data.categories.map((c) => <option value={c.id} key={c.id}>{c.name}</option>)}</select><input className="small-filter" aria-label="Giá từ" type="number" min="0" placeholder="Giá từ" value={minPrice} onChange={(e) => { setMinPrice(e.target.value); setPageIndex(0); }} /><input className="small-filter" aria-label="Giá đến" type="number" min="0" placeholder="Giá đến" value={maxPrice} onChange={(e) => { setMaxPrice(e.target.value); setPageIndex(0); }} /></>}
            </div></div>
            <div className="table-scroll"><table><thead><tr>{resources[page].cols.map(([key, label]) => <th key={key}>{label}</th>)}{(isAdmin || page === "products") && <th className="action-head">Thao tác</th>}</tr></thead><tbody>
              {loading ? <tr><td className="empty-cell" colSpan={resources[page].cols.length + (isAdmin || page === "products" ? 1 : 0)}>Đang tải dữ liệu…</td></tr> : visibleRows.length ? visibleRows.map((row) => <tr key={row.id}>{resources[page].cols.map(([key]) => <td key={key}>{renderCell(key, row, page, data)}</td>)}{(isAdmin || page === "products") && <td><div className="row-actions">{page === "products" && <><button className="icon-button" title="Điều chỉnh tồn kho" aria-label={`Điều chỉnh tồn kho ${row.name}`} disabled={busy} onClick={() => setStockProduct(row)}>±</button><button className="icon-button" title="Xem lịch sử tồn kho" aria-label={`Lịch sử tồn kho ${row.name}`} disabled={busy} onClick={() => openStockHistory(row)}>◷</button>{isAdmin && <label className="icon-button upload-image-button" title="Tải ảnh sản phẩm">▧<input type="file" accept="image/png,image/jpeg,image/gif,image/webp" disabled={busy} onChange={(event) => { const file = event.target.files?.[0]; if (file) uploadProductImage(row, file); event.target.value = ""; }} /></label>}</>}{isAdmin && <><button className="icon-button" title="Chỉnh sửa" onClick={() => openEditor(page, row)}>✎</button><button className="icon-button danger-icon" title="Xóa" onClick={() => deleteEntity(page, row)}>⌫</button></>}</div></td>}</tr>) : <tr><td className="empty-cell" colSpan={resources[page].cols.length + (isAdmin || page === "products" ? 1 : 0)}>{query || categoryFilter || minPrice || maxPrice || statusFilter !== "all" ? "Không tìm thấy dữ liệu phù hợp." : "Chưa có dữ liệu. Nhấn Thêm mới để bắt đầu."}</td></tr>}
            </tbody></table></div>
            <div className="panel-footer"><span>Hiển thị {visibleRows.length} / {totalRows} mục</span><div className="pagination-controls"><label>Hiển thị <select value={pageSize} onChange={(e) => { setPageSize(Number(e.target.value)); setPageIndex(0); }}><option>10</option><option>25</option><option>50</option></select></label><button disabled={pageIndex <= 0} onClick={() => setPageIndex((n) => Math.max(0, n - 1))}>‹</button><span>Trang {totalRows ? pageIndex + 1 : 0} / {Math.ceil(totalRows / pageSize)}</span><button disabled={(pageIndex + 1) * pageSize >= totalRows} onClick={() => setPageIndex((n) => n + 1)}>›</button></div></div>
          </section>}
          <footer className="app-footer">SPORTIVA ADMIN <span>·</span> NEXT.JS + SPRING BOOT <span>·</span> MARIADB</footer>
        </div>
      </section>
      {modal && <EntityModal modal={modal} data={data} busy={busy} error={error} onClose={() => { setModal(null); setError(""); }} onSave={saveEntity} />}
      {stockProduct && <StockAdjustmentModal product={stockProduct} busy={busy} error={error} onClose={() => { setStockProduct(null); setError(""); }} onSave={adjustStock} />}
      {stockHistory && <StockHistoryModal product={stockHistory.product} movements={stockHistory.movements} onClose={() => setStockHistory(null)} />}
      {toast && <div className="toast" role="status">{toast}</div>}
    </main>
  );
}

function LoginPage({ onSubmit, error, busy }) {
  return <main className="login-page"><div className="login-card"><div className="login-brand"><span className="brand-mark">S</span><div><strong>SPORTIVA<span className="brand-dot">.</span></strong><small>QUẢN LÝ CỬA HÀNG</small></div></div><div className="eyebrow">XIN CHÀO TRỞ LẠI</div><h1>Đăng nhập hệ thống</h1><p>Đăng nhập để quản lý cửa hàng thể thao của bạn.</p><form onSubmit={onSubmit} className="login-form"><label>Tên đăng nhập<input name="username" autoComplete="username" required maxLength={80} placeholder="Nhập tên đăng nhập" /></label><label>Mật khẩu<input name="password" type="password" autoComplete="current-password" required maxLength={200} placeholder="Nhập mật khẩu" /></label>{error && <div className="form-error" role="alert">{error}</div>}<button className="button primary login-submit" disabled={busy}>{busy ? "Đang đăng nhập…" : "Đăng nhập →"}</button></form><div className="demo-box"><strong>Tài khoản dùng thử</strong><span>Quản trị: <b>admin</b> / <b>Admin@123</b></span><span>Nhân viên: <b>staff</b> / <b>Staff@123</b></span></div><div className="login-foot">Next.js · Spring Boot · MariaDB</div></div></main>;
}

function Dashboard({ data, metrics, revenueReport, lowStock, loading, onOpenProducts }) {
  const cards = [
    ["Doanh thu đơn hàng", money(metrics?.revenue ?? 0), "₫", "Tổng doanh thu"],
    ["Đơn hàng", metrics?.orders ?? data.orders.length, "▤", "Đang quản lý"],
    ["Chờ xử lý", metrics?.pendingOrders ?? 0, "◷", "Cần theo dõi"],
    ["Sắp hết hàng", metrics?.lowStock ?? lowStock.length, "⚠", "Kiểm tra tồn kho"],
    ["Sản phẩm", metrics?.products ?? data.products.length, "◈", "Đang quản lý"],
    ["Khách hàng", metrics?.customers ?? data.customers.length, "♙", "Đang quản lý"],
    ["Giá trị tồn kho", money(metrics?.inventoryValue ?? 0), "₫", "Theo giá bán hiện tại"],
  ];
  const latest = [...data.products].slice(-6).reverse();
  const peakRevenue = Math.max(1, ...revenueReport.map((item) => Number(item.revenue)));
  const periodRevenue = revenueReport.reduce((sum, item) => sum + Number(item.revenue), 0);
  return <>
    <div className="stats-grid">{cards.map(([label, value, icon, hint]) => <article className="stat-card" key={label}><div className="stat-top">{label}<span className="stat-icon">{icon}</span></div><strong>{loading ? "…" : value}</strong><small>{hint}</small></article>)}</div>
    {lowStock.length > 0 && <div className="alert stock-alert"><span>⚠</span><div><strong>{lowStock.length} sản phẩm sắp hết hàng</strong><small>{lowStock.slice(0, 3).map((p) => p.name).join(" · ")}</small></div></div>}
    <section className="panel revenue-panel">
      <div className="panel-header">
        <div><h2>Doanh thu 30 ngày gần nhất</h2><span className="panel-caption">Không tính các đơn đã hủy · Tổng {money(periodRevenue)}</span></div>
        <a className="button subtle" href="/api/dashboard/revenue.csv?days=30" download>Tải báo cáo CSV ↓</a>
      </div>
      {loading ? <div className="empty-box">Đang tải báo cáo…</div> : revenueReport.length ? <div className="revenue-chart" role="img" aria-label="Biểu đồ doanh thu theo ngày trong 30 ngày gần nhất">
        {revenueReport.map((item, index) => {
          const amount = Number(item.revenue);
          const barHeight = amount > 0 ? Math.max(3, amount / peakRevenue * 100) : 0;
          return <div className="revenue-day" key={item.date} title={`${item.date}: ${money(amount)} · ${item.orders} đơn`}>
            <div className="revenue-bar-track"><span className="revenue-bar" style={{ height: `${barHeight}%` }} /></div>
            <small>{index % 5 === 0 || index === revenueReport.length - 1 ? item.date.slice(8) : ""}</small>
          </div>;
        })}
      </div> : <div className="empty-box">Chưa có dữ liệu doanh thu trong khoảng thời gian này.</div>}
    </section>
    <section className="panel"><div className="panel-header"><div><h2>Sản phẩm mới cập nhật</h2><span className="panel-caption">Hàng hóa trong kho thể thao</span></div><button className="button subtle" onClick={onOpenProducts}>Xem tất cả ↗</button></div><div className="table-scroll"><table><thead><tr><th>Sản phẩm</th><th>Danh mục</th><th>Thương hiệu</th><th>Giá bán</th><th>Tồn kho</th><th>Trạng thái</th></tr></thead><tbody>{loading ? <tr><td className="empty-cell" colSpan="6">Đang tải dữ liệu…</td></tr> : latest.length ? latest.map((product) => <tr key={product.id}><td><ProductName product={product} /></td><td>{data.categories.find((c) => c.id === product.categoryId)?.name || "—"}</td><td>{product.brand || "—"}</td><td>{money(product.price)}</td><td>{product.quantity}</td><td><StatusBadge value={product.status} quantity={product.quantity} minStock={product.minStock} /></td></tr>) : <tr><td className="empty-cell" colSpan="6">Chưa có sản phẩm. Thêm mặt hàng đầu tiên để bắt đầu.</td></tr>}</tbody></table></div><div className="panel-footer">{data.products.length} sản phẩm trong hệ thống</div></section>
  </>;
}

function Checkout({ data, cart, setCart, saleCustomer, setSaleCustomer, saleAddress, setSaleAddress, paymentMethod, setPaymentMethod, saleQuery, setSaleQuery, busy, onCheckout, notify }) {
  const products = data.products.filter((p) => p.status !== "Ngừng bán" && `${p.name} ${p.brand || ""}`.toLocaleLowerCase("vi").includes(saleQuery.toLocaleLowerCase("vi")));
  const items = Object.entries(cart).map(([id, quantity]) => ({ product: data.products.find((p) => p.id === Number(id)), quantity: Number(quantity) })).filter((x) => x.product);
  const total = items.reduce((sum, item) => sum + Number(item.product.price) * item.quantity, 0);
  function setQuantity(product, next) {
    if (!Number.isInteger(next) || next < 0 || next > Number(product.quantity)) { notify(`Số lượng ${product.name} tối đa là ${product.quantity}`); return; }
    setCart((old) => { const copy = { ...old }; if (next === 0) delete copy[product.id]; else copy[product.id] = next; return copy; });
  }
  function chooseCustomer(id) { setSaleCustomer(id); const customer = data.customers.find((c) => String(c.id) === id); if (customer) setSaleAddress(customer.address || ""); }
  return <>
    <div className="checkout-grid">
      <section className="panel checkout-products">
        <div className="panel-header">
          <div><h2>Sản phẩm có thể bán</h2><span className="panel-caption">Chọn hàng để thêm vào giỏ</span></div>
          <input className="search-input" value={saleQuery} onChange={(e) => setSaleQuery(e.target.value)} placeholder="Tìm sản phẩm / thương hiệu" />
        </div>
        <div className="sale-products">
          {products.length ? products.map((product) => <article className="sale-card" key={product.id}>
            <div className="sale-image">{product.imageUrl ? <img src={product.imageUrl} alt={product.name} /> : "👟"}</div>
            <div className="sale-info">
              <strong>{product.name}</strong><small>{product.brand || "Chưa rõ thương hiệu"} · Còn {product.quantity}</small>
              <b>{money(product.price)}</b>
              <button className="button subtle" disabled={product.quantity < 1} onClick={() => setQuantity(product, Number(cart[product.id] || 0) + 1)}>＋ Thêm vào giỏ</button>
            </div>
          </article>) : <div className="empty-box">Không tìm thấy sản phẩm phù hợp.</div>}
        </div>
      </section>
      <section className="panel cart-panel">
        <div className="panel-header"><div><h2>Giỏ hàng</h2><span className="panel-caption">{items.length} mặt hàng</span></div></div>
        {items.length ? <div className="cart-list">{items.map(({ product, quantity }) => <div className="cart-line" key={product.id}>
          <div><strong>{product.name}</strong><small>{money(product.price)} / sản phẩm</small></div>
          <input aria-label={`Số lượng ${product.name}`} type="number" min="0" max={product.quantity} value={quantity} onChange={(e) => setQuantity(product, Number(e.target.value))} />
          <button className="icon-button danger-icon" onClick={() => setQuantity(product, 0)} aria-label="Xóa">×</button>
        </div>)}</div> : <div className="empty-box compact">Chưa có mặt hàng trong giỏ.</div>}
        <div className="cart-total"><span>Tổng tiền</span><strong>{money(total)}</strong></div>
        <div className="checkout-form">
          <label>Khách hàng<select value={saleCustomer} onChange={(e) => chooseCustomer(e.target.value)} required>
            <option value="">Chọn khách hàng</option>{data.customers.map((customer) => <option key={customer.id} value={customer.id}>{customer.fullName}{customer.phone ? ` · ${customer.phone}` : ""}</option>)}
          </select></label>
          <label>Địa chỉ giao hàng<input value={saleAddress} onChange={(e) => setSaleAddress(e.target.value)} maxLength={500} placeholder="Để trống dùng địa chỉ khách hàng" /></label>
          <label>Phương thức thanh toán<select value={paymentMethod} onChange={(e) => setPaymentMethod(e.target.value)}>
            <option value="COD">COD — thu tiền khi giao</option><option value="BANK_TRANSFER">Chuyển khoản — chờ xác nhận</option>
            <option value="MOMO_DEMO">MoMo (mô phỏng)</option><option value="CARD_DEMO">Thẻ (mô phỏng)</option>
          </select></label>
          <p className="note">MoMo và thẻ chỉ mô phỏng quy trình thanh toán của đồ án; không thu tiền thật.</p>
          <button className="button primary checkout-button" disabled={busy || !items.length || !data.customers.length} onClick={onCheckout}>{busy ? "Đang xử lý…" : "Tạo đơn hàng"}</button>
        </div>
      </section>
    </div>
    {lowStockMessage(data.products) && <div className="alert stock-alert checkout-stock">⚠ {lowStockMessage(data.products)}</div>}
  </>;
}

function StockAdjustmentModal({ product, busy, error, onClose, onSave }) {
  return <div className="modal-backdrop" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
    <section className="modal-card" role="dialog" aria-modal="true" aria-labelledby="stock-modal-title">
      <header className="modal-header"><div><span className="eyebrow">QUẢN LÝ TỒN KHO</span><h2 id="stock-modal-title">Điều chỉnh {product.name}</h2></div><button className="icon-button" onClick={onClose} aria-label="Đóng">×</button></header>
      <form onSubmit={onSave}>
        <div className="form-grid">
          <label className="form-field">Thay đổi số lượng<input name="quantityChange" type="number" step="1" required autoFocus aria-describedby="stock-change-help" /><small id="stock-change-help">Số dương để nhập hàng, số âm để xuất hàng. Hiện có {product.quantity} sản phẩm.</small></label>
          <label className="form-field full">Lý do điều chỉnh<input name="reason" type="text" maxLength="250" required /></label>
        </div>
        {error && <div className="form-error" role="alert">{error}</div>}
        <footer className="modal-actions"><button type="button" className="button subtle" onClick={onClose}>Hủy</button><button type="submit" className="button primary" disabled={busy}>{busy ? "Đang lưu…" : "Cập nhật tồn kho"}</button></footer>
      </form>
    </section>
  </div>;
}

function StockHistoryModal({ product, movements, onClose }) {
  return <div className="modal-backdrop" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
    <section className="modal-card" role="dialog" aria-modal="true" aria-labelledby="stock-history-title">
      <header className="modal-header"><div><span className="eyebrow">TỐI ĐA 100 LẦN GẦN NHẤT</span><h2 id="stock-history-title">Lịch sử tồn kho · {product.name}</h2></div><button className="icon-button" onClick={onClose} aria-label="Đóng">×</button></header>
      <div className="table-scroll"><table><thead><tr><th>Thời gian</th><th>Loại</th><th>Thay đổi</th><th>Lý do</th></tr></thead><tbody>
        {movements.length ? movements.map((movement) => <tr key={movement.id}><td>{new Date(movement.createdAt).toLocaleString("vi-VN")}</td><td>{movement.movementType}</td><td>{movement.quantityChange > 0 ? "+" : ""}{movement.quantityChange}</td><td>{movement.reason}</td></tr>) : <tr><td className="empty-cell" colSpan="4">Chưa có lịch sử tồn kho.</td></tr>}
      </tbody></table></div>
      <footer className="modal-actions"><button type="button" className="button subtle" onClick={onClose}>Đóng</button></footer>
    </section>
  </div>;
}

function EntityModal({ modal, data, busy, error, onClose, onSave }) {
  const config = resources[modal.key];
  return <div className="modal-backdrop" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}><section className="modal-card" role="dialog" aria-modal="true" aria-labelledby="modal-title"><header className="modal-header"><div><span className="eyebrow">{modal.row ? "CẬP NHẬT THÔNG TIN" : "THÊM DỮ LIỆU MỚI"}</span><h2 id="modal-title">{modal.row ? "Chỉnh sửa " : "Thêm "}{config.label.toLowerCase()}</h2></div><button className="icon-button" onClick={onClose} aria-label="Đóng">×</button></header><form onSubmit={onSave}><div className="form-grid">{config.fields.map(([key, label, type, required]) => <label className={type === "textarea" ? "form-field full" : "form-field"} key={key}>{label}{renderInput(key, label, type, required && !(key === "password" && modal.row), modal.value[key], data)}</label>)}</div>{error && <div className="form-error" role="alert">{error}</div>}<footer className="modal-actions"><button type="button" className="button subtle" onClick={onClose}>Hủy</button><button type="submit" className="button primary" disabled={busy}>{busy ? "Đang lưu…" : "Lưu thông tin"}</button></footer></form></section></div>;
}

function renderInput(key, label, type, required, value, data) {
  const common = { name: key, required: Boolean(required), defaultValue: value ?? "" };
  if (type === "textarea") return <textarea {...common} rows="3" maxLength={key === "address" || key === "shippingAddress" ? 500 : 1000} />;
  if (type === "productStatus" || type === "orderStatus") return <select {...common}>{statusOptions[type].map((option) => <option key={option}>{option}</option>)}</select>;
  if (type === "userRole") return <select {...common}><option value="STAFF">Nhân viên</option><option value="ADMIN">Quản trị viên</option></select>;
  if (type === "enabled") return <select {...common} defaultValue={String(value ?? true)}><option value="true">Đang hoạt động</option><option value="false">Đã khóa</option></select>;
  const choices = type === "category" ? data.categories.map((x) => [x.id, x.name]) : type === "customer" ? data.customers.map((x) => [x.id, x.fullName]) : type === "product" ? data.products.map((x) => [x.id, x.name]) : type === "order" ? data.orders.map((x) => [x.id, `Đơn #${x.id}`]) : null;
  if (choices) return <select {...common}><option value="">Chọn {label.toLowerCase()}</option>{choices.map(([id, text]) => <option value={id} key={id}>{text}</option>)}</select>;
  const isPassword = type === "password";
  return <input {...common} type={type} autoComplete={isPassword ? "new-password" : undefined} minLength={isPassword ? 12 : undefined} min={type === "number" ? "0" : undefined} step={type === "number" ? (key === "quantity" || key === "minStock" ? "1" : "any") : undefined} maxLength={type === "text" || isPassword ? 200 : undefined} />;
}

function renderCell(key, row, page, data) {
  if (key === "name" && page === "products") return <ProductName product={row} />;
  if (key === "enabled") return <StatusBadge value={row[key] ? "Đang hoạt động" : "Đã khóa"} />;
  if (key === "role") return row[key] === "ADMIN" ? "Quản trị viên" : "Nhân viên";
  if (key === "categoryId") return data.categories.find((item) => item.id === row[key])?.name || `#${row[key] || "—"}`;
  if (key === "customerId") return data.customers.find((item) => item.id === row[key])?.fullName || `#${row[key] || "—"}`;
  if (key === "productId") return data.products.find((item) => item.id === row[key])?.name || `#${row[key] || "—"}`;
  if (key === "orderId") return `#${row[key] || "—"}`;
  if (["price", "totalAmount", "unitPrice", "subtotal"].includes(key)) return money(row[key]);
  if (key === "status") return <StatusBadge value={row[key]} quantity={row.quantity} minStock={row.minStock} />;
  if (key === "orderDate") return row[key] ? new Date(row[key]).toLocaleDateString("vi-VN") : "—";
  return row[key] ?? "—";
}
function ProductName({ product }) { return <div className="product-name"><span className="product-thumb">{product.imageUrl ? <img src={product.imageUrl} alt="" /> : "👟"}</span><span><strong>{product.name}</strong><small>SKU-{String(product.id).padStart(4, "0")}</small></span></div>; }
function StatusBadge({ value, quantity, minStock = 5 }) {
  const low = value === "Sắp hết" || (quantity != null && Number(quantity) <= Number(minStock ?? 5));
  const text = low && value === "Đang bán" ? "Sắp hết" : value || "Đang bán";
  return <span className={`badge ${low ? "warning" : value === "Ngừng bán" || value === "Đã hủy" ? "muted-badge" : "success"}`}>{text}</span>;
}
function lowStockMessage(products) { const low = products.filter((p) => Number(p.quantity) <= Number(p.minStock ?? 5) && p.status !== "Ngừng bán"); return low.length ? `${low.length} mặt hàng sắp hết: ${low.slice(0, 4).map((p) => p.name).join(", ")}` : ""; }
