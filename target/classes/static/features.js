/* Sportiva: session security, staff checkout, product filters and sales metrics. */
(() => {
  const originalRender = render;
  const originalDashboard = dashboard;
  const originalShow = show;
  cfg.products.fields.push(['minStock', 'Ngưỡng cảnh báo tồn kho', 'number']);
  cfg.products.cols.push(['minStock', 'Cảnh báo từ']);
  let account = null;
  let productPage = 0;
  let cart = new Map();
  let saleProducts = [];
  let csrf = '';

  const style = document.createElement('style');
  style.textContent = `
    [hidden]{display:none!important}.auth-screen{position:fixed;inset:0;z-index:20;background:#f4f7f3;display:grid;place-items:center;padding:18px}
    .auth-card{width:min(420px,100%);background:#fff;border:1px solid #e7ece8;border-radius:18px;padding:30px;box-shadow:0 18px 55px #14251f18}
    .auth-card h1{font:800 25px Manrope;margin:10px 0 5px}.auth-card p{color:#7b8981;line-height:1.55}
    .auth-card form{display:grid;gap:12px;margin-top:20px}.auth-card label{font-size:12px;font-weight:700}
    .auth-card input{width:100%;padding:12px;border:1px solid #e7ece8;border-radius:9px;margin-top:6px}
    .demo-accounts{background:#f6f8f5;border-radius:10px;padding:12px;font-size:11px;color:#59675f;line-height:1.7;margin-top:14px}
    .profile{gap:8px}.logout{margin-left:auto;border:1px solid #e7ece8;border-radius:7px;background:#fff;padding:7px 9px;color:#53635a;font-size:11px}
    .sales-view{display:none;margin-top:18px}.sales-grid{display:grid;grid-template-columns:minmax(0,1.35fr) minmax(300px,.8fr);gap:16px}
    .sales-box{background:#fff;border:1px solid #e7ece8;border-radius:13px;padding:17px;min-width:0}.sales-products{display:grid;grid-template-columns:repeat(auto-fill,minmax(190px,1fr));gap:10px;margin-top:13px}
    .sale-card{border:1px solid #edf0ed;border-radius:10px;padding:12px}.sale-card h3{font-size:13px;margin:0 0 8px}.sale-card small{color:#87928b}.sale-card .btn{margin-top:10px;padding:8px 10px;font-size:11px}
    .cart-row{display:grid;grid-template-columns:1fr auto auto;align-items:center;gap:8px;padding:10px 0;border-bottom:1px solid #edf0ed;font-size:12px}
    .cart-row input{width:56px;padding:6px;border:1px solid #e7ece8;border-radius:6px}.sale-controls{display:grid;gap:10px;margin-top:14px}
    .sale-controls label{display:grid;gap:5px;font-size:11px;font-weight:700}.sale-controls input,.sale-controls select{width:100%;padding:9px;border:1px solid #e7ece8;border-radius:8px;background:#fff}
    .cart-total{display:flex;justify-content:space-between;font:800 17px Manrope;padding:13px 0}.payment-note{font-size:10px;color:#7b8981;line-height:1.5}
    .filter-extra{display:flex;gap:7px;align-items:center;flex-wrap:wrap}.filter-extra input,.filter-extra select{border:1px solid #e7ece8;border-radius:8px;padding:9px;background:#fff;max-width:150px}
    .page-controls{display:flex;gap:8px;align-items:center}.page-controls button{border:1px solid #e7ece8;background:white;border-radius:7px;padding:5px 9px}
    .staff-mode #add{display:none!important}.staff-mode .actions{display:none!important}
    .stock-alert{background:#fff5df;color:#92631b;padding:10px 13px;border-radius:9px;margin:0 0 14px;font-size:12px}
    .inventory-actions{display:flex;gap:6px;min-width:132px}.inventory-actions button{border:1px solid #e7ece8;border-radius:7px;background:#fff;padding:6px 8px;color:#53635a;font-size:11px}
    .inventory-notice{display:none;background:#fff5df;color:#92631b;padding:12px 15px;border-radius:10px;margin-bottom:14px;font-size:12px}
    @media(max-width:800px){.sales-grid{grid-template-columns:1fr}}
  `;
  document.head.append(style);

  const auth = document.createElement('div');
  auth.id = 'authScreen'; auth.className = 'auth-screen';
  auth.innerHTML = `<div class="auth-card"><span class="logo">S</span><h1>Đăng nhập Sportiva</h1><p>Đăng nhập để quản lý cửa hàng. Quyền truy cập sẽ theo vai trò tài khoản.</p><form id="loginForm"><label>Tên đăng nhập<input name="username" autocomplete="username" required maxlength="80"></label><label>Mật khẩu<input name="password" type="password" autocomplete="current-password" required maxlength="200"></label><button class="btn primary" type="submit">Đăng nhập</button><div id="loginError" class="muted" role="alert"></div></form><div class="demo-accounts"><b>Tài khoản trình diễn</b><br>Quản trị: admin / Admin@123<br>Nhân viên: staff / Staff@123</div></div>`;
  document.body.append(auth);

  const navArea = document.getElementById('nav');
  const salesButton = document.createElement('button');
  salesButton.dataset.key = 'checkout';
  salesButton.innerHTML = '<span class="ico">🛒</span><span class="nav-label">Bán hàng</span>';
  salesButton.addEventListener('click', () => show('checkout'));
  navArea.append(salesButton);

  const onlineOrdersButton = document.createElement('button');
  onlineOrdersButton.innerHTML = '<span class="ico">▣</span><span class="nav-label">Đơn khách đặt</span>';
  onlineOrdersButton.addEventListener('click', () => { window.location.href = '/admin/orders.html'; });
  navArea.append(onlineOrdersButton);

  const profile = document.querySelector('.profile');
  if (profile) {
    profile.innerHTML = '<span class="avatar" id="userAvatar">?</span><div class="profile-info"><b id="userName">Chưa đăng nhập</b><small id="userRole">—</small></div><button id="logoutButton" class="logout" type="button">Thoát</button>';
    document.getElementById('logoutButton').addEventListener('click', logout);
  }

  const panel = document.querySelector('.panel');
  const inventoryNotice = document.createElement('div');
  inventoryNotice.className = 'inventory-notice';
  panel.before(inventoryNotice);
  const salesView = document.createElement('section');
  salesView.id = 'salesView'; salesView.className = 'sales-view';
  salesView.innerHTML = `<div id="stockAlert"></div><div class="sales-grid"><div class="sales-box"><div class="panel-head" style="padding:0 0 12px"><h2>Sản phẩm có thể bán</h2><input id="saleSearch" class="search" placeholder="Tìm sản phẩm / thương hiệu" aria-label="Tìm sản phẩm"></div><div id="saleProducts" class="sales-products"></div></div><div class="sales-box"><h2 style="font:700 16px Manrope;margin:0">Giỏ hàng</h2><div id="cartLines" class="muted" style="margin-top:8px">Chưa có mặt hàng</div><div class="cart-total"><span>Tổng tiền</span><span id="cartTotal">0 ₫</span></div><div class="sale-controls"><label>Khách hàng<select id="saleCustomer" required></select></label><label>Địa chỉ giao hàng<input id="saleAddress" maxlength="300" placeholder="Để trống dùng địa chỉ khách hàng"></label><label>Phương thức thanh toán<select id="paymentMethod"><option value="COD">COD — thu tiền khi giao</option><option value="BANK_TRANSFER">Chuyển khoản — chờ xác nhận</option><option value="MOMO_DEMO">MoMo (mô phỏng)</option><option value="CARD_DEMO">Thẻ (mô phỏng)</option></select></label><p class="payment-note">MoMo và thẻ chỉ mô phỏng luồng thanh toán phục vụ đồ án; không kết nối ngân hàng và không thu tiền thật.</p><button id="checkoutButton" class="btn primary" type="button">Tạo đơn hàng</button></div></div></div>`;
  panel.insertAdjacentElement('afterend', salesView);

  const tools = document.querySelector('.tools');
  const filters = document.createElement('div');
  filters.id = 'productFilters'; filters.className = 'filter-extra'; filters.style.display = 'none';
  filters.innerHTML = `<select id="categoryFilter" aria-label="Lọc danh mục"><option value="">Mọi danh mục</option></select><input id="minPrice" type="number" min="0" placeholder="Giá từ" aria-label="Giá từ"><input id="maxPrice" type="number" min="0" placeholder="Giá đến" aria-label="Giá đến"><select id="pageSize" aria-label="Số dòng"><option value="10">10 / trang</option><option value="25">25 / trang</option><option value="50">50 / trang</option></select>`;
  tools?.append(filters);
  const pageControls = document.createElement('span');
  pageControls.id = 'pageControls'; pageControls.className = 'page-controls'; pageControls.style.display = 'none';
  document.querySelector('.pagination')?.append(pageControls);

  // All unsafe HTTP methods include the session-bound CSRF token.
  api = async (url, options = {}) => {
    const method = (options.method || 'GET').toUpperCase();
    const headers = new Headers(options.headers || {});
    if (!headers.has('Content-Type') && options.body) headers.set('Content-Type', 'application/json');
    if (!['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method) && csrf) headers.set('X-XSRF-TOKEN', csrf);
    const response = await fetch(url, { credentials: 'same-origin', ...options, headers });
    const text = await response.text();
    let data = null;
    if (text) { try { data = JSON.parse(text); } catch { data = { message: text }; } }
    if (!response.ok) {
      if (response.status === 401 && !url.includes('/api/auth/login')) auth.hidden = false;
      throw new Error(data?.message || `Lỗi máy chủ (${response.status})`);
    }
    return data;
  };

  async function loadCsrf() {
    const response = await fetch('/api/auth/csrf', { credentials: 'same-origin' });
    const result = await response.json(); csrf = result.token;
  }
  function setAccount(info) {
    if (info.roles.includes('CUSTOMER')) {
      window.location.replace('/shop.html');
      return;
    }
    account = info;
    document.body.classList.toggle('staff-mode', !info.roles.includes('ADMIN'));
    document.getElementById('userName').textContent = info.username;
    document.getElementById('userRole').textContent = info.roles.includes('ADMIN') ? 'Quản trị viên' : 'Nhân viên bán hàng';
    document.getElementById('userAvatar').textContent = info.username.slice(0, 2).toUpperCase();
    auth.hidden = true;
  }
  async function init() {
    try {
      await loadCsrf();
      const me = await api('/api/auth/me');
      if (me.roles.includes('CUSTOMER')) {
        window.location.replace('/shop.html');
        return;
      }
      setAccount(me); await refresh();
    } catch { auth.hidden = false; }
  }
  document.getElementById('loginForm').addEventListener('submit', async event => {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    document.getElementById('loginError').textContent = '';
    try {
      await loadCsrf();
      const info = await api('/api/auth/login', { method: 'POST', body: JSON.stringify(Object.fromEntries(formData)) });
      document.getElementById('loginForm').reset();
      if (info.roles.includes('CUSTOMER')) {
        window.location.replace('/shop.html');
        return;
      }
      setAccount(info); await refresh(); notify('Đăng nhập thành công');
    } catch (error) { document.getElementById('loginError').textContent = error.message || 'Đăng nhập thất bại'; }
  });
  async function logout() {
    try { await api('/api/auth/logout', { method: 'POST' }); } catch { /* Session may already have expired. */ }
    account = null; auth.hidden = false; document.body.classList.remove('staff-mode');
    document.getElementById('loginError').textContent = '';
    try { await loadCsrf(); } catch {}
  }

  // Dashboard metrics come from the server rather than being guessed from a visible page.
  dashboard = async () => {
    originalDashboard();
    try {
      const data = await api('/api/dashboard');
      stats.innerHTML = [
        ['Doanh thu đơn hàng', money(data.revenue), '₫'], ['Đơn hàng', data.orders, '▤'],
        ['Chờ xử lý', data.pendingOrders, '◷'], ['Sắp hết hàng', data.lowStock, '⚠'],
        ['Sản phẩm', data.products, '◈'], ['Khách hàng', data.customers, '♙'],
        ['Giá trị tồn kho', money(data.inventoryValue), '₫']
      ].map(item => `<div class="stat"><div class="stat-head">${item[0]}<span class="stat-icon">${item[2]}</span></div><strong>${item[1]}</strong><small>Thông tin hiện tại</small></div>`).join('');
      const low = await api('/api/products/low-stock');
      inventoryNotice.style.display = current === 'dashboard' && low.count ? 'block' : 'none';
      inventoryNotice.textContent = low.count
        ? `⚠ ${low.count} sản phẩm đang ở ngưỡng tồn kho hoặc thấp hơn. Mở Sản phẩm để nhập thêm hàng hoặc xem lịch sử.`
        : '';
      document.getElementById('stockAlert').innerHTML = '';
    } catch (error) { notify(error.message); }
  };

  async function renderFilteredProducts() {
    const params = new URLSearchParams({ q: search.value, page: String(productPage), size: pageSize.value });
    const categoryId = document.getElementById('categoryFilter').value;
    const min = document.getElementById('minPrice').value, max = document.getElementById('maxPrice').value;
    if (categoryId) params.set('categoryId', categoryId);
    if (min) params.set('minPrice', min);
    if (max) params.set('maxPrice', max);
    if (filter.value !== 'all') params.set('status', filter.value);
    try {
      const result = await api('/api/products/filter?' + params.toString());
      const products = result.items;
      head.innerHTML = '<tr>' + cfg.products.cols.map(col => `<th>${col[1]}</th>`).join('') + '<th>Thao tác</th></tr>';
      body.innerHTML = products.length ? products.map(product => '<tr>' + cfg.products.cols.map(([key]) => `<td>${cell(key, product)}</td>`).join('') + `<td><div class="actions"><button class="icon" onclick="openForm(${product.id})">✎</button><button class="icon" onclick="removeRow(${product.id})">⌫</button></div><div class="inventory-actions"><button type="button" data-stock-adjust="${product.id}">± Nhập/xuất</button><button type="button" data-stock-history="${product.id}">Lịch sử</button></div></td></tr>`).join('') : `<tr><td colspan="${cfg.products.cols.length + 1}" class="empty">Không có sản phẩm phù hợp bộ lọc.</td></tr>`;
      body.querySelectorAll('[data-stock-adjust]').forEach(button => button.addEventListener('click', () => adjustStock(Number(button.dataset.stockAdjust))));
      body.querySelectorAll('[data-stock-history]').forEach(button => button.addEventListener('click', () => showStockHistory(Number(button.dataset.stockHistory))));
      count.textContent = `Tổng ${result.total} sản phẩm`;
      pageControls.innerHTML = `<button type="button" ${result.page <= 0 ? 'disabled' : ''} id="prevProductPage">‹</button><span>Trang ${result.totalPages ? result.page + 1 : 0} / ${result.totalPages}</span><button type="button" ${result.page + 1 >= result.totalPages ? 'disabled' : ''} id="nextProductPage">›</button>`;
      document.getElementById('prevProductPage')?.addEventListener('click', () => { productPage--; render(); });
      document.getElementById('nextProductPage')?.addEventListener('click', () => { productPage++; render(); });
    } catch (error) { notify(error.message); }
  }
  render = () => current === 'products' ? renderFilteredProducts() : current === 'checkout' ? renderSale() : originalRender();
  search.addEventListener('input', () => { productPage = 0; if (current === 'products') render(); });
  filter.addEventListener('change', () => { productPage = 0; if (current === 'products') render(); });
  filters.querySelectorAll('select,input').forEach(el => el.addEventListener('change', () => { productPage = 0; if (current === 'products') render(); }));
  document.getElementById('categoryFilter').addEventListener('focus', populateCategories);
  function populateCategories() {
    const select = document.getElementById('categoryFilter');
    const selected = select.value;
    select.innerHTML = '<option value="">Mọi danh mục</option>' + (rows.categories || []).map(c => `<option value="${c.id}">${esc(c.name)}</option>`).join('');
    select.value = selected;
  }

  show = key => {
    if (key !== 'checkout') {
      salesView.style.display = 'none'; stats.style.display = ''; panel.style.display = '';
      filters.style.display = key === 'products' ? 'flex' : 'none'; pageControls.style.display = key === 'products' ? 'flex' : 'none';
      inventoryNotice.style.display = 'none';
      originalShow(key); if (key === 'products') { populateCategories(); render(); }
      return;
    }
    current = 'checkout';
    document.querySelectorAll('#nav button').forEach(button => button.classList.toggle('active', button.dataset.key === 'checkout'));
    side.classList.remove('open'); crumb.textContent = title.textContent = 'Bán hàng';
    sub.textContent = 'Tạo đơn hàng, cập nhật tồn kho và chọn phương thức thanh toán.';
    add.style.display = 'none'; search.style.display = 'none'; filter.style.display = 'none';
    filters.style.display = 'none'; pageControls.style.display = 'none'; stats.style.display = 'none'; panel.style.display = 'none'; salesView.style.display = 'block';
    Promise.all([api('/api/products'), api('/api/customers')]).then(([p, c]) => {
      rows.products = extract(p); rows.customers = extract(c); renderSale();
    }).catch(error => notify(error.message));
  };

  async function adjustStock(productId) {
    const quantityChange = prompt('Nhập số lượng thay đổi (số dương để nhập hàng, số âm để xuất hàng):');
    if (quantityChange === null) return;
    if (!/^-?\d+$/.test(quantityChange.trim()) || Number(quantityChange) === 0) {
      notify('Vui lòng nhập số nguyên khác 0.');
      return;
    }
    const reason = prompt('Lý do điều chỉnh tồn kho:');
    if (reason === null) return;
    try {
      await api(`/api/products/${productId}/stock-adjustments`, {
        method: 'POST',
        body: JSON.stringify({ quantityChange: Number(quantityChange), reason })
      });
      notify('Đã cập nhật tồn kho và lưu lịch sử.');
      await refresh();
    } catch (error) { notify(error.message); }
  }

  async function showStockHistory(productId) {
    try {
      const movements = await api(`/api/products/${productId}/stock-movements`);
      const text = movements.length
        ? movements.map(item => `${new Date(item.createdAt).toLocaleString('vi-VN')} | ${item.quantityChange > 0 ? '+' : ''}${item.quantityChange} | ${item.movementType} | ${item.reason}`).join('\n')
        : 'Chưa có lịch sử điều chỉnh tồn kho.';
      alert(`Lịch sử tồn kho gần đây (tối đa 100 lần):\n\n${text}`);
    } catch (error) { notify(error.message); }
  }

  function renderSale() {
    const query = document.getElementById('saleSearch').value.toLowerCase();
    const visibleProducts = (rows.products || []).filter(p => p.status !== 'Ngừng bán' && `${p.name} ${p.brand || ''}`.toLowerCase().includes(query));
    document.getElementById('saleProducts').innerHTML = visibleProducts.length ? visibleProducts.map(p => `<article class="sale-card"><h3>${esc(p.name)}</h3><small>${esc(p.brand || 'Không rõ thương hiệu')} · Tồn ${p.quantity}</small><div style="margin-top:8px;font-weight:700">${money(p.price)}</div><button class="btn light" type="button" data-add-product="${p.id}" ${p.quantity < 1 ? 'disabled' : ''}>Thêm vào giỏ</button></article>`).join('') : '<p class="empty">Không tìm thấy mặt hàng.</p>';
    document.querySelectorAll('[data-add-product]').forEach(button => button.addEventListener('click', () => addToCart(Number(button.dataset.addProduct))));
    const customers = rows.customers || [];
    const customerSelect = document.getElementById('saleCustomer');
    const previous = customerSelect.value;
    customerSelect.innerHTML = '<option value="">Chọn khách hàng</option>' + customers.map(c => `<option value="${c.id}">${esc(c.fullName)}${c.phone ? ' · ' + esc(c.phone) : ''}</option>`).join('');
    if (previous) customerSelect.value = previous;
    document.getElementById('cartLines').innerHTML = cart.size ? [...cart.entries()].map(([id, quantity]) => {
      const p = rows.products.find(x => x.id === id);
      return `<div class="cart-row"><span>${esc(p.name)}<br><small>${money(p.price)} / sp</small></span><input aria-label="Số lượng ${esc(p.name)}" type="number" min="1" max="${p.quantity}" value="${quantity}" data-cart-qty="${id}"><button class="icon" type="button" data-remove-cart="${id}">×</button></div>`;
    }).join('') : '<div class="empty">Chưa có mặt hàng trong giỏ.</div>';
    document.querySelectorAll('[data-cart-qty]').forEach(input => input.addEventListener('change', () => updateCart(Number(input.dataset.cartQty), Number(input.value))));
    document.querySelectorAll('[data-remove-cart]').forEach(button => button.addEventListener('click', () => { cart.delete(Number(button.dataset.removeCart)); renderSale(); }));
    const total = [...cart.entries()].reduce((sum, [id, qty]) => sum + Number(rows.products.find(p => p.id === id)?.price || 0) * qty, 0);
    document.getElementById('cartTotal').textContent = money(total);
    document.getElementById('checkoutButton').disabled = !cart.size || !customers.length;
    const lowStock = rows.products.filter(p => Number(p.quantity) <= 5 && p.status !== 'Ngừng bán');
    document.getElementById('stockAlert').innerHTML = lowStock.length ? `<div class="stock-alert">⚠ ${lowStock.length} mặt hàng sắp hết: ${lowStock.map(p => esc(p.name)).join(', ')}</div>` : '';
  }
  function addToCart(id) {
    const product = rows.products.find(p => p.id === id);
    if (!product) return;
    const next = (cart.get(id) || 0) + 1;
    if (next > product.quantity) { notify(`Tồn kho ${product.name} chỉ còn ${product.quantity}`); return; }
    cart.set(id, next); renderSale();
  }
  function updateCart(id, quantity) {
    const product = rows.products.find(p => p.id === id);
    if (!Number.isInteger(quantity) || quantity < 1 || quantity > product.quantity) {
      notify(`Số lượng phải từ 1 đến ${product.quantity}`); renderSale(); return;
    }
    cart.set(id, quantity); renderSale();
  }
  document.getElementById('saleSearch').addEventListener('input', renderSale);
  document.getElementById('saleCustomer').addEventListener('change', event => {
    const customer = rows.customers.find(c => String(c.id) === event.target.value);
    if (customer) document.getElementById('saleAddress').value = customer.address || '';
  });
  document.getElementById('checkoutButton').addEventListener('click', async () => {
    const customerId = Number(document.getElementById('saleCustomer').value);
    if (!customerId || !cart.size) { notify('Chọn khách hàng và thêm sản phẩm trước khi tạo đơn'); return; }
    try {
      const result = await api('/api/checkout', { method: 'POST', body: JSON.stringify({
        customerId, shippingAddress: document.getElementById('saleAddress').value,
        paymentMethod: document.getElementById('paymentMethod').value,
        items: [...cart.entries()].map(([productId, quantity]) => ({ productId, quantity }))
      }) });
      cart.clear(); notify(`Đã tạo đơn #${result.order.id} · ${money(result.order.totalAmount)}. ${result.order.paymentStatus}`);
      if (result.paymentNotice) setTimeout(() => notify(result.paymentNotice), 2500);
      await refresh();
      if (current === 'checkout') { rows.products = extract(await api('/api/products')); renderSale(); }
    } catch (error) { notify(error.message); }
  });

  // Use a category selector instead of making staff guess an internal database ID.
  const originalOpenForm = openForm;
  openForm = id => {
    originalOpenForm(id);
    if (current === 'products') {
      const field = fields.querySelector('[name="categoryId"]');
      if (field && field.tagName === 'INPUT') {
        const selected = field.value; const select = document.createElement('select'); select.name = 'categoryId'; select.required = true;
        select.innerHTML = '<option value="">Chọn danh mục</option>' + (rows.categories || []).map(c => `<option value="${c.id}">${esc(c.name)}</option>`).join('');
        select.value = selected; field.replaceWith(select);
      }
    }
  };
  const oldSubmit = form.onsubmit;
  form.onsubmit = event => {
    if (current !== 'products') return oldSubmit(event);
    event.preventDefault();
    const category = fields.querySelector('[name="categoryId"]');
    if (!category?.value) { notify('Vui lòng chọn danh mục'); return; }
    return oldSubmit(event);
  };

  cfg.orders.cols.push(['paymentMethod', 'Thanh toán'], ['paymentStatus', 'Tình trạng tiền']);
  const oldCell = cell;
  cell = (key, row) => key === 'paymentMethod' ? ({ COD:'COD', BANK_TRANSFER:'Chuyển khoản', MOMO_DEMO:'MoMo demo', CARD_DEMO:'Thẻ demo' }[row[key]] || esc(row[key] || '—')) : oldCell(key, row);

  init();
})();
