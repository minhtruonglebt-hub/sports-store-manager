"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Icon, resources, orderStatuses, productStatuses, paymentLabels, parseList, money, shortDate, fullDate, localDateTime, isLow, canConfirmPayment, paymentDone } from "./lib";

const emptyData = { categories: [], products: [], customers: [], orders: [], details: [], users: [] };
const nav = [
  { group: "Tổng quan", items: [{ key: "dashboard", label: "Bảng điều khiển", icon: "home" }] },
  { group: "Bán hàng", items: [{ key: "checkout", label: "Bán tại quầy", icon: "cart" }, { key: "online", label: "Đơn khách đặt", icon: "globe" }, { key: "orders", label: "Đơn hàng", icon: "receipt" }, { key: "details", label: "Chi tiết đơn", icon: "list" }] },
  { group: "Kho & khách hàng", items: [{ key: "products", label: "Sản phẩm", icon: "box" }, { key: "categories", label: "Danh mục", icon: "tag" }, { key: "customers", label: "Khách hàng", icon: "users" }] },
  { group: "Hệ thống", items: [{ key: "users", label: "Tài khoản", icon: "shield", admin: true }] },
];
const titles = { dashboard: "Bảng điều khiển", checkout: "Bán tại quầy", online: "Đơn khách đặt", ...Object.fromEntries(Object.entries(resources).map(([k, v]) => [k, v.label])) };

export default function Home() {
  const [account, setAccount] = useState(null);
  const [booting, setBooting] = useState(true);
  const [backend, setBackend] = useState("checking");
  const [page, setPage] = useState("dashboard");
  const [data, setData] = useState(emptyData);
  const [online, setOnline] = useState([]);
  const [metrics, setMetrics] = useState(null);
  const [revenue, setRevenue] = useState([]);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [toast, setToast] = useState("");
  const [error, setError] = useState("");
  const [modal, setModal] = useState(null);
  const [stockProduct, setStockProduct] = useState(null);
  const [stockHistory, setStockHistory] = useState(null);
  const [confirm, setConfirm] = useState(null);
  const [menuOpen, setMenuOpen] = useState(false);
  const [reloadTick, setReloadTick] = useState(0);
  const csrf = useRef("");
  const toastTimer = useRef(null);
  const isAdmin = account?.roles?.includes("ADMIN");

  const notify = useCallback((m) => { setToast(m); clearTimeout(toastTimer.current); toastTimer.current = setTimeout(() => setToast(""), 3200); }, []);

  const api = useCallback(async (url, options = {}) => {
    const method = (options.method || "GET").toUpperCase();
    const headers = new Headers(options.headers || {});
    const isForm = typeof FormData !== "undefined" && options.body instanceof FormData;
    if (options.body && !isForm && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
    if (!["GET", "HEAD", "OPTIONS", "TRACE"].includes(method) && csrf.current) headers.set("X-XSRF-TOKEN", csrf.current);
    const res = await fetch(url, { credentials: "same-origin", ...options, headers });
    const text = await res.text();
    let payload = null;
    if (text) { try { payload = JSON.parse(text); } catch { payload = { message: text }; } }
    if (!res.ok) {
      if (res.status === 401 && !url.includes("/api/auth/login")) setAccount(null);
      throw new Error(payload?.message || `Lỗi máy chủ (${res.status})`);
    }
    return payload;
  }, []);

  const loadCsrf = useCallback(async () => { csrf.current = (await api("/api/auth/csrf")).token; }, [api]);

  // Backend heartbeat
  useEffect(() => {
    let alive = true;
    const check = async () => {
      try { const r = await fetch("/api/health", { cache: "no-store" }); const j = r.ok ? await r.json() : null; if (alive) setBackend(j?.status === "UP" ? "up" : "down"); }
      catch { if (alive) setBackend("down"); }
    };
    check(); const t = setInterval(check, 15000);
    return () => { alive = false; clearInterval(t); };
  }, []);

  // Loads everything for a given account. Takes the role explicitly so it never needs to re-run when `account` changes.
  const refreshData = useCallback(async (admin) => {
    setLoading(true); setError("");
    try {
      const keys = Object.keys(resources).filter((k) => k !== "users" || admin);
      const [lists, summary, report, onlineOrders] = await Promise.all([
        Promise.all(keys.map(async (k) => [k, parseList(await api(resources[k].url))])),
        api("/api/dashboard"), api("/api/dashboard/revenue?days=30"), api("/api/orders/online"),
      ]);
      setData({ ...emptyData, ...Object.fromEntries(lists) });
      setMetrics(summary); setRevenue(report.items || []); setOnline(onlineOrders || []);
      setReloadTick((n) => n + 1);
    } catch (e) { setError(e.message || "Không tải được dữ liệu."); }
    finally { setLoading(false); }
  }, [api]);

  // One-shot session restore (no dependency on `account`, so no double fetch)
  useEffect(() => {
    let alive = true;
    (async () => {
      try {
        await loadCsrf();
        const me = await api("/api/auth/me");
        if (!alive) return;
        if (!me.roles?.some((r) => r === "ADMIN" || r === "STAFF")) { setAccount(null); return; }
        setAccount(me);
        await refreshData(me.roles.includes("ADMIN"));
      } catch { if (alive) setAccount(null); }
      finally { if (alive) setBooting(false); }
    })();
    return () => { alive = false; clearTimeout(toastTimer.current); };
  }, [api, loadCsrf, refreshData]);

  async function login(e) {
    e.preventDefault(); setBusy(true); setError("");
    const form = e.currentTarget; const body = Object.fromEntries(new FormData(form));
    try {
      await loadCsrf();
      const info = await api("/api/auth/login", { method: "POST", body: JSON.stringify(body) });
      if (!info.roles?.some((r) => r === "ADMIN" || r === "STAFF")) {
        await api("/api/auth/logout", { method: "POST" }).catch(() => {});
        throw new Error("Tài khoản này không có quyền vào trang quản trị.");
      }
      setAccount(info); form.reset(); setPage("dashboard");
      await loadCsrf();
      await refreshData(info.roles.includes("ADMIN")); notify("Đăng nhập thành công");
    } catch (err) { setError(err.message || "Đăng nhập thất bại"); }
    finally { setBusy(false); }
  }

  async function logout() {
    try { await api("/api/auth/logout", { method: "POST" }); } catch { /* session may already be gone */ }
    setAccount(null); setPage("dashboard"); setData(emptyData); setOnline([]); setMetrics(null); setRevenue([]); setError("");
    try { await loadCsrf(); } catch { /* login fetches a fresh token */ }
  }

  const reload = () => refreshData(isAdmin);
  const go = (key) => { setPage(key); setError(""); setMenuOpen(false); };

  async function run(task, okMessage) {
    setBusy(true); setError("");
    try { await task(); if (okMessage) notify(okMessage); await reload(); return true; }
    catch (e) { setError(e.message || "Có lỗi xảy ra."); return false; }
    finally { setBusy(false); }
  }

  const pendingOnline = online.filter((o) => o.status === "Chờ xác nhận").length;

  if (booting) return <div className="boot"><div className="spinner" /></div>;
  if (!account) return <Login onSubmit={login} error={error} busy={busy} backend={backend} />;

  return (
    <div className={`shell ${menuOpen ? "menu-open" : ""}`}>
      <aside className="side">
        <div className="logo"><span className="logo-mark"><Icon name="bolt" size={20} /></span><div><b>SPORTIVA</b><small>Quản lý cửa hàng</small></div></div>
        <nav aria-label="Điều hướng chính">
          {nav.map((g) => {
            const items = g.items.filter((i) => !i.admin || isAdmin);
            if (!items.length) return null;
            return <div key={g.group} className="nav-group"><div className="nav-title">{g.group}</div>
              {items.map((i) => <button key={i.key} className={`nav-item ${page === i.key ? "on" : ""}`} onClick={() => go(i.key)}>
                <Icon name={i.icon} /><span>{i.label}</span>
                {i.key === "online" && pendingOnline > 0 && <em className="count">{pendingOnline}</em>}
              </button>)}</div>;
          })}
        </nav>
        <div className="me"><div className="avatar">{account.username.slice(0, 2).toUpperCase()}</div><div><b>{account.username}</b><small>{isAdmin ? "Quản trị viên" : "Nhân viên"}</small></div><button onClick={logout} title="Đăng xuất" aria-label="Đăng xuất"><Icon name="out" /></button></div>
      </aside>
      <div className="scrim" onClick={() => setMenuOpen(false)} />
      <section className="main">
        <header className="top">
          <button className="burger" aria-label="Mở menu" onClick={() => setMenuOpen(true)}><Icon name="menu" /></button>
          <h1>{titles[page]}</h1>
          <div className={`pulse ${backend}`} role="status"><i />{backend === "up" ? "Hệ thống hoạt động" : backend === "down" ? "Mất kết nối máy chủ" : "Đang kiểm tra…"}</div>
        </header>
        <div className="content">
          {error && <div className="banner bad" role="alert"><Icon name="alert" /><span>{error}</span><button onClick={() => setError("")} aria-label="Đóng"><Icon name="x" size={16} /></button></div>}
          {page === "dashboard" && <Dashboard data={data} metrics={metrics} revenue={revenue} loading={loading} online={online} go={go} />}
          {page === "checkout" && <Checkout data={data} api={api} busy={busy} setBusy={setBusy} notify={notify} setError={setError} reload={reload} />}
          {page === "online" && <OnlineOrders orders={online} busy={busy} loading={loading}
            onStatus={(o, status) => status === "Đã hủy"
              ? setConfirm({ title: `Hủy đơn #${o.id}?`, text: "Tồn kho sẽ được hoàn lại và đơn đã hủy không thể mở lại.", danger: true, ok: "Hủy đơn", action: () => run(() => api(`/api/orders/${o.id}/status`, { method: "PUT", body: JSON.stringify({ status }) }), `Đã hủy đơn #${o.id}`) })
              : run(() => api(`/api/orders/${o.id}/status`, { method: "PUT", body: JSON.stringify({ status }) }), `Đơn #${o.id} → ${status}`)}
            onPay={(o) => run(() => api(`/api/orders/${o.id}/payment-confirmation`, { method: "POST" }), `Đã xác nhận thanh toán đơn #${o.id}`)} />}
          {resources[page] && <Table key={page} page={page} data={data} api={api} loading={loading} busy={busy} isAdmin={isAdmin} reloadTick={reloadTick}
            onAdd={() => openEditor(page)} onEdit={(row) => openEditor(page, row)}
            onDelete={(row) => setConfirm({ title: `Xóa ${resources[page].label.toLowerCase()}?`, text: "Thao tác này không thể hoàn tác.", danger: true, ok: "Xóa", action: () => run(() => api(`${resources[page].url}/${row.id}`, { method: "DELETE" }), "Đã xóa dữ liệu") })}
            onStock={setStockProduct} onHistory={async (p) => { try { setStockHistory({ product: p, movements: await api(`/api/products/${p.id}/stock-movements`) }); } catch (e) { setError(e.message); } }}
            onUpload={(p, file) => { const body = new FormData(); body.append("image", file); run(() => api(`/api/products/${p.id}/image`, { method: "POST", body }), `Đã tải ảnh cho ${p.name}`); }} />}
        </div>
      </section>
      {modal && <EntityModal modal={modal} data={data} busy={busy} error={error} onClose={() => { setModal(null); setError(""); }} onSave={saveEntity} />}
      {stockProduct && <StockModal product={stockProduct} busy={busy} error={error} onClose={() => { setStockProduct(null); setError(""); }} onSave={adjustStock} />}
      {stockHistory && <HistoryModal {...stockHistory} onClose={() => setStockHistory(null)} />}
      {confirm && <Confirm {...confirm} onClose={() => setConfirm(null)} />}
      {toast && <div className="toast" role="status">{toast}</div>}
    </div>
  );

  function openEditor(key, row = null) {
    setError("");
    const value = row ? { ...row } : {};
    if (key === "orders" && !row) Object.assign(value, { orderDate: localDateTime(), status: "Chờ xác nhận", totalAmount: 0 });
    if (key === "orders" && row?.orderDate) value.orderDate = String(row.orderDate).slice(0, 16);
    if (key === "products" && !row) Object.assign(value, { status: "Đang bán", minStock: 5 });
    if (key === "products" && row?.status === "Sắp hết") value.status = "Đang bán";
    if (key === "details" && !row) value.quantity = 1;
    if (key === "users" && !row) Object.assign(value, { role: "STAFF", enabled: true });
    if (key === "users") value.password = "";
    setModal({ key, row, value });
  }

  async function saveEntity(e) {
    e.preventDefault();
    const cfg = resources[modal.key]; const form = new FormData(e.currentTarget); const body = {};
    cfg.fields.forEach(([key, , type]) => {
      let v = form.get(key);
      if (type === "number" || ["category", "customer", "product", "order"].includes(type)) v = v === "" || v == null ? null : Number(v);
      else if (type === "enabled") v = v === "true";
      else if (v === "") v = null;
      body[key] = v;
    });
    if (modal.key === "orders" && body.orderDate?.length === 16) body.orderDate += ":00";
    const ok = await run(() => api(`${cfg.url}${modal.row ? `/${modal.row.id}` : ""}`, { method: modal.row ? "PUT" : "POST", body: JSON.stringify(body) }), modal.row ? "Đã cập nhật" : "Đã thêm mới");
    if (ok) setModal(null);
  }

  async function adjustStock(e) {
    e.preventDefault(); const f = new FormData(e.currentTarget);
    const ok = await run(() => api(`/api/products/${stockProduct.id}/stock-adjustments`, { method: "POST", body: JSON.stringify({ quantityChange: Number(f.get("quantityChange")), reason: f.get("reason") }) }), "Đã cập nhật tồn kho và lưu lịch sử");
    if (ok) setStockProduct(null);
  }
}

/* ---------- Login ---------- */
function Login({ onSubmit, error, busy, backend }) {
  return <main className="login">
    <section className="login-hero"><div className="logo light"><span className="logo-mark"><Icon name="bolt" size={20} /></span><div><b>SPORTIVA</b><small>Quản lý cửa hàng</small></div></div>
      <h2>Vận hành cửa hàng thể thao <em>nhanh, gọn, chính xác.</em></h2><p>Theo dõi tồn kho, xử lý đơn khách đặt và báo cáo doanh thu trong một nơi.</p>
      <ul><li><Icon name="check" size={16} />Cảnh báo sắp hết hàng theo từng sản phẩm</li><li><Icon name="check" size={16} />Xác nhận thanh toán và trạng thái giao hàng</li><li><Icon name="check" size={16} />Báo cáo doanh thu xuất CSV</li></ul></section>
    <section className="login-panel"><form onSubmit={onSubmit} className="login-form">
      <span className="eyebrow">Xin chào trở lại</span><h1>Đăng nhập</h1><p className="muted">Dùng tài khoản quản trị hoặc nhân viên.</p>
      <label>Tên đăng nhập<input name="username" autoComplete="username" required maxLength={80} placeholder="admin" /></label>
      <label>Mật khẩu<input name="password" type="password" autoComplete="current-password" required maxLength={200} placeholder="••••••••" /></label>
      {error && <div className="banner bad" role="alert"><Icon name="alert" /><span>{error}</span></div>}
      {backend === "down" && <div className="banner warn"><Icon name="alert" /><span>Chưa kết nối được máy chủ (cổng 8081). Hãy chạy Spring Boot trước.</span></div>}
      <button className="btn primary lg" disabled={busy}>{busy ? "Đang đăng nhập…" : "Đăng nhập"}</button>
      <div className="demo"><b>Tài khoản dùng thử</b><span>Quản trị: <code>admin</code> / <code>Admin@123</code></span><span>Nhân viên: <code>staff</code> / <code>Staff@123</code></span></div>
    </form></section>
  </main>;
}

/* ---------- Dashboard ---------- */
function Dashboard({ data, metrics, revenue, loading, online, go }) {
  const low = data.products.filter(isLow);
  const cards = [
    ["Doanh thu", money(metrics?.revenue ?? 0), "Chưa tính đơn hủy", "bolt", "accent"],
    ["Đơn hàng", metrics?.orders ?? data.orders.length, `${metrics?.pendingOrders ?? 0} đang chờ xử lý`, "receipt"],
    ["Đơn khách đặt chờ xác nhận", online.filter((o) => o.status === "Chờ xác nhận").length, "Cần xử lý ngay", "globe", "warn"],
    ["Sắp hết hàng", metrics?.lowStock ?? low.length, "Theo ngưỡng từng sản phẩm", "alert", low.length ? "warn" : ""],
    ["Sản phẩm", metrics?.products ?? data.products.length, `${metrics?.categories ?? data.categories.length} danh mục`, "box"],
    ["Giá trị tồn kho", money(metrics?.inventoryValue ?? 0), `${metrics?.customers ?? data.customers.length} khách hàng`, "tag"],
  ];
  const peak = Math.max(1, ...revenue.map((r) => Number(r.revenue)));
  const total = revenue.reduce((s, r) => s + Number(r.revenue), 0);
  return <>
    <div className="kpis">{cards.map(([label, value, hint, icon, tone]) => <article key={label} className={`kpi ${tone || ""}`}><span className="kpi-icon"><Icon name={icon} /></span><small>{label}</small><strong>{loading ? "…" : value}</strong><em>{hint}</em></article>)}</div>
    <div className="two">
      <section className="card"><header className="card-h"><div><h2>Doanh thu 30 ngày</h2><span className="muted">Tổng {money(total)}</span></div><a className="btn ghost" href="/api/dashboard/revenue.csv?days=30" download><Icon name="down" size={16} />CSV</a></header>
        {revenue.length ? <div className="bars" role="img" aria-label="Biểu đồ doanh thu theo ngày">{revenue.map((r, i) => <div key={r.date} className="bar-col" title={`${r.date}: ${money(r.revenue)} · ${r.orders} đơn`}><div className="bar-track"><span style={{ height: `${Number(r.revenue) > 0 ? Math.max(4, (Number(r.revenue) / peak) * 100) : 0}%` }} /></div><small>{i % 5 === 0 || i === revenue.length - 1 ? r.date.slice(8) : ""}</small></div>)}</div> : <div className="empty">Chưa có dữ liệu doanh thu.</div>}
      </section>
      <section className="card"><header className="card-h"><div><h2>Cần nhập thêm hàng</h2><span className="muted">{low.length} sản phẩm dưới ngưỡng</span></div><button className="btn ghost" onClick={() => go("products")}>Xem kho</button></header>
        <ul className="low-list">{low.length ? low.slice(0, 6).map((p) => <li key={p.id}><span className="thumb">{p.imageUrl ? <img src={p.imageUrl} alt="" /> : <Icon name="box" size={16} />}</span><div><b>{p.name}</b><small>Ngưỡng {p.minStock}</small></div><span className="qty">{p.quantity}</span></li>) : <li className="empty">Tồn kho đang ổn định.</li>}</ul>
      </section>
    </div>
  </>;
}

/* ---------- Generic table ---------- */
function Table({ page, data, api, loading, busy, isAdmin, reloadTick, onAdd, onEdit, onDelete, onStock, onHistory, onUpload }) {
  const cfg = resources[page];
  const [query, setQuery] = useState(""); const [status, setStatus] = useState(""); const [cat, setCat] = useState("");
  const [min, setMin] = useState(""); const [max, setMax] = useState("");
  const [idx, setIdx] = useState(0); const [size, setSize] = useState(10);
  const [remote, setRemote] = useState(null);
  const isProducts = page === "products";

  useEffect(() => {
    if (!isProducts) return;
    let alive = true;
    const t = setTimeout(async () => {
      const p = new URLSearchParams({ q: query, page: String(idx), size: String(size) });
      if (cat) p.set("categoryId", cat); if (min) p.set("minPrice", min); if (max) p.set("maxPrice", max); if (status) p.set("status", status);
      try { const r = await api(`/api/products/filter?${p}`); if (alive) setRemote(r); } catch { if (alive) setRemote(null); }
    }, 200);
    return () => { alive = false; clearTimeout(t); };
  }, [isProducts, api, query, idx, size, cat, min, max, status, reloadTick]); // reloadTick refreshes the page after edits

  const local = useMemo(() => {
    const q = query.trim().toLocaleLowerCase("vi");
    return (data[page] || []).filter((r) => !q || JSON.stringify(r).toLocaleLowerCase("vi").includes(q));
  }, [data, page, query]);
  const total = isProducts ? (remote?.total ?? 0) : local.length;
  const rows = isProducts ? (remote?.items ?? []) : local.slice(idx * size, (idx + 1) * size);
  const pages = Math.max(1, Math.ceil(total / size));
  const canAct = isAdmin || isProducts;
  const reset = (fn) => (e) => { fn(e.target.value); setIdx(0); };
  const filtering = query || status || cat || min || max;

  return <section className="card">
    <header className="card-h wrap">
      <div className="tools">
        <label className="search"><Icon name="search" size={16} /><input value={query} onChange={reset(setQuery)} placeholder="Tìm kiếm…" aria-label="Tìm kiếm" /></label>
        {isProducts && <>
          <select value={status} onChange={reset(setStatus)} aria-label="Lọc trạng thái"><option value="">Mọi trạng thái</option>{productStatuses.map((s) => <option key={s}>{s}</option>)}</select>
          <select value={cat} onChange={reset(setCat)} aria-label="Lọc danh mục"><option value="">Mọi danh mục</option>{data.categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</select>
          <input className="num" type="number" min="0" placeholder="Giá từ" value={min} onChange={reset(setMin)} aria-label="Giá từ" />
          <input className="num" type="number" min="0" placeholder="Giá đến" value={max} onChange={reset(setMax)} aria-label="Giá đến" />
        </>}
      </div>
      {isAdmin && <button className="btn primary" onClick={onAdd}><Icon name="plus" size={16} />Thêm mới</button>}
    </header>
    <div className="scroll"><table><thead><tr>{cfg.cols.map(([k, l]) => <th key={k}>{l}</th>)}{canAct && <th className="r">Thao tác</th>}</tr></thead>
      <tbody>{loading && !rows.length ? <tr><td className="empty" colSpan={cfg.cols.length + 1}>Đang tải dữ liệu…</td></tr> : rows.length ? rows.map((r) => <tr key={r.id}>
        {cfg.cols.map(([k]) => <td key={k} data-label={cfg.cols.find((c) => c[0] === k)[1]}>{cell(k, r, page, data)}</td>)}
        {canAct && <td className="r"><div className="acts">
          {isProducts && <><button className="ib" title="Điều chỉnh tồn kho" aria-label={`Điều chỉnh tồn kho ${r.name}`} disabled={busy} onClick={() => onStock(r)}><Icon name="plusminus" size={16} /></button>
            <button className="ib" title="Lịch sử tồn kho" aria-label={`Lịch sử tồn kho ${r.name}`} disabled={busy} onClick={() => onHistory(r)}><Icon name="clock" size={16} /></button>
            {isAdmin && <label className="ib file" title="Tải ảnh sản phẩm"><Icon name="image" size={16} /><input type="file" accept="image/png,image/jpeg,image/gif,image/webp" disabled={busy} onChange={(e) => { const f = e.target.files?.[0]; if (f) onUpload(r, f); e.target.value = ""; }} /></label>}</>}
          {isAdmin && <><button className="ib" title="Chỉnh sửa" aria-label="Chỉnh sửa" onClick={() => onEdit(r)}><Icon name="edit" size={16} /></button><button className="ib danger" title="Xóa" aria-label="Xóa" onClick={() => onDelete(r)}><Icon name="trash" size={16} /></button></>}
        </div></td>}
      </tr>) : <tr><td className="empty" colSpan={cfg.cols.length + 1}>{filtering ? "Không tìm thấy dữ liệu phù hợp." : "Chưa có dữ liệu."}</td></tr>}</tbody></table></div>
    <footer className="card-f"><span>{rows.length} / {total} mục</span>
      <div className="pager"><label>Dòng <select value={size} onChange={(e) => { setSize(Number(e.target.value)); setIdx(0); }}><option>10</option><option>25</option><option>50</option></select></label>
        <button disabled={idx <= 0} onClick={() => setIdx((n) => n - 1)} aria-label="Trang trước"><Icon name="left" size={16} /></button><span>{idx + 1} / {pages}</span>
        <button disabled={idx + 1 >= pages} onClick={() => setIdx((n) => n + 1)} aria-label="Trang sau"><Icon name="right" size={16} /></button></div></footer>
  </section>;
}

function cell(key, row, page, data) {
  if (key === "name" && page === "products") return <div className="pname"><span className="thumb">{row.imageUrl ? <img src={row.imageUrl} alt="" /> : <Icon name="box" size={16} />}</span><span><b>{row.name}</b><small>SKU-{String(row.id).padStart(4, "0")}{row.color ? ` · ${row.color}` : ""}</small></span></div>;
  if (key === "quantity" && page === "products") return <span className={isLow(row) ? "stock low" : "stock"}>{row.quantity}<small> / cảnh báo {row.minStock}</small></span>;
  if (key === "enabled") return <Badge tone={row.enabled ? "ok" : "mute"}>{row.enabled ? "Hoạt động" : "Đã khóa"}</Badge>;
  if (key === "role") return row.role === "ADMIN" ? "Quản trị viên" : "Nhân viên";
  if (key === "categoryId") return data.categories.find((x) => x.id === row[key])?.name || `#${row[key] ?? "—"}`;
  if (key === "customerId") return data.customers.find((x) => x.id === row[key])?.fullName || `#${row[key] ?? "—"}`;
  if (key === "productId") return data.products.find((x) => x.id === row[key])?.name || `#${row[key] ?? "—"}`;
  if (key === "orderId" || (key === "id" && page === "orders")) return `#${row[key] ?? "—"}`;
  if (["price", "totalAmount", "unitPrice", "subtotal"].includes(key)) return money(row[key]);
  if (key === "orderDate") return shortDate(row[key]);
  if (key === "status") return <StatusBadge value={row.status} />;
  return row[key] || "—";
}
function Badge({ tone = "ok", children }) { return <span className={`badge ${tone}`}>{children}</span>; }
function StatusBadge({ value }) {
  const tone = { "Hoàn thành": "ok", "Đang bán": "ok", "Đang giao": "info", "Đang xử lý": "info", "Chờ xác nhận": "warn", "Sắp hết": "warn", "Đã hủy": "mute", "Ngừng bán": "mute" }[value] || "mute";
  return <Badge tone={tone}>{value || "—"}</Badge>;
}

/* ---------- Online orders ---------- */
function OnlineOrders({ orders, busy, loading, onStatus, onPay }) {
  const [filter, setFilter] = useState("all");
  const counts = Object.fromEntries(orderStatuses.map((s) => [s, orders.filter((o) => o.status === s).length]));
  const list = orders.filter((o) => filter === "all" || o.status === filter);
  return <>
    <div className="chips" role="tablist">{[["all", "Tất cả", orders.length], ...orderStatuses.map((s) => [s, s, counts[s]])].map(([k, l, n]) => <button key={k} role="tab" aria-selected={filter === k} className={filter === k ? "on" : ""} onClick={() => setFilter(k)}>{l}<em>{n}</em></button>)}</div>
    {loading && !orders.length ? <div className="card empty">Đang tải đơn hàng…</div> : list.length ? <div className="orders">{list.map((o) => <article className="order" key={o.id}>
      <header><div><b>Đơn #{o.id}</b><small>{fullDate(o.orderDate)}</small></div><StatusBadge value={o.status} /></header>
      <div className="order-body">
        <div className="who"><b>{o.customerName}</b><span>{o.customerPhone || "—"}</span>{o.customerEmail && <span>{o.customerEmail}</span>}<span className="addr">{o.shippingAddress || "Chưa có địa chỉ"}</span>{o.customerNote && <p className="note">“{o.customerNote}”</p>}</div>
        <ul className="items">{o.items.map((it, i) => <li key={i}><span>{it.productName}<small>{[it.brand, it.size].filter(Boolean).join(" · ")}</small></span><span>×{it.quantity}</span><b>{money(it.subtotal)}</b></li>)}</ul>
      </div>
      <footer>
        <div className="pay"><small>{paymentLabels[o.paymentMethod] || o.paymentMethod}</small><Badge tone={paymentDone(o) ? "ok" : "warn"}>{o.paymentStatus}</Badge></div>
        <strong className="total">{money(o.totalAmount)}</strong>
        <div className="order-acts">
          {canConfirmPayment(o) && <button className="btn ghost" disabled={busy} onClick={() => onPay(o)}><Icon name="check" size={16} />Xác nhận thanh toán</button>}
          {o.status !== "Đã hủy" && <select value={o.status} disabled={busy} aria-label={`Trạng thái đơn ${o.id}`} onChange={(e) => onStatus(o, e.target.value)}>{orderStatuses.map((s) => <option key={s}>{s}</option>)}</select>}
        </div>
      </footer>
    </article>)}</div> : <div className="card empty">Không có đơn nào ở mục này.</div>}
  </>;
}

/* ---------- POS ---------- */
function Checkout({ data, api, busy, setBusy, notify, setError, reload }) {
  const [cart, setCart] = useState({}); const [customer, setCustomer] = useState(""); const [address, setAddress] = useState("");
  const [method, setMethod] = useState("COD"); const [q, setQ] = useState("");
  const products = data.products.filter((p) => p.status !== "Ngừng bán" && `${p.name} ${p.brand || ""}`.toLocaleLowerCase("vi").includes(q.toLocaleLowerCase("vi")));
  const items = Object.entries(cart).map(([id, quantity]) => ({ product: data.products.find((p) => p.id === Number(id)), quantity })).filter((x) => x.product);
  const total = items.reduce((s, i) => s + Number(i.product.price) * i.quantity, 0);
  function setQty(p, n) {
    if (!Number.isInteger(n) || n < 0) return;
    if (n > Number(p.quantity)) { notify(`${p.name} chỉ còn ${p.quantity} sản phẩm`); return; }
    setCart((o) => { const c = { ...o }; if (n === 0) delete c[p.id]; else c[p.id] = n; return c; });
  }
  async function submit() {
    if (!customer || !items.length) { notify("Chọn khách hàng và thêm sản phẩm trước"); return; }
    setBusy(true); setError("");
    try {
      const r = await api("/api/checkout", { method: "POST", body: JSON.stringify({ customerId: Number(customer), shippingAddress: address, paymentMethod: method, items: items.map((i) => ({ productId: i.product.id, quantity: i.quantity })) }) });
      setCart({}); notify(`Đã tạo đơn #${r.order.id} · ${money(r.order.totalAmount)}`); await reload();
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  return <div className="pos">
    <section className="card"><header className="card-h"><div><h2>Chọn sản phẩm</h2><span className="muted">{products.length} mặt hàng đang bán</span></div><label className="search"><Icon name="search" size={16} /><input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Tên hoặc thương hiệu" aria-label="Tìm sản phẩm" /></label></header>
      <div className="tiles">{products.length ? products.map((p) => <button key={p.id} className="tile" disabled={p.quantity < 1} onClick={() => setQty(p, (cart[p.id] || 0) + 1)}>
        <span className="tile-img">{p.imageUrl ? <img src={p.imageUrl} alt="" /> : <Icon name="box" size={28} />}</span><b>{p.name}</b><small>{p.brand || "—"} · còn {p.quantity}</small><strong>{money(p.price)}</strong>{cart[p.id] > 0 && <em>{cart[p.id]}</em>}</button>) : <div className="empty">Không có sản phẩm phù hợp.</div>}</div></section>
    <section className="card cart"><header className="card-h"><div><h2>Giỏ hàng</h2><span className="muted">{items.length} mặt hàng</span></div></header>
      {items.length ? <ul className="cart-list">{items.map(({ product: p, quantity }) => <li key={p.id}><div><b>{p.name}</b><small>{money(p.price)}</small></div>
        <div className="stepper"><button onClick={() => setQty(p, quantity - 1)} aria-label="Giảm">−</button><input type="number" min="0" value={quantity} onChange={(e) => setQty(p, Number(e.target.value))} aria-label={`Số lượng ${p.name}`} /><button onClick={() => setQty(p, quantity + 1)} aria-label="Tăng">+</button></div>
        <button className="ib danger" onClick={() => setQty(p, 0)} aria-label="Xóa khỏi giỏ"><Icon name="x" size={16} /></button></li>)}</ul> : <div className="empty">Chạm vào sản phẩm để thêm vào giỏ.</div>}
      <div className="cart-sum"><span>Tổng cộng</span><strong>{money(total)}</strong></div>
      <div className="stack">
        <label>Khách hàng<select value={customer} onChange={(e) => { setCustomer(e.target.value); const c = data.customers.find((x) => String(x.id) === e.target.value); setAddress(c?.address || ""); }}><option value="">Chọn khách hàng</option>{data.customers.map((c) => <option key={c.id} value={c.id}>{c.fullName}{c.phone ? ` · ${c.phone}` : ""}</option>)}</select></label>
        <label>Địa chỉ giao hàng<input value={address} onChange={(e) => setAddress(e.target.value)} maxLength={500} placeholder="Để trống = địa chỉ khách" /></label>
        <label>Thanh toán<select value={method} onChange={(e) => setMethod(e.target.value)}><option value="COD">COD — thu tiền khi giao</option><option value="BANK_TRANSFER">Chuyển khoản</option><option value="MOMO_DEMO">MoMo (mô phỏng)</option><option value="CARD_DEMO">Thẻ (mô phỏng)</option></select></label>
        <button className="btn primary lg" disabled={busy || !items.length || !customer} onClick={submit}>{busy ? "Đang xử lý…" : "Tạo đơn hàng"}</button>
      </div></section>
  </div>;
}

/* ---------- Modals ---------- */
function Modal({ title, eyebrow, onClose, children, wide }) {
  useEffect(() => { const k = (e) => e.key === "Escape" && onClose(); window.addEventListener("keydown", k); return () => window.removeEventListener("keydown", k); }, [onClose]);
  return <div className="backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}><section className={`modal ${wide ? "wide" : ""}`} role="dialog" aria-modal="true" aria-label={title}>
    <header><div>{eyebrow && <span className="eyebrow">{eyebrow}</span>}<h2>{title}</h2></div><button className="ib" onClick={onClose} aria-label="Đóng"><Icon name="x" size={16} /></button></header>{children}</section></div>;
}
function EntityModal({ modal, data, busy, error, onClose, onSave }) {
  const cfg = resources[modal.key];
  return <Modal eyebrow={modal.row ? "Cập nhật" : "Thêm mới"} title={`${modal.row ? "Chỉnh sửa" : "Thêm"} ${cfg.label.toLowerCase()}`} onClose={onClose}>
    <form onSubmit={onSave}><div className="grid">{cfg.fields.map(([key, label, type, required]) => <label key={key} className={type === "textarea" ? "field full" : "field"}>{label}{input(key, label, type, required && !(key === "password" && modal.row), modal.value[key], data, modal)}</label>)}</div>
      {modal.key === "products" && <p className="hint">“Sắp hết” được tính tự động khi tồn kho ≤ ngưỡng cảnh báo. Chỉ “Ngừng bán” là trạng thái chọn tay.</p>}
      {modal.key === "users" && modal.row && <p className="hint">Để trống mật khẩu nếu không muốn đổi.</p>}
      {error && <div className="banner bad" role="alert"><Icon name="alert" /><span>{error}</span></div>}
      <footer><button type="button" className="btn ghost" onClick={onClose}>Hủy</button><button className="btn primary" disabled={busy}>{busy ? "Đang lưu…" : "Lưu"}</button></footer></form></Modal>;
}
function input(key, label, type, required, value, data, modal) {
  const c = { name: key, required: Boolean(required), defaultValue: value ?? "" };
  if (type === "textarea") return <textarea {...c} rows="3" maxLength={500} />;
  if (type === "productStatus") return <select {...c}><option>Đang bán</option><option>Ngừng bán</option></select>;
  if (type === "orderStatus") return <select {...c}>{orderStatuses.map((s) => <option key={s}>{s}</option>)}</select>;
  if (type === "userRole") return <select {...c}><option value="STAFF">Nhân viên</option><option value="ADMIN">Quản trị viên</option></select>;
  if (type === "enabled") return <select {...c} defaultValue={String(value ?? true)}><option value="true">Hoạt động</option><option value="false">Đã khóa</option></select>;
  const choices = { category: data.categories.map((x) => [x.id, x.name]), customer: data.customers.map((x) => [x.id, x.fullName]), product: data.products.map((x) => [x.id, x.name]), order: data.orders.map((x) => [x.id, `Đơn #${x.id}`]) }[type];
  if (choices) return <select {...c}><option value="">Chọn…</option>{choices.map(([id, t]) => <option value={id} key={id}>{t}</option>)}</select>;
  const pw = type === "password";
  return <input {...c} type={type} autoComplete={pw ? "new-password" : undefined} minLength={pw ? 12 : undefined} readOnly={key === "username" && Boolean(modal.row)} min={type === "number" ? "0" : undefined} step={type === "number" ? (key === "quantity" || key === "minStock" ? "1" : "any") : undefined} maxLength={type === "text" || pw ? 200 : undefined} />;
}
function StockModal({ product, busy, error, onClose, onSave }) {
  return <Modal eyebrow="Quản lý tồn kho" title={`Điều chỉnh ${product.name}`} onClose={onClose}><form onSubmit={onSave}><div className="grid">
    <label className="field full">Thay đổi số lượng<input name="quantityChange" type="number" step="1" required autoFocus /><small>Số dương = nhập hàng, số âm = xuất hàng. Hiện có {product.quantity}.</small></label>
    <label className="field full">Lý do<input name="reason" maxLength="250" required /></label></div>
    {error && <div className="banner bad" role="alert"><Icon name="alert" /><span>{error}</span></div>}
    <footer><button type="button" className="btn ghost" onClick={onClose}>Hủy</button><button className="btn primary" disabled={busy}>{busy ? "Đang lưu…" : "Cập nhật"}</button></footer></form></Modal>;
}
function HistoryModal({ product, movements, onClose }) {
  return <Modal wide eyebrow="100 lần gần nhất" title={`Lịch sử tồn kho · ${product.name}`} onClose={onClose}><div className="scroll"><table><thead><tr><th>Thời gian</th><th>Loại</th><th>Thay đổi</th><th>Lý do</th></tr></thead><tbody>
    {movements.length ? movements.map((m) => <tr key={m.id}><td>{fullDate(m.createdAt)}</td><td>{m.movementType}</td><td className={m.quantityChange > 0 ? "pos-n" : "neg-n"}>{m.quantityChange > 0 ? "+" : ""}{m.quantityChange}</td><td>{m.reason}</td></tr>) : <tr><td className="empty" colSpan="4">Chưa có lịch sử.</td></tr>}</tbody></table></div></Modal>;
}
function Confirm({ title, text, ok, danger, action, onClose }) {
  return <Modal title={title} onClose={onClose}><p className="hint">{text}</p><footer><button className="btn ghost" onClick={onClose}>Quay lại</button><button className={`btn ${danger ? "danger" : "primary"}`} onClick={() => { onClose(); action(); }}>{ok}</button></footer></Modal>;
}
