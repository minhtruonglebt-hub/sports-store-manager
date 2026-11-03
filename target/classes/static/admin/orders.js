(() => {
  const statuses = ['Chờ xác nhận', 'Đang xử lý', 'Đang giao', 'Hoàn thành', 'Đã hủy'];
  const ordersElement = document.getElementById('orders');
  let orders = [];
  let csrf = '';

  const esc = value => String(value ?? '').replace(/[&<>"']/g, character => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  })[character]);
  const money = value => new Intl.NumberFormat('vi-VN', {
    style: 'currency', currency: 'VND', maximumFractionDigits: 0
  }).format(value || 0);

  async function api(url, options = {}) {
    const method = (options.method || 'GET').toUpperCase();
    const headers = new Headers(options.headers || {});
    if (options.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
    if (!['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method) && csrf) headers.set('X-XSRF-TOKEN', csrf);
    const response = await fetch(url, { credentials: 'same-origin', ...options, headers });
    const text = await response.text();
    let data = null;
    if (text) {
      try { data = JSON.parse(text); } catch { data = { message: text }; }
    }
    if (!response.ok) {
      const error = new Error(data?.message || `Lỗi máy chủ (${response.status})`);
      error.status = response.status;
      throw error;
    }
    return data;
  }

  async function loadCsrf() {
    const response = await fetch('/api/auth/csrf', { credentials: 'same-origin' });
    if (!response.ok) throw new Error('Không thể khởi tạo bảo mật phiên.');
    csrf = (await response.json()).token;
  }

  function render() {
    const query = document.getElementById('search').value.trim().toLocaleLowerCase('vi');
    const status = document.getElementById('statusFilter').value;
    const visibleOrders = orders.filter(order => {
      const text = [order.id, order.customerName, order.customerPhone, order.customerEmail,
        order.shippingAddress, ...order.items.map(item => item.productName)].join(' ').toLocaleLowerCase('vi');
      return text.includes(query) && (!status || order.status === status);
    });

    ordersElement.innerHTML = visibleOrders.length ? visibleOrders.map(order => {
      const isCancelled = order.status === 'Đã hủy';
      const badgeClass = isCancelled ? 'cancelled' : order.status === 'Hoàn thành' ? 'done' : '';
      const options = statuses.map(item => `<option value="${esc(item)}" ${item === order.status ? 'selected' : ''}>${esc(item)}</option>`).join('');
      const items = order.items.map(item => `<tr><td>${esc(item.productName)}${item.brand ? ` · ${esc(item.brand)}` : ''}${item.size ? ` · cỡ ${esc(item.size)}` : ''}</td><td>${item.quantity}</td><td>${money(item.unitPrice)}</td><td>${money(item.subtotal)}</td></tr>`).join('');
      return `<article class="order" data-order="${order.id}">
        <div class="order-head"><div><div class="order-number">Đơn hàng #${order.id}</div><div class="order-date">${new Date(order.orderDate).toLocaleString('vi-VN')}</div></div><span class="badge ${badgeClass}">${esc(order.status)}</span></div>
        <div class="order-grid"><div class="info"><b>THÔNG TIN KHÁCH HÀNG</b><div>${esc(order.customerName)}<br>${esc(order.customerPhone || 'Chưa có số điện thoại')}<br>${esc(order.customerEmail || 'Chưa có email')}</div></div>
          <div class="info"><b>GIAO HÀNG & THANH TOÁN</b><div>${esc(order.shippingAddress || 'Chưa có địa chỉ')}<br>${esc(order.paymentMethod || '—')} · ${esc(order.paymentStatus || '—')}</div></div></div>
        <div style="overflow:auto"><table class="items"><thead><tr><th>Sản phẩm</th><th>SL</th><th>Đơn giá</th><th>Thành tiền</th></tr></thead><tbody>${items}</tbody></table></div>
        <div class="order-footer"><span class="total">Tổng: ${money(order.totalAmount)}</span><div class="status-control"><label for="status-${order.id}">Trạng thái</label><select id="status-${order.id}" data-status>${options}</select><button class="button primary" data-save ${isCancelled ? 'disabled' : ''}>Cập nhật</button></div></div>
        <p class="muted" data-message role="status"></p>
      </article>`;
    }).join('') : `<div class="empty">${orders.length ? 'Không có đơn phù hợp bộ lọc.' : 'Chưa có đơn hàng trực tuyến nào.'}</div>`;
  }

  function updateSummary() {
    document.getElementById('totalOrders').textContent = orders.length;
    document.getElementById('pendingOrders').textContent = orders.filter(order => order.status === 'Chờ xác nhận').length;
    document.getElementById('shippingOrders').textContent = orders.filter(order => order.status === 'Đang giao').length;
  }

  async function loadOrders() {
    ordersElement.innerHTML = '<div class="empty">Đang tải đơn hàng...</div>';
    try {
      orders = await api('/api/orders/online');
      updateSummary();
      render();
    } catch (error) {
      ordersElement.innerHTML = `<div class="error">${esc(error.message || 'Không thể tải đơn hàng.')}</div>`;
      if (error.status === 401 || error.status === 403) {
        window.setTimeout(() => { window.location.href = '/'; }, 1200);
      }
    }
  }

  ordersElement.addEventListener('click', async event => {
    const button = event.target.closest('[data-save]');
    if (!button || button.disabled) return;
    const card = button.closest('[data-order]');
    const orderId = Number(card.dataset.order);
    const status = card.querySelector('[data-status]').value;
    const message = card.querySelector('[data-message]');
    button.disabled = true;
    message.textContent = '';
    try {
      await api(`/api/orders/${orderId}/status`, {
        method: 'PUT',
        body: JSON.stringify({ status })
      });
      message.textContent = 'Đã cập nhật trạng thái đơn hàng.';
      await loadOrders();
    } catch (error) {
      message.textContent = error.message || 'Không thể cập nhật đơn hàng.';
      button.disabled = false;
    }
  });

  document.getElementById('refresh').addEventListener('click', loadOrders);
  document.getElementById('search').addEventListener('input', render);
  document.getElementById('statusFilter').addEventListener('change', render);

  async function init() {
    try {
      await loadCsrf();
      const account = await api('/api/auth/me');
      if (!account.roles.some(role => role === 'ADMIN' || role === 'STAFF')) {
        window.location.href = account.roles.includes('CUSTOMER') ? '/shop.html' : '/';
        return;
      }
      document.getElementById('account').textContent = `${account.username} · ${account.roles.join(', ')}`;
      await loadOrders();
    } catch {
      window.location.href = '/';
    }
  }

  init();
})();
