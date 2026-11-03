(() => {
  const authScreen = document.getElementById('authScreen');
  const app = document.getElementById('app');
  const loginForm = document.getElementById('loginForm');
  const registerForm = document.getElementById('registerForm');
  const productsElement = document.getElementById('products');
  const cart = loadSavedCart();
  let csrf = '';
  let products = [];
  let customerAuthenticated = false;
  let paymentInfo = null;

  const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, character => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  })[character]);
  const money = value => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(value || 0);

  function loadSavedCart() {
    try {
      const saved = JSON.parse(localStorage.getItem('sportiva-customer-cart') || '[]');
      if (!Array.isArray(saved)) throw new Error('Dữ liệu giỏ hàng không đúng định dạng.');
      return new Map(saved.filter(item => Array.isArray(item) && item.length === 2
        && Number.isSafeInteger(item[0]) && item[0] > 0
        && Number.isSafeInteger(item[1]) && item[1] > 0 && item[1] <= 999));
    } catch (error) {
      document.getElementById('checkoutError').textContent =
        `Không thể khôi phục giỏ hàng đã lưu: ${error.message || 'dữ liệu không hợp lệ'}`;
      return new Map();
    }
  }

  function saveCart() {
    try {
      localStorage.setItem('sportiva-customer-cart', JSON.stringify([...cart.entries()]));
      return true;
    } catch {
      document.getElementById('checkoutError').textContent =
        'Trình duyệt không lưu được giỏ hàng; giỏ chỉ còn đến khi bạn đóng trang.';
      return false;
    }
  }

  async function loadCsrf() {
    const response = await fetch('/api/auth/csrf', { credentials: 'same-origin' });
    if (!response.ok) throw new Error('Không thể khởi tạo bảo mật phiên đăng nhập.');
    csrf = (await response.json()).token;
  }

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

  function showLogin(message = '') {
    authScreen.hidden = false;
    switchTab(false);
    document.getElementById('loginError').textContent = message;
  }

  function switchTab(register) {
    loginForm.hidden = register;
    registerForm.hidden = !register;
    document.getElementById('loginTab').className = `button ${register ? 'light' : 'primary'}`;
    document.getElementById('registerTab').className = `button ${register ? 'primary' : 'light'}`;
    document.getElementById('loginError').textContent = '';
    document.getElementById('registerError').textContent = '';
  }

  function showGuestStore() {
    customerAuthenticated = false;
    authScreen.hidden = true;
    app.hidden = false;
    document.getElementById('accountName').textContent = '';
    document.getElementById('openLogin').hidden = false;
    document.getElementById('logout').hidden = true;
    document.getElementById('refreshOrders').hidden = true;
    document.getElementById('customerInfo').textContent =
      'Bạn có thể xem sản phẩm trước. Đăng nhập hoặc tạo tài khoản để đặt hàng và theo dõi đơn.';
    document.getElementById('orderList').textContent = 'Đăng nhập để xem đơn hàng của bạn.';
    document.getElementById('checkout').textContent = 'Đăng nhập để đặt hàng';
  }

  async function showStore() {
    customerAuthenticated = true;
    authScreen.hidden = true;
    app.hidden = false;
    document.getElementById('openLogin').hidden = true;
    document.getElementById('logout').hidden = false;
    document.getElementById('checkout').textContent = 'Đặt hàng';
    document.getElementById('refreshOrders').hidden = false;
    await Promise.all([loadProducts(), loadOrders(), loadCustomer()]);
  }

  async function loadProducts() {
    products = await api('/api/store/products');
    let cartChanged = false;
    for (const [id, quantity] of cart) {
      const product = products.find(item => item.id === id);
      if (!product || !product.quantity || product.quantity < 1) {
        cart.delete(id);
        cartChanged = true;
      } else if (quantity > product.quantity) {
        cart.set(id, product.quantity);
        cartChanged = true;
      }
    }
    if (cartChanged) saveCart();
    renderProducts();
    renderCart();
  }

  function renderProducts() {
    const query = document.getElementById('search').value.trim().toLocaleLowerCase();
    const visible = products.filter(product =>
      `${product.name} ${product.brand || ''}`.toLocaleLowerCase().includes(query));
    productsElement.innerHTML = visible.length ? visible.map(product => {
      const image = product.imageUrl && product.imageUrl.startsWith('/uploads/products/')
        ? `<img src="${escapeHtml(product.imageUrl)}" alt="${escapeHtml(product.name)}">` : '👟';
      const unavailable = !product.quantity || product.quantity < 1;
      return `<article class="product"><div class="product-image">${image}</div><div class="product-info">
        <small>${escapeHtml(product.brand || 'Sportiva')}</small><h3>${escapeHtml(product.name)}</h3>
        <div class="product-meta">${escapeHtml([product.size && `Cỡ ${product.size}`, product.color].filter(Boolean).join(' · ') || 'Sản phẩm thể thao')}</div>
        <div class="product-bottom"><span class="price">${money(product.price)}</span><span class="stock">${unavailable ? 'Hết hàng' : `Còn ${product.quantity}`}</span></div>
        <button class="button primary" data-add="${product.id}" type="button" ${unavailable ? 'disabled' : ''}>Thêm vào giỏ</button></div></article>`;
    }).join('') : '<div class="empty">Không tìm thấy sản phẩm phù hợp.</div>';
    productsElement.querySelectorAll('[data-add]').forEach(button => button.addEventListener('click', () => addToCart(Number(button.dataset.add))));
  }

  function addToCart(id) {
    const product = products.find(item => item.id === id);
    if (!product) return;
    const quantity = (cart.get(id) || 0) + 1;
    if (quantity > product.quantity) {
      document.getElementById('checkoutError').textContent = `Số lượng ${product.name} vượt quá tồn kho.`;
      return;
    }
    cart.set(id, quantity);
    saveCart();
    document.getElementById('checkoutError').textContent = '';
    renderCart();
  }

  function renderCart() {
    const container = document.getElementById('cartLines');
    const entries = [...cart.entries()];
    if (!entries.length) container.innerHTML = '<div class="empty">Chưa có sản phẩm</div>';
    else container.innerHTML = entries.map(([id, quantity]) => {
      const product = products.find(item => item.id === id);
      if (!product) return '';
      return `<div class="cart-line"><div><b>${escapeHtml(product.name)}</b><br><small>${money(product.price)} × ${quantity}</small></div>
        <div class="cart-controls"><button type="button" data-dec="${id}" aria-label="Giảm số lượng">−</button><span>${quantity}</span><button type="button" data-inc="${id}" aria-label="Tăng số lượng">+</button></div></div>`;
    }).join('');
    container.querySelectorAll('[data-dec]').forEach(button => button.addEventListener('click', () => changeQuantity(Number(button.dataset.dec), -1)));
    container.querySelectorAll('[data-inc]').forEach(button => button.addEventListener('click', () => changeQuantity(Number(button.dataset.inc), 1)));
    const total = entries.reduce((sum, [id, quantity]) => {
      const product = products.find(item => item.id === id);
      return sum + (product ? product.price * quantity : 0);
    }, 0);
    document.getElementById('total').textContent = money(total);
    document.getElementById('cartCount').textContent = entries.reduce((sum, [, quantity]) => sum + quantity, 0);
    document.getElementById('cartShortcut').setAttribute('aria-label',
      `Đi đến giỏ hàng, ${entries.reduce((sum, [, quantity]) => sum + quantity, 0)} sản phẩm`);
  }

  function changeQuantity(id, amount) {
    const quantity = (cart.get(id) || 0) + amount;
    if (quantity <= 0) cart.delete(id);
    else {
      const product = products.find(item => item.id === id);
      if (product && quantity > product.quantity) return;
      cart.set(id, quantity);
    }
    saveCart();
    renderCart();
  }

  async function loadCustomer() {
    const customer = await api('/api/store/me');
    document.getElementById('accountName').textContent = customer.fullName;
    document.getElementById('deliveryPhone').value = customer.phone || '';
    document.getElementById('customerInfo').textContent =
      `${customer.fullName} · ${customer.email}${customer.phone ? ` · ${customer.phone}` : ''}`;
  }

  async function loadOrders() {
    const orders = await api('/api/store/orders');
    const orderList = document.getElementById('orderList');
    orderList.innerHTML = orders.length ? orders.map(order => `<article class="order">
      <div class="order-head"><span>Đơn #${order.id} · ${escapeHtml(order.status)}</span><span>${money(order.totalAmount)}</span></div>
      <small>${new Date(order.orderDate).toLocaleString('vi-VN')} · ${escapeHtml(order.paymentStatus || '')}</small>
      <small>Thanh toán: ${escapeHtml({ BANK_TRANSFER: 'Chuyển khoản', VNPAY: 'VNPay', COD: 'COD' }[order.paymentMethod] || order.paymentMethod || '—')}</small>
      <div class="order-items">${order.items.map(item => `${escapeHtml(item.productName)} × ${item.quantity} — ${money(item.subtotal)}`).join('<br>')}</div>
      <small>Giao đến: ${escapeHtml(order.shippingAddress || '—')} · SĐT: ${escapeHtml(order.customerPhone || '—')}</small>
      ${order.customerNote ? `<div class="order-items">Ghi chú: ${escapeHtml(order.customerNote)}</div>` : ''}
    </article>`).join('') : '<div class="empty">Bạn chưa có đơn hàng nào.</div>';
  }

  async function loadPaymentInfo() {
    paymentInfo = await api('/api/store/payment-info');
    const option = document.getElementById('bankTransferOption');
    const info = document.getElementById('bankTransferInfo');
    const vnPayOption = document.getElementById('vnPayOption');
    option.disabled = !paymentInfo.bankTransferConfigured;
    option.textContent = paymentInfo.bankTransferConfigured
      ? 'Chuyển khoản ngân hàng'
      : 'Chuyển khoản (chưa được cửa hàng cấu hình)';
    if (paymentInfo.bankTransferConfigured) {
      info.innerHTML = `<b>Thông tin chuyển khoản</b><br>Ngân hàng: ${escapeHtml(paymentInfo.bankName)}<br>Số tài khoản: <b>${escapeHtml(paymentInfo.accountNumber)}</b><br>Chủ tài khoản: ${escapeHtml(paymentInfo.accountHolder)}<br>Nội dung: ghi mã đơn sau khi đặt hàng. Cửa hàng sẽ xác nhận sau khi nhận được tiền.`;
    } else {
      info.textContent = 'Cửa hàng chưa cấu hình thông tin tài khoản ngân hàng. Vui lòng chọn COD hoặc liên hệ cửa hàng.';
    }
    vnPayOption.disabled = !paymentInfo.vnPayConfigured;
    vnPayOption.textContent = paymentInfo.vnPayConfigured
      ? 'Thanh toán qua VNPay'
      : 'VNPay (chưa được cửa hàng cấu hình)';
    info.hidden = document.getElementById('payment').value !== 'BANK_TRANSFER';
  }

  document.getElementById('loginTab').addEventListener('click', () => switchTab(false));
  document.getElementById('registerTab').addEventListener('click', () => switchTab(true));
  document.getElementById('openLogin').addEventListener('click', () => showLogin());
  document.getElementById('closeAuth').addEventListener('click', () => { authScreen.hidden = true; });
  authScreen.addEventListener('click', event => {
    if (event.target === authScreen) authScreen.hidden = true;
  });
  document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && !authScreen.hidden) authScreen.hidden = true;
  });
  document.getElementById('search').addEventListener('input', renderProducts);
  document.getElementById('cartShortcut').addEventListener('click', () => {
    const cartBox = document.getElementById('cartBox');
    cartBox.scrollIntoView({ behavior: 'smooth', block: 'center' });
    cartBox.querySelector('h2').focus({ preventScroll: true });
  });
  document.getElementById('payment').addEventListener('change', event => {
    document.getElementById('bankTransferInfo').hidden = event.target.value !== 'BANK_TRANSFER';
  });
  document.getElementById('refreshOrders').addEventListener('click', async () => {
    try {
      await loadOrders();
    } catch (error) {
      document.getElementById('checkoutError').textContent = error.message || 'Không thể cập nhật đơn hàng.';
    }
  });

  loginForm.addEventListener('submit', async event => {
    event.preventDefault();
    document.getElementById('loginError').textContent = '';
    try {
      await loadCsrf();
      const info = await api('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify(Object.fromEntries(new FormData(loginForm)))
      });
      if (!info.roles.includes('CUSTOMER')) {
        await api('/api/auth/logout', { method: 'POST' });
        window.location.replace('/');
        return;
      }
      loginForm.reset();
      await showStore();
    } catch (error) {
      document.getElementById('loginError').textContent = error.message || 'Đăng nhập thất bại.';
    }
  });

  registerForm.addEventListener('submit', async event => {
    event.preventDefault();
    const data = Object.fromEntries(new FormData(registerForm));
    const errorElement = document.getElementById('registerError');
    errorElement.textContent = '';
    if (data.password !== data.confirmPassword) {
      errorElement.textContent = 'Mật khẩu nhập lại không khớp.';
      return;
    }
    delete data.confirmPassword;
    try {
      await loadCsrf();
      await api('/api/auth/customer/register', { method: 'POST', body: JSON.stringify(data) });
      registerForm.reset();
      switchTab(false);
      document.getElementById('loginError').textContent = 'Tạo tài khoản thành công. Hãy đăng nhập để mua hàng.';
    } catch (error) {
      errorElement.textContent = error.message || 'Không thể tạo tài khoản.';
    }
  });

  document.getElementById('checkout').addEventListener('click', async () => {
    const errorElement = document.getElementById('checkoutError');
    errorElement.textContent = '';
    errorElement.style.color = '';
    if (!cart.size) {
      errorElement.textContent = 'Vui lòng thêm sản phẩm vào giỏ hàng.';
      return;
    }
    if (!customerAuthenticated) {
      showLogin('Đăng nhập hoặc tạo tài khoản để tiếp tục đặt hàng.');
      return;
    }
    const shippingAddress = document.getElementById('address').value.trim();
    if (!shippingAddress) {
      errorElement.textContent = 'Vui lòng nhập địa chỉ nhận hàng.';
      document.getElementById('address').focus();
      return;
    }
    const customerPhone = document.getElementById('deliveryPhone').value.trim();
    if (!/^[0-9+() .-]{8,20}$/.test(customerPhone)) {
      errorElement.textContent = 'Vui lòng nhập số điện thoại nhận hàng hợp lệ.';
      document.getElementById('deliveryPhone').focus();
      return;
    }
    if (document.getElementById('payment').value === 'BANK_TRANSFER' && !paymentInfo?.bankTransferConfigured) {
      errorElement.textContent = 'Cửa hàng chưa cấu hình tài khoản nhận chuyển khoản. Vui lòng chọn COD.';
      return;
    }
    try {
      const result = await api('/api/checkout/customer', {
        method: 'POST',
        body: JSON.stringify({
          shippingAddress,
          customerPhone,
          customerNote: document.getElementById('customerNote').value.trim(),
          paymentMethod: document.getElementById('payment').value,
          items: [...cart.entries()].map(([productId, quantity]) => ({ productId, quantity }))
        })
      });
      cart.clear();
      saveCart();
      renderCart();
      if (result.paymentUrl) {
        window.location.assign(result.paymentUrl);
        return;
      }
      document.getElementById('customerNote').value = '';
      await Promise.all([loadProducts(), loadOrders()]);
      errorElement.textContent = document.getElementById('payment').value === 'BANK_TRANSFER'
        ? 'Đã nhận đơn. Vui lòng chuyển khoản theo thông tin phía trên; đơn chỉ được xác nhận thanh toán sau khi cửa hàng kiểm tra.'
        : 'Đặt hàng thành công. Bạn thanh toán khi nhận hàng.';
      errorElement.style.color = 'var(--green)';
    } catch (error) {
      errorElement.style.color = '#a33';
      errorElement.textContent = error.message || 'Không thể đặt hàng.';
    }
  });

  document.getElementById('logout').addEventListener('click', async () => {
    try {
      await api('/api/auth/logout', { method: 'POST' });
      await loadCsrf();
      showGuestStore();
    } catch (error) {
      document.getElementById('checkoutError').textContent = error.message || 'Không thể đăng xuất.';
    }
  });

  document.getElementById('switchToAdmin').addEventListener('click', async event => {
    const button = event.currentTarget;
    if (!customerAuthenticated) {
      window.location.assign('/');
      return;
    }
    button.disabled = true;
    try {
      await loadCsrf();
      try {
        await api('/api/auth/logout', { method: 'POST' });
      } catch (error) {
        if (error.status !== 401) throw error;
      }
      window.location.assign('/');
    } catch (error) {
      button.disabled = false;
      const message = error.message || 'Không thể chuyển sang giao diện quản lý.';
      document.getElementById('checkoutError').textContent = message;
      document.getElementById('checkoutError').style.color = '#a33';
    }
  });

  async function init() {
    const paymentResult = new URLSearchParams(window.location.search).get('payment');
    try {
      await loadCsrf();
      await loadPaymentInfo();
      const info = await api('/api/auth/me');
      if (!info.roles.includes('CUSTOMER')) {
        showGuestStore();
        await loadProducts();
        return;
      }
      await showStore();
    } catch (error) {
      if (error.status === 401) {
        showGuestStore();
        await loadProducts();
      } else {
        productsElement.innerHTML = `<div class="empty">${escapeHtml(error.message || 'Không thể tải cửa hàng.')}</div>`;
      }
    }
    if (paymentResult === 'success' || paymentResult === 'failed') {
      const message = document.getElementById('checkoutError');
      message.textContent = paymentResult === 'success'
        ? 'VNPay đã xác nhận thanh toán. Bạn có thể xem trạng thái đơn hàng bên dưới.'
        : 'Thanh toán VNPay chưa thành công hoặc chưa được xác nhận. Kiểm tra đơn hàng hoặc liên hệ cửa hàng.';
      message.style.color = paymentResult === 'success' ? 'var(--green)' : '#a33';
      window.history.replaceState(null, '', window.location.pathname + window.location.hash);
    }
  }

  renderCart();
  switchTab(false);
  init();
})();
