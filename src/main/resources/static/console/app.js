const state = {
  token: localStorage.getItem("reycom.token") || "",
  user: readJson("reycom.user", null),
  products: [],
  categories: [],
  cart: null,
  orders: [],
  payments: [],
  notifications: [],
  inventory: [],
  adminOrders: [],
  adminPayments: [],
  productPage: { page: 0, totalPages: 1, totalElements: 0 },
  shop: { search: "", categoryId: "", sort: "createdAt,desc", page: 0 },
  authTab: "login",
  adminSection: "overview",
  busy: false,
  health: null,
};

const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

function readJson(key, fallback) {
  try { return JSON.parse(localStorage.getItem(key) || JSON.stringify(fallback)); }
  catch { return fallback; }
}

function apiBase() {
  const configured = window.REYCOM_CONFIG?.apiBaseUrl?.trim();
  if (configured) return configured.replace(/\/$/, "");
  return location.port === "3000" ? "http://localhost:8080" : "";
}

function escapeHtml(value = "") {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function formatMoney(value) {
  return new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 2 }).format(Number(value || 0));
}

function formatDate(value, withTime = false) {
  if (!value) return "—";
  return new Intl.DateTimeFormat("en-IN", {
    day: "numeric", month: "short", year: "numeric",
    ...(withTime ? { hour: "numeric", minute: "2-digit" } : {}),
  }).format(new Date(value));
}

function initials(name = "Guest") {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]).join("").toUpperCase();
}

function categoryEmoji(product = {}) {
  const text = `${product.name || ""} ${product.category?.name || ""}`.toLowerCase();
  if (/phone|mobile/.test(text)) return "📱";
  if (/laptop|computer/.test(text)) return "💻";
  if (/keyboard/.test(text)) return "⌨️";
  if (/head|audio|speaker/.test(text)) return "🎧";
  if (/watch/.test(text)) return "⌚";
  if (/camera/.test(text)) return "📷";
  if (/shoe|fashion|cloth/.test(text)) return "👟";
  if (/book/.test(text)) return "📚";
  if (/home|furniture/.test(text)) return "🪑";
  if (/beauty|care/.test(text)) return "✨";
  return "📦";
}

function productVisual(product, compact = false) {
  if (product?.imageUrl) {
    return `<img src="${escapeHtml(product.imageUrl)}" alt="${escapeHtml(product.name)}" loading="lazy" onerror="this.parentElement.innerHTML='<div class=&quot;product-art${compact ? " compact" : ""}&quot;><span class=&quot;art-icon&quot;>📦</span></div>'">`;
  }
  return `<div class="product-art${compact ? " compact" : ""}"><span class="art-icon">${categoryEmoji(product)}</span></div>`;
}

function toast(message, detail = "", type = "success") {
  const region = $("#toastRegion");
  const element = document.createElement("div");
  element.className = `toast ${type === "error" ? "error" : ""}`;
  element.innerHTML = `<div class="toast-icon">${type === "error" ? "!" : "✓"}</div><div><strong>${escapeHtml(message)}</strong>${detail ? `<span>${escapeHtml(detail)}</span>` : ""}</div>`;
  region.append(element);
  setTimeout(() => element.remove(), 3400);
}

async function api(path, options = {}, { quiet = false } = {}) {
  const headers = { ...(options.body !== undefined ? { "Content-Type": "application/json" } : {}), ...(options.headers || {}) };
  if (state.token) headers.Authorization = `Bearer ${state.token}`;
  let response;
  try {
    response = await fetch(`${apiBase()}${path}`, { ...options, headers });
  } catch (cause) {
    const error = new Error("The ReyCom API is unavailable. Start Docker Compose and try again.", { cause });
    if (!quiet) toast("Could not reach ReyCom", error.message, "error");
    throw error;
  }
  const text = await response.text();
  let payload = null;
  try { payload = text ? JSON.parse(text) : null; } catch { payload = { message: text }; }
  if (!response.ok) {
    const error = new Error(payload?.message || `Request failed (${response.status})`);
    error.status = response.status;
    error.payload = payload;
    if (response.status === 401 && state.token) clearSession(false);
    if (!quiet) toast("Request failed", error.message, "error");
    throw error;
  }
  return payload;
}

async function withBusy(button, action) {
  if (state.busy) return;
  state.busy = true;
  const label = button?.textContent;
  if (button) { button.disabled = true; button.textContent = "Please wait…"; }
  try { return await action(); }
  finally {
    state.busy = false;
    if (button) { button.disabled = false; button.textContent = label; }
  }
}

function persistSession(auth) {
  state.token = auth.token;
  state.user = auth.user;
  localStorage.setItem("reycom.token", auth.token);
  localStorage.setItem("reycom.user", JSON.stringify(auth.user));
  syncHeader();
}

function clearSession(showMessage = true) {
  state.token = "";
  state.user = null;
  state.cart = null;
  state.notifications = [];
  localStorage.removeItem("reycom.token");
  localStorage.removeItem("reycom.user");
  syncHeader();
  closeDrawers();
  if (showMessage) toast("Signed out", "Your local session has been cleared.");
  navigate("home");
}

function isAdmin() { return state.user?.role === "ADMIN"; }

function syncHeader() {
  const user = state.user;
  $("#headerAvatar").textContent = initials(user?.fullName || user?.email || "Guest");
  $("#accountGreeting").textContent = user ? "Welcome back" : "Welcome";
  $("#accountLabel").textContent = user ? (user.fullName?.split(" ")[0] || user.email) : "Sign in";
  $$('[data-auth-only]').forEach((el) => el.classList.toggle("hidden", !user));
  $$('[data-admin-only]').forEach((el) => el.classList.toggle("hidden", !isAdmin()));
  updateCartCount();
  updateNotificationCount();
}

function updateCartCount() {
  const count = state.cart?.items?.reduce((sum, item) => sum + item.quantity, 0) || 0;
  $("#cartCount").textContent = count;
}

function updateNotificationCount() {
  const count = state.notifications.filter((item) => !item.read).length;
  $("#notificationCount").textContent = count;
  $("#notificationCount").classList.toggle("hidden", count === 0);
}

function routeInfo() {
  const value = location.hash.replace(/^#\/?/, "") || "home";
  const [route, id] = value.split("/");
  return { route, id };
}

function navigate(route) {
  closeDrawers();
  location.hash = route;
  if (routeInfo().route === route) renderRoute();
}

async function loadPublicData() {
  const [categoryResult, productResult] = await Promise.allSettled([
    api("/api/categories", {}, { quiet: true }),
    api("/api/products?page=0&size=40&sortBy=createdAt&sortDir=desc", {}, { quiet: true }),
  ]);
  state.categories = categoryResult.status === "fulfilled" ? categoryResult.value.data || [] : [];
  if (productResult.status === "fulfilled") {
    const page = productResult.value.data || {};
    state.products = page.content || [];
  }
}

async function loadCart({ quiet = true } = {}) {
  if (!state.user) { state.cart = null; updateCartCount(); return null; }
  try {
    const result = await api("/api/cart", {}, { quiet });
    state.cart = result.data;
  } catch { state.cart = null; }
  updateCartCount();
  return state.cart;
}

async function loadNotifications({ quiet = true } = {}) {
  if (!state.user) return [];
  try {
    const result = await api("/api/notifications", {}, { quiet });
    state.notifications = result.data || [];
  } catch { state.notifications = []; }
  updateNotificationCount();
  return state.notifications;
}

function productCard(product) {
  return `<article class="product-card">
    <div class="product-image" data-action="view-product" data-id="${product.id}">
      ${productVisual(product)}
      <span class="product-tag">${product.active ? "Available" : "Unavailable"}</span>
    </div>
    <div class="product-body">
      <span class="product-category">${escapeHtml(product.category?.name || "ReyCom edit")}</span>
      <h3><a href="#product/${product.id}">${escapeHtml(product.name)}</a></h3>
      <div class="product-description">${escapeHtml(product.description || "A carefully selected product for everyday life.")}</div>
      <div class="price-row"><span class="price">${formatMoney(product.price)}</span><button class="add-button" type="button" data-action="add-to-cart" data-id="${product.id}" aria-label="Add ${escapeHtml(product.name)} to cart">+</button></div>
    </div>
  </article>`;
}

function renderHome() {
  const featured = state.products.slice(0, 8);
  const categories = state.categories.slice(0, 4);
  return `<section class="hero">
    <div class="hero-inner">
      <div class="hero-copy"><span class="eyebrow">A smarter way to shop</span><h1>Find your<br><span>everyday better.</span></h1><p>Discover thoughtfully selected products, enjoy a fast secure checkout, and follow every order from payment to delivery.</p>
        <div class="hero-actions"><a class="primary-button blue" href="#shop">Explore products →</a><a class="secondary-button" href="#orders">Track an order</a></div>
        <div class="hero-trust"><span><i>✓</i> Secure payments</span><span><i>✓</i> Live order updates</span><span><i>✓</i> Easy cancellation</span></div>
      </div>
      <div class="hero-visual" aria-label="Featured ReyCom products"><div class="visual-card main"><div class="product-art"><span class="art-icon">🎧</span></div></div><div class="visual-card small"><div class="product-art compact"><span class="art-icon">⌚</span></div></div><div class="hero-float rating"><strong>4.9 ★</strong><span>Customer experience</span></div><div class="hero-float delivery"><strong>Live tracking</strong><span>From order to delivery</span></div></div>
    </div>
  </section>
  <section class="benefits"><div class="benefit-grid">
    <div class="benefit"><span class="benefit-icon">🚚</span><div><strong>Free delivery</strong><span>On orders above ₹999</span></div></div>
    <div class="benefit"><span class="benefit-icon">🔒</span><div><strong>Secure checkout</strong><span>JWT-protected purchase flow</span></div></div>
    <div class="benefit"><span class="benefit-icon">↻</span><div><strong>Simple ordering</strong><span>Cart, pay and track in one place</span></div></div>
    <div class="benefit"><span class="benefit-icon">🔔</span><div><strong>Live notifications</strong><span>Kafka-powered status updates</span></div></div>
  </div></section>
  <section class="page-shell">
    <div class="section-head"><div><span class="eyebrow">Browse collections</span><h2>Shop by category</h2></div><a class="text-link" href="#shop">View all products →</a></div>
    <div class="category-grid">${categories.length ? categories.map((category, index) => `<a class="category-card" href="#shop" data-action="choose-category" data-id="${category.id}"><span class="category-icon">${["💻","🎧","⌚","🏠"][index] || "📦"}</span><div><strong>${escapeHtml(category.name)}</strong><span>${escapeHtml(category.description || "Explore the collection")}</span></div></a>`).join("") : ["Electronics","Everyday essentials","Work & study","Home favourites"].map((name, index) => `<a class="category-card" href="#shop"><span class="category-icon">${["💻","✨","📚","🏠"][index]}</span><div><strong>${name}</strong><span>Explore products</span></div></a>`).join("")}</div>
  </section>
  <section class="page-shell" style="padding-top:0">
    <div class="section-head"><div><span class="eyebrow">Fresh on ReyCom</span><h2>Featured products</h2><p>Products created and managed through the ReyCom administration workspace.</p></div><a class="text-link" href="#shop">Shop all →</a></div>
    ${featured.length ? `<div class="product-grid">${featured.map(productCard).join("")}</div>` : emptyState("📦", "Your storefront is ready", "Sign in as an Admin to add categories, products and inventory. They will appear here instantly.", "Open Admin", "admin")}
    <div class="promo-strip"><div class="promo-copy"><span class="eyebrow light">Built for confidence</span><h2>Every purchase has a story you can follow.</h2><p>ReyCom records order and payment events, sends asynchronous notifications, and makes the complete journey transparent.</p><a class="primary-button" style="background:white;color:#101828;margin-top:1rem" href="#orders">View your orders</a></div><div class="promo-visual">📦</div></div>
  </section>`;
}

function emptyState(icon, title, message, actionLabel = "", route = "") {
  return `<div class="empty-state"><div class="empty-icon">${icon}</div><h2>${escapeHtml(title)}</h2><p>${escapeHtml(message)}</p>${actionLabel ? `<a class="primary-button blue" href="#${route}">${escapeHtml(actionLabel)}</a>` : ""}</div>`;
}

async function fetchShopProducts() {
  const [sortBy, sortDir] = state.shop.sort.split(",");
  const params = new URLSearchParams({ page: String(state.shop.page), size: "12", sortBy, sortDir });
  if (state.shop.search) params.set("search", state.shop.search);
  if (state.shop.categoryId) params.set("categoryId", state.shop.categoryId);
  const result = await api(`/api/products?${params}`, {}, { quiet: true });
  const page = result.data || {};
  state.products = page.content || [];
  state.productPage = { page: page.number ?? state.shop.page, totalPages: page.totalPages || Math.max(1, Math.ceil((page.totalElements || 0) / 12)), totalElements: page.totalElements || 0 };
}

function renderShop() {
  const pages = Array.from({ length: state.productPage.totalPages }, (_, index) => index).slice(0, 7);
  return `<section class="page-title"><div><span class="eyebrow">The ReyCom collection</span><h1>Shop all products</h1><p>Search, filter, compare, and add products to your cart.</p></div></section>
  <section class="page-shell"><div class="shop-layout">
    <aside class="filters"><div class="filter-section"><h3>Categories</h3><button class="filter-option ${!state.shop.categoryId ? "active" : ""}" data-action="filter-category" data-id="">All products <span>${state.productPage.totalElements}</span></button>${state.categories.map((category) => `<button class="filter-option ${state.shop.categoryId === category.id ? "active" : ""}" data-action="filter-category" data-id="${category.id}">${escapeHtml(category.name)} <span>→</span></button>`).join("")}</div><div class="filter-section"><h3>Shopping confidently</h3><div class="health-list"><div class="health-row">Secure checkout <span>✓</span></div><div class="health-row">Live inventory <span>✓</span></div><div class="health-row">Order tracking <span>✓</span></div></div></div></aside>
    <div><div class="shop-toolbar"><p>${state.productPage.totalElements} product${state.productPage.totalElements === 1 ? "" : "s"}${state.shop.search ? ` matching “${escapeHtml(state.shop.search)}”` : ""}</p><select id="shopSort" aria-label="Sort products"><option value="createdAt,desc" ${state.shop.sort === "createdAt,desc" ? "selected" : ""}>Newest first</option><option value="price,asc" ${state.shop.sort === "price,asc" ? "selected" : ""}>Price: low to high</option><option value="price,desc" ${state.shop.sort === "price,desc" ? "selected" : ""}>Price: high to low</option><option value="name,asc" ${state.shop.sort === "name,asc" ? "selected" : ""}>Name: A–Z</option></select></div>
      ${state.products.length ? `<div class="product-grid">${state.products.map(productCard).join("")}</div><div class="pagination">${pages.map((page) => `<button class="${page === state.productPage.page ? "active" : ""}" data-action="shop-page" data-page="${page}">${page + 1}</button>`).join("")}</div>` : emptyState("🔎", "No products found", "Try another search term or choose a different category.", "Clear filters", "shop")}
    </div>
  </div></section>`;
}

function signedOutPage(title, message) {
  return `<div class="signed-out-prompt"><div>${emptyState("🔐", title, message)}</div><button class="primary-button blue" type="button" data-action="open-auth">Sign in to continue</button></div>`;
}

async function loadOrders() {
  const [orderResult, paymentResult] = await Promise.all([
    api("/api/orders"), api("/api/payments", {}, { quiet: true }),
  ]);
  state.orders = orderResult.data || [];
  state.payments = paymentResult?.data || [];
}

function renderOrders() {
  if (!state.user) return signedOutPage("Sign in to see your orders", "Track purchases, payments, cancellations, and every order event from your account.");
  return `<section class="page-title"><div><span class="eyebrow">Your purchases</span><h1>Orders</h1><p>Follow every ReyCom order from creation through payment and delivery.</p></div></section><section class="page-shell"><div class="content-card"><div class="card-head"><div><h2>Order history</h2><p>${state.orders.length} order${state.orders.length === 1 ? "" : "s"} on your account</p></div><a class="secondary-button" href="#shop">Continue shopping</a></div>${state.orders.length ? `<div class="order-list">${state.orders.map(orderCard).join("")}</div>` : emptyState("🛍️", "No orders yet", "Add a product to your cart and complete checkout to see it here.", "Start shopping", "shop")}</div><div id="orderEventPanel"></div></section>`;
}

function orderCard(order, admin = false) {
  const payment = (admin ? state.adminPayments : state.payments).find((item) => item.orderId === order.orderId);
  return `<article class="order-card"><div class="order-top"><div><strong>${escapeHtml(order.orderNumber)}</strong><div class="order-meta"><span>${formatDate(order.createdAt, true)}</span><span>${order.items?.length || 0} item(s)</span><span>${formatMoney(order.totalAmount)}</span>${payment ? `<span>Payment: ${escapeHtml(payment.status)}</span>` : ""}</div></div><span class="status-pill ${String(order.status).toLowerCase()}">${escapeHtml(String(order.status).replaceAll("_", " "))}</span></div><div class="order-items-preview">${(order.items || []).slice(0, 3).map((item) => `<span class="order-item-pill">${escapeHtml(item.productName)} × ${item.quantity}</span>`).join("")}</div><div class="order-actions"><button class="small-button" data-action="view-events" data-id="${order.orderId}" data-admin="${admin}">View timeline</button>${!admin && ["CREATED","CONFIRMED","PAYMENT_PENDING"].includes(order.status) ? `<button class="small-button" data-action="cancel-order" data-id="${order.orderId}">Cancel order</button>` : ""}${admin ? `<select class="form-control" style="width:auto;min-height:34px" data-order-status="${order.orderId}">${["CREATED","CONFIRMED","PAYMENT_PENDING","PAID","CANCELLED","SHIPPED","DELIVERED","FAILED"].map((status) => `<option ${status === order.status ? "selected" : ""}>${status}</option>`).join("")}</select><button class="small-button" data-action="update-order" data-id="${order.orderId}">Update</button>` : ""}</div></article>`;
}

async function renderOrderEvents(orderId, admin = false) {
  const path = admin ? `/api/admin/orders/${orderId}/events` : `/api/orders/${orderId}/events`;
  const result = await api(path);
  const events = result.data || [];
  const target = $(admin ? "#adminDetailPanel" : "#orderEventPanel");
  if (!target) return;
  target.innerHTML = `<div class="content-card"><div class="card-head"><div><h2>Order timeline</h2><p>Event history stored in DynamoDB</p></div></div>${events.length ? `<div class="timeline">${events.map((event) => `<div class="timeline-item"><span class="timeline-dot"></span><div><strong>${escapeHtml(String(event.eventType).replaceAll("_", " "))}</strong><span>${escapeHtml(event.message || "Order updated")} · ${formatDate(event.eventTime, true)}</span></div></div>`).join("")}</div>` : `<p style="color:var(--muted);font-size:.72rem">No events have been recorded yet.</p>`}</div>`;
  target.scrollIntoView({ behavior: "smooth", block: "center" });
}

function renderCheckout() {
  if (!state.user) return signedOutPage("Sign in before checkout", "Your account keeps orders, payments, and notifications securely connected.");
  if (!state.cart?.items?.length) return `<section class="page-shell">${emptyState("🛒", "Your cart is empty", "Browse the collection and add something before checkout.", "Shop products", "shop")}</section>`;
  return `<section class="page-title"><div><span class="eyebrow">Secure checkout</span><h1>Complete your order</h1><p>Review your basket and choose how you would like to pay.</p></div></section><section class="page-shell"><div class="checkout-layout"><div>
    <div class="checkout-steps"><div class="checkout-step active">1 · Review</div><div class="checkout-step active">2 · Payment</div><div class="checkout-step">3 · Confirmation</div></div>
    <form class="content-card" id="checkoutForm"><div class="card-head"><div><h2>Payment method</h2><p>This demo simulates a successful payment through the real payment APIs.</p></div></div><div class="payment-methods">${[["UPI","📱","UPI"],["CARD","💳","Card"],["NET_BANKING","🏦","Net banking"],["WALLET","👛","Wallet"],["CASH_ON_DELIVERY","💵","Cash on delivery"]].map(([value,icon,label], index) => `<label class="payment-option"><input type="radio" name="paymentMethod" value="${value}" ${index === 0 ? "checked" : ""}><span>${icon}</span><strong>${label}</strong></label>`).join("")}</div><button class="primary-button blue" style="margin-top:1rem" type="submit">Pay ${formatMoney(state.cart.totalAmount)} securely</button></form>
    <div class="content-card"><div class="card-head"><div><h3>What happens next?</h3><p>ReyCom creates an order, records an event, processes payment, and sends a notification through Kafka.</p></div></div></div>
  </div><aside class="content-card sticky-summary"><div class="card-head"><h2>Order summary</h2></div><div class="checkout-items">${state.cart.items.map((item) => `<div class="checkout-item"><span>${escapeHtml(item.productName)} × ${item.quantity}</span><span>${formatMoney(item.lineTotal)}</span></div>`).join("")}</div><div class="summary-row"><span>Subtotal</span><strong>${formatMoney(state.cart.totalAmount)}</strong></div><div class="summary-row"><span>Demo delivery</span><strong>Free</strong></div><div class="summary-row total"><span>Total</span><span>${formatMoney(state.cart.totalAmount)}</span></div></aside></div></section>`;
}

async function completeCheckout(form, button) {
  await withBusy(button, async () => {
    const method = new FormData(form).get("paymentMethod");
    const orderResult = await api("/api/orders", { method: "POST" });
    const order = orderResult.data;
    const paymentResult = await api(`/api/orders/${order.orderId}/payments`, { method: "POST", body: JSON.stringify({ paymentMethod: method }) });
    await api(`/api/payments/${paymentResult.data.paymentId}/success`, { method: "POST" });
    state.cart = null;
    updateCartCount();
    toast("Payment successful", `${order.orderNumber} is confirmed and now appears in your order history.`);
    await loadNotifications({ quiet: true });
    navigate("orders");
  });
}

function renderNotifications() {
  if (!state.user) return signedOutPage("Sign in to view notifications", "Order and payment updates will appear here in real time.");
  return `<section class="page-title"><div><span class="eyebrow">Stay informed</span><h1>Notifications</h1><p>Asynchronous updates created by ReyCom's Kafka consumers.</p></div></section><section class="page-shell"><div class="content-card"><div class="card-head"><div><h2>Recent activity</h2><p>${state.notifications.filter((item) => !item.read).length} unread notification(s)</p></div><button class="secondary-button" data-action="refresh-notifications">Refresh</button></div>${state.notifications.length ? `<div class="notification-list">${state.notifications.map((item) => `<article class="notification-card ${item.read ? "" : "unread"}"><span class="notification-icon">${/PAYMENT/.test(item.eventType) ? "💳" : "📦"}</span><div><h3>${escapeHtml(item.title)}</h3><p>${escapeHtml(item.message)}</p></div><div><time>${formatDate(item.createdAt, true)}</time>${!item.read ? `<button class="small-button" style="margin-top:.4rem" data-action="read-notification" data-id="${item.id}">Mark read</button>` : ""}</div></article>`).join("")}</div>` : emptyState("🔔", "You're all caught up", "Order and payment updates will appear here after your next purchase.")}</div></section>`;
}

function renderAccount() {
  if (!state.user) return signedOutPage("Your ReyCom account", "Sign in or create an account to shop, place orders, and receive live updates.");
  const profiles = readJson("reycom.testProfiles", { admin: { email: "admin@reycom.local", password: "" }, client: { email: "helen@reycom.local", password: "" } });
  return `<section class="page-title"><div><span class="eyebrow">Personal space</span><h1>My account</h1><p>Manage your ReyCom session and demo identities.</p></div></section><section class="page-shell"><div class="account-layout"><aside class="account-sidebar"><div class="account-profile"><span class="avatar">${initials(state.user.fullName)}</span><strong>${escapeHtml(state.user.fullName)}</strong><span>${escapeHtml(state.user.email)}</span></div><nav class="side-nav"><a class="active" href="#account">Profile</a><a href="#orders">Orders</a><a href="#notifications">Notifications</a><button data-action="logout">Sign out</button></nav></aside><div>
    <div class="content-card"><div class="card-head"><div><h2>Profile details</h2><p>Your authenticated account from the ReyCom API</p></div><span class="status-pill success">${escapeHtml(state.user.role)}</span></div><div class="form-grid"><div class="field"><label>Full name</label><input value="${escapeHtml(state.user.fullName)}" disabled></div><div class="field"><label>Email</label><input value="${escapeHtml(state.user.email)}" disabled></div><div class="field"><label>Member since</label><input value="${formatDate(state.user.createdAt)}" disabled></div><div class="field"><label>Account role</label><input value="${escapeHtml(state.user.role)}" disabled></div></div></div>
    <form class="content-card" id="demoProfilesForm"><div class="card-head"><div><h2>Demo quick switch</h2><p>Save local Admin and Client credentials once, then switch roles from the account menu.</p></div></div><div class="form-grid"><div class="field"><label>Admin email</label><input name="adminEmail" type="email" value="${escapeHtml(profiles.admin?.email || "admin@reycom.local")}"></div><div class="field"><label>Admin password</label><input name="adminPassword" type="password" value="${escapeHtml(profiles.admin?.password || "")}" placeholder="Stored only in this browser"></div><div class="field"><label>Client email</label><input name="clientEmail" type="email" value="${escapeHtml(profiles.client?.email || "helen@reycom.local")}"></div><div class="field"><label>Client password</label><input name="clientPassword" type="password" value="${escapeHtml(profiles.client?.password || "")}" placeholder="Stored only in this browser"></div></div><div style="display:flex;gap:.5rem;margin-top:1rem"><button class="primary-button blue" type="submit">Save demo profiles</button><button class="danger-button" type="button" data-action="clear-profiles">Clear profiles</button></div></form>
  </div></div></section>`;
}

async function loadAdminData() {
  const results = await Promise.allSettled([
    api("/api/admin/inventory"), api("/api/admin/orders"), api("/api/admin/payments"),
    api("/api/products?page=0&size=100&sortBy=createdAt&sortDir=desc"), api("/api/categories"),
  ]);
  state.inventory = results[0].status === "fulfilled" ? results[0].value.data || [] : [];
  state.adminOrders = results[1].status === "fulfilled" ? results[1].value.data || [] : [];
  state.adminPayments = results[2].status === "fulfilled" ? results[2].value.data || [] : [];
  state.products = results[3].status === "fulfilled" ? results[3].value.data?.content || [] : state.products;
  state.categories = results[4].status === "fulfilled" ? results[4].value.data || [] : state.categories;
}

function adminSidebar() {
  return `<aside class="admin-sidebar"><span class="eyebrow" style="padding:.65rem .75rem">Operations</span><nav class="side-nav">${[["overview","Overview"],["catalog","Catalog"],["inventory","Inventory"],["orders","Orders"],["payments","Payments"]].map(([id,label]) => `<button class="${state.adminSection === id ? "active" : ""}" data-action="admin-section" data-section="${id}">${label}</button>`).join("")}</nav></aside>`;
}

function renderAdmin() {
  if (!state.user) return signedOutPage("Admin authentication required", "Sign in with an administrator account to manage ReyCom operations.");
  if (!isAdmin()) return `<section class="page-shell">${emptyState("⛔", "Administrator access required", "Your current account is a customer. Switch to the saved Admin demo identity from the account menu.")}</section>`;
  let content = "";
  if (state.adminSection === "overview") content = renderAdminOverview();
  if (state.adminSection === "catalog") content = renderAdminCatalog();
  if (state.adminSection === "inventory") content = renderAdminInventory();
  if (state.adminSection === "orders") content = renderAdminOrders();
  if (state.adminSection === "payments") content = renderAdminPayments();
  return `<section class="page-title"><div><span class="eyebrow">ReyCom operations</span><h1>Admin workspace</h1><p>Manage the catalogue, inventory, orders, and payments from one role-protected application.</p></div></section><section class="page-shell"><div class="admin-layout">${adminSidebar()}<div>${content}<div id="adminDetailPanel"></div></div></div></section>`;
}

function renderAdminOverview() {
  const paid = state.adminOrders.filter((order) => order.status === "PAID").length;
  const revenue = state.adminPayments.filter((payment) => payment.status === "SUCCESS").reduce((sum, payment) => sum + Number(payment.amount), 0);
  return `<div class="admin-grid"><div class="stat-card"><span>Products</span><strong>${state.products.length}</strong></div><div class="stat-card"><span>Orders</span><strong>${state.adminOrders.length}</strong></div><div class="stat-card"><span>Paid orders</span><strong>${paid}</strong></div><div class="stat-card"><span>Captured revenue</span><strong>${formatMoney(revenue)}</strong></div></div><div class="content-card"><div class="card-head"><div><h2>Recent orders</h2><p>Latest customer activity</p></div><button class="secondary-button" data-action="admin-section" data-section="orders">Manage orders</button></div><div class="order-list">${state.adminOrders.slice(0, 5).map((order) => orderCard(order, true)).join("") || `<p style="color:var(--muted)">No orders yet.</p>`}</div></div>`;
}

function renderAdminCatalog() {
  return `<div class="admin-forms"><form class="content-card" id="categoryAdminForm"><div class="card-head"><div><h2>New category</h2><p>Create a storefront collection</p></div></div><div class="form-stack"><div class="field"><label>Name</label><input name="name" required placeholder="Electronics"></div><div class="field"><label>Description</label><textarea name="description" placeholder="Collection description"></textarea></div><button class="primary-button blue" type="submit">Create category</button></div></form><form class="content-card" id="productAdminForm"><div class="card-head"><div><h2>New product</h2><p>Add a sellable product to ReyCom</p></div></div><div class="form-grid"><div class="field"><label>Name</label><input name="name" required></div><div class="field"><label>SKU</label><input name="sku" required placeholder="DEMO-001"></div><div class="field"><label>Price</label><input name="price" type="number" min="0.01" step="0.01" required></div><div class="field"><label>Category</label><select name="categoryId" required><option value="">Choose category</option>${state.categories.map((category) => `<option value="${category.id}">${escapeHtml(category.name)}</option>`).join("")}</select></div><div class="field wide"><label>Description</label><textarea name="description"></textarea></div><div class="field wide"><label>Image URL (optional)</label><input name="imageUrl" type="url"></div><button class="primary-button blue wide" type="submit">Create product</button></div></form></div><div class="content-card"><div class="card-head"><div><h2>Products</h2><p>${state.products.length} active product(s)</p></div></div>${state.products.length ? `<div class="data-table-wrap"><table class="data-table"><thead><tr><th>Product</th><th>Category</th><th>SKU</th><th>Price</th><th>Action</th></tr></thead><tbody>${state.products.map((product) => `<tr><td>${escapeHtml(product.name)}</td><td>${escapeHtml(product.category?.name || "—")}</td><td>${escapeHtml(product.sku)}</td><td>${formatMoney(product.price)}</td><td><button class="small-button" data-action="delete-product" data-id="${product.id}">Remove</button></td></tr>`).join("")}</tbody></table></div>` : `<p style="color:var(--muted)">No products yet.</p>`}</div>`;
}

function renderAdminInventory() {
  return `<form class="content-card" id="inventoryAdminForm"><div class="card-head"><div><h2>Create inventory</h2><p>Make a product available for purchase</p></div></div><div class="form-grid"><div class="field wide"><label>Product</label><select name="productId" required><option value="">Choose product</option>${state.products.map((product) => `<option value="${product.id}">${escapeHtml(product.name)} · ${escapeHtml(product.sku)}</option>`).join("")}</select></div><div class="field"><label>Available</label><input name="quantityAvailable" type="number" min="0" value="25" required></div><div class="field"><label>Reserved</label><input name="reservedQuantity" type="number" min="0" value="0" required></div><div class="field"><label>Low stock threshold</label><input name="lowStockThreshold" type="number" min="0" value="5" required></div><button class="primary-button blue" type="submit">Create inventory</button></div></form><div class="content-card"><div class="card-head"><div><h2>Stock levels</h2><p>Live available and reserved quantities</p></div></div>${state.inventory.length ? `<div class="data-table-wrap"><table class="data-table"><thead><tr><th>Product</th><th>Available</th><th>Reserved</th><th>Sellable</th><th>Health</th></tr></thead><tbody>${state.inventory.map((item) => `<tr><td>${escapeHtml(item.productName)}</td><td>${item.quantityAvailable}</td><td>${item.reservedQuantity}</td><td>${item.availableToSell}</td><td><span class="status-pill ${item.lowStock ? "pending" : "success"}">${item.lowStock ? "Low stock" : "Healthy"}</span></td></tr>`).join("")}</tbody></table></div>` : `<p style="color:var(--muted)">No inventory records yet.</p>`}</div>`;
}

function renderAdminOrders() {
  return `<div class="content-card"><div class="card-head"><div><h2>All orders</h2><p>Update fulfilment state and inspect event timelines</p></div></div><div class="order-list">${state.adminOrders.length ? state.adminOrders.map((order) => orderCard(order, true)).join("") : `<p style="color:var(--muted)">No orders yet.</p>`}</div></div>`;
}

function renderAdminPayments() {
  return `<div class="content-card"><div class="card-head"><div><h2>All payments</h2><p>Payment operations across every customer</p></div></div>${state.adminPayments.length ? `<div class="data-table-wrap"><table class="data-table"><thead><tr><th>Payment</th><th>Order</th><th>Method</th><th>Amount</th><th>Status</th><th>Created</th></tr></thead><tbody>${state.adminPayments.map((payment) => `<tr><td>${escapeHtml(payment.paymentNumber)}</td><td>${escapeHtml(payment.orderNumber)}</td><td>${escapeHtml(payment.paymentMethod)}</td><td>${formatMoney(payment.amount)}</td><td><span class="status-pill ${String(payment.status).toLowerCase()}">${escapeHtml(payment.status)}</span></td><td>${formatDate(payment.createdAt, true)}</td></tr>`).join("")}</tbody></table></div>` : `<p style="color:var(--muted)">No payments yet.</p>`}</div>`;
}

function renderDeveloper() {
  const health = state.health;
  return `<section class="page-title"><div><span class="eyebrow">Engineering view</span><h1>Developer tools</h1><p>Inspect APIs, events, metrics, and the original endpoint testing workspace without leaving ReyCom.</p></div></section><section class="page-shell"><div class="developer-grid">
    <a class="developer-card" href="./developer/"><div><span class="developer-card-icon">⌘</span><h3>API Console</h3><p>Open the original workflow-based console for direct endpoint testing and response inspection.</p></div><span class="text-link">Open console →</span></a>
    <a class="developer-card" href="http://localhost:8080/swagger-ui/index.html" target="_blank" rel="noreferrer"><div><span class="developer-card-icon">{ }</span><h3>Swagger UI</h3><p>Review the OpenAPI contract and exercise individual HTTP endpoints.</p></div><span class="text-link">Open Swagger →</span></a>
    <a class="developer-card" href="http://localhost:8081" target="_blank" rel="noreferrer"><div><span class="developer-card-icon">⚡</span><h3>Kafka UI</h3><p>Inspect order and payment event topics, messages, and consumer activity.</p></div><span class="text-link">Open Kafka UI →</span></a>
    <a class="developer-card" href="http://localhost:9090/targets" target="_blank" rel="noreferrer"><div><span class="developer-card-icon">P</span><h3>Prometheus</h3><p>Verify API scraping and query the application's technical and business metrics.</p></div><span class="text-link">Open Prometheus →</span></a>
    <a class="developer-card" href="http://localhost:3001" target="_blank" rel="noreferrer"><div><span class="developer-card-icon">▥</span><h3>Grafana</h3><p>View request traffic, latency, JVM health, orders, payments, and notifications.</p></div><span class="text-link">Open Grafana →</span></a>
    <div class="developer-card"><div><span class="developer-card-icon">♥</span><h3>System health</h3><p>Current API dependency status.</p></div><div class="health-list">${health?.components ? Object.entries(health.components).filter(([name]) => ["db","redis","ping"].includes(name)).map(([name, value]) => `<div class="health-row"><span>${escapeHtml(name === "db" ? "PostgreSQL" : name === "redis" ? "Redis" : "API")}</span><span class="health-dot ${value.status === "UP" ? "up" : ""}"></span></div>`).join("") : `<div class="health-row"><span>API unavailable</span><span class="health-dot"></span></div>`}</div></div>
  </div></section>`;
}

async function renderRoute() {
  const { route, id } = routeInfo();
  $$("[data-nav]").forEach((link) => link.classList.toggle("active", link.dataset.nav === route));
  $("#mobileNav").classList.remove("open");
  $("#mobileMenuButton").setAttribute("aria-expanded", "false");
  const view = $("#appView");
  view.innerHTML = `<div class="page-loader"><span></span><p>Loading ${escapeHtml(route)}</p></div>`;
  try {
    if (route === "shop") await fetchShopProducts();
    if (route === "orders" && state.user) await loadOrders();
    if (route === "checkout" && state.user) await loadCart({ quiet: false });
    if (route === "notifications" && state.user) await loadNotifications({ quiet: false });
    if (route === "admin" && isAdmin()) await loadAdminData();
    if (route === "developer") await checkHealth();
  } catch { /* The relevant API helper already provides user feedback. */ }
  if (route === "home") view.innerHTML = renderHome();
  else if (route === "shop") view.innerHTML = renderShop();
  else if (route === "product" && id) { view.innerHTML = renderHome(); await showProduct(id); }
  else if (route === "orders") view.innerHTML = renderOrders();
  else if (route === "checkout") view.innerHTML = renderCheckout();
  else if (route === "notifications") view.innerHTML = renderNotifications();
  else if (route === "account") view.innerHTML = renderAccount();
  else if (route === "admin") view.innerHTML = renderAdmin();
  else if (route === "developer") view.innerHTML = renderDeveloper();
  else view.innerHTML = renderHome();
  window.scrollTo({ top: 0, behavior: "instant" });
  view.focus({ preventScroll: true });
}

async function showProduct(id) {
  let product = state.products.find((item) => item.id === id);
  if (!product) {
    try { product = (await api(`/api/products/${id}`)).data; }
    catch { return; }
  }
  $("#productModalContent").innerHTML = `<div class="product-detail"><div class="product-detail-visual">${productVisual(product)}</div><div class="product-detail-copy"><span class="eyebrow">${escapeHtml(product.category?.name || "ReyCom product")}</span><h2>${escapeHtml(product.name)}</h2><p>${escapeHtml(product.description || "A thoughtfully selected product for the ReyCom collection.")}</p><div class="detail-price">${formatMoney(product.price)}</div><div class="detail-meta"><div><span>Product code</span><strong>${escapeHtml(product.sku)}</strong></div><div><span>Availability</span><strong>${product.active ? "Ready to order" : "Unavailable"}</strong></div></div><button class="primary-button blue" type="button" data-action="add-to-cart" data-id="${product.id}">Add to cart</button></div></div>`;
  openModal("productModal");
}

function openModal(id) {
  $("#" + id).hidden = false;
  document.body.classList.add("no-scroll");
}

function closeModal(id) {
  $("#" + id).hidden = true;
  if (!$(".drawer.open")) document.body.classList.remove("no-scroll");
}

function renderAuthForm() {
  $$("[data-auth-tab]").forEach((button) => button.classList.toggle("active", button.dataset.authTab === state.authTab));
  const profiles = readJson("reycom.testProfiles", {});
  $("#authFormContent").innerHTML = state.authTab === "login" ? `<form class="auth-form" id="loginForm"><h2>Welcome back</h2><p>Sign in to continue shopping and manage your orders.</p><div class="form-stack"><div class="field"><label for="loginEmail">Email</label><input id="loginEmail" name="email" type="email" autocomplete="username" required placeholder="you@example.com"></div><div class="field"><label for="loginPassword">Password</label><input id="loginPassword" name="password" type="password" autocomplete="current-password" required placeholder="Your password"></div><div id="authError"></div><button class="primary-button blue auth-submit" type="submit">Sign in</button></div><div class="demo-login"><p>Demo mode: use credentials saved locally in your browser.</p><div class="demo-buttons"><button class="small-button" type="button" data-action="quick-login" data-profile="admin">Admin</button><button class="small-button" type="button" data-action="quick-login" data-profile="client">Client</button></div></div><div class="form-note">No account? <button class="text-link" style="border:0;background:none" type="button" data-auth-tab="signup">Create one free</button></div></form>` : `<form class="auth-form" id="signupForm"><h2>Create account</h2><p>Join ReyCom to build a cart, place orders, and receive live updates.</p><div class="form-stack"><div class="field"><label for="signupName">Full name</label><input id="signupName" name="fullName" autocomplete="name" required placeholder="Your name"></div><div class="field"><label for="signupEmail">Email</label><input id="signupEmail" name="email" type="email" autocomplete="email" required placeholder="you@example.com"></div><div class="field"><label for="signupPassword">Password</label><input id="signupPassword" name="password" type="password" minlength="8" maxlength="72" autocomplete="new-password" required placeholder="At least 8 characters"></div><div id="authError"></div><button class="primary-button blue auth-submit" type="submit">Create ReyCom account</button></div><div class="form-note">Already registered? <button class="text-link" style="border:0;background:none" type="button" data-auth-tab="login">Sign in</button></div></form>`;
  void profiles;
}

function openAuth(tab = "login") {
  state.authTab = tab;
  renderAuthForm();
  openModal("authModal");
  setTimeout(() => $(tab === "login" ? "#loginEmail" : "#signupName")?.focus(), 0);
}

async function submitAuth(form, button, register = false) {
  const values = Object.fromEntries(new FormData(form));
  await withBusy(button, async () => {
    try {
      const result = await api(register ? "/api/auth/register" : "/api/auth/login", { method: "POST", body: JSON.stringify(values) }, { quiet: true });
      persistSession(result.data);
      if (register) rememberProfile("client", values.email, values.password);
      else rememberProfile(result.data.user.role === "ADMIN" ? "admin" : "client", values.email, values.password);
      closeModal("authModal");
      await Promise.all([loadCart(), loadNotifications()]);
      toast(register ? "Account created" : "Welcome back", `Signed in as ${result.data.user.fullName}.`);
      renderRoute();
    } catch (error) {
      $("#authError").innerHTML = `<div class="form-error">${escapeHtml(error.message)}</div>`;
    }
  });
}

function rememberProfile(profile, email, password) {
  const profiles = readJson("reycom.testProfiles", { admin: {}, client: {} });
  profiles[profile] = { email, password };
  localStorage.setItem("reycom.testProfiles", JSON.stringify(profiles));
}

async function quickLogin(profile, button) {
  const credentials = readJson("reycom.testProfiles", {})[profile];
  if (!credentials?.email || !credentials?.password) {
    closeModal("authModal");
    toast(`${profile === "admin" ? "Admin" : "Client"} profile is not configured`, "Sign in once or save credentials from My account.", "error");
    navigate("account");
    return;
  }
  await withBusy(button, async () => {
    try {
      const result = await api("/api/auth/login", { method: "POST", body: JSON.stringify(credentials) }, { quiet: true });
      persistSession(result.data);
      closeModal("authModal");
      closeDrawers();
      await Promise.all([loadCart(), loadNotifications()]);
      toast(`Switched to ${profile === "admin" ? "Admin" : "Client"}`, result.data.user.email);
      navigate(profile === "admin" ? "admin" : "home");
    } catch (error) { toast("Quick login failed", error.message, "error"); }
  });
}

function openDrawer(id) {
  closeDrawers();
  const drawer = $("#" + id);
  drawer.classList.add("open");
  drawer.setAttribute("aria-hidden", "false");
  $("#drawerOverlay").hidden = false;
  document.body.classList.add("no-scroll");
}

function closeDrawers() {
  $$(".drawer.open").forEach((drawer) => { drawer.classList.remove("open"); drawer.setAttribute("aria-hidden", "true"); });
  $("#drawerOverlay").hidden = true;
  if ($$(".modal-shell:not([hidden])").length === 0) document.body.classList.remove("no-scroll");
}

async function openCart() {
  if (!state.user) { openAuth("login"); return; }
  await loadCart({ quiet: false });
  renderCartDrawer();
  openDrawer("cartDrawer");
}

function renderCartDrawer() {
  const root = $("#drawerCartContent");
  if (!state.cart?.items?.length) {
    root.innerHTML = emptyState("🛒", "Your cart is empty", "Add a product and it will appear here.", "Browse products", "shop");
    return;
  }
  root.innerHTML = `<div>${state.cart.items.map((item) => `<div class="cart-line"><div class="cart-line-art">${productVisual({ name: item.productName }, true)}</div><div><h3>${escapeHtml(item.productName)}</h3><p>${formatMoney(item.unitPrice)} each</p><div class="qty-control"><button data-action="cart-qty" data-id="${item.itemId}" data-qty="${item.quantity - 1}">−</button><span>${item.quantity}</span><button data-action="cart-qty" data-id="${item.itemId}" data-qty="${item.quantity + 1}">+</button></div></div><button class="remove-link" data-action="remove-cart" data-id="${item.itemId}">Remove</button></div>`).join("")}</div><div class="cart-summary"><div class="summary-row"><span>Subtotal</span><strong>${formatMoney(state.cart.totalAmount)}</strong></div><div class="summary-row"><span>Demo delivery</span><strong>Free</strong></div><div class="summary-row total"><span>Total</span><span>${formatMoney(state.cart.totalAmount)}</span></div></div><button class="primary-button blue drawer-cta" data-action="go-checkout">Proceed to checkout</button>`;
}

function renderAccountDrawer() {
  const root = $("#accountDrawerContent");
  if (!state.user) {
    root.innerHTML = `<div class="account-menu-user"><span class="avatar">G</span><strong>Welcome to ReyCom</strong><span>Sign in to shop and manage orders</span></div><div class="account-menu-links"><button data-action="open-auth">Sign in <span>→</span></button><button data-action="open-signup">Create account <span>→</span></button><a href="#developer">Developer tools <span>→</span></a></div>`;
  } else {
    root.innerHTML = `<div class="account-menu-user"><span class="avatar">${initials(state.user.fullName)}</span><strong>${escapeHtml(state.user.fullName)}</strong><span>${escapeHtml(state.user.email)} · ${escapeHtml(state.user.role)}</span></div><div class="account-menu-links"><a href="#account">My profile <span>→</span></a><a href="#orders">Order history <span>→</span></a><a href="#notifications">Notifications <span>→</span></a>${isAdmin() ? `<a href="#admin">Admin workspace <span>→</span></a>` : ""}<button data-action="quick-login" data-profile="${isAdmin() ? "client" : "admin"}">Switch to ${isAdmin() ? "Client" : "Admin"} <span>↔</span></button><a href="#developer">Developer tools <span>→</span></a><button data-action="logout">Sign out <span>→</span></button></div>`;
  }
}

async function addToCart(productId, button) {
  if (!state.user) { openAuth("login"); return; }
  await withBusy(button, async () => {
    const result = await api("/api/cart/items", { method: "POST", body: JSON.stringify({ productId, quantity: 1 }) });
    state.cart = result.data;
    updateCartCount();
    toast("Added to cart", "Your basket is ready when you are.");
  });
}

async function checkHealth() {
  try {
    state.health = (await fetch(`${apiBase()}/actuator/health`).then((response) => response.ok ? response.json() : null));
    $("#footerStatusDot")?.classList.toggle("up", state.health?.status === "UP");
    if ($("#footerStatusText")) $("#footerStatusText").textContent = state.health?.status === "UP" ? "All core services operational" : "API unavailable";
  } catch {
    state.health = null;
    if ($("#footerStatusText")) $("#footerStatusText").textContent = "API unavailable";
  }
}

function formObject(form) {
  const result = Object.fromEntries(new FormData(form));
  $$('input[type="number"]', form).forEach((input) => { result[input.name] = Number(input.value); });
  return result;
}

document.addEventListener("click", async (event) => {
  const authTab = event.target.closest("[data-auth-tab]");
  if (authTab) { state.authTab = authTab.dataset.authTab; renderAuthForm(); return; }
  const actionElement = event.target.closest("[data-action]");
  if (!actionElement) return;
  const { action, id, profile, section } = actionElement.dataset;
  if (action === "open-auth") openAuth("login");
  if (action === "open-signup") openAuth("signup");
  if (action === "quick-login") quickLogin(profile, actionElement);
  if (action === "logout") clearSession();
  if (action === "view-product") showProduct(id);
  if (action === "add-to-cart") addToCart(id, actionElement);
  if (action === "choose-category") { event.preventDefault(); state.shop.categoryId = id; state.shop.page = 0; navigate("shop"); }
  if (action === "filter-category") { state.shop.categoryId = id; state.shop.page = 0; await fetchShopProducts(); $("#appView").innerHTML = renderShop(); }
  if (action === "shop-page") { state.shop.page = Number(actionElement.dataset.page); await fetchShopProducts(); $("#appView").innerHTML = renderShop(); window.scrollTo({ top: 300, behavior: "smooth" }); }
  if (action === "cart-qty") {
    const quantity = Number(actionElement.dataset.qty);
    if (quantity <= 0) await api(`/api/cart/items/${id}`, { method: "DELETE" });
    else await api(`/api/cart/items/${id}`, { method: "PUT", body: JSON.stringify({ quantity }) });
    await loadCart({ quiet: false }); renderCartDrawer();
  }
  if (action === "remove-cart") { await api(`/api/cart/items/${id}`, { method: "DELETE" }); await loadCart({ quiet: false }); renderCartDrawer(); toast("Removed from cart"); }
  if (action === "go-checkout") navigate("checkout");
  if (action === "view-events") renderOrderEvents(id, actionElement.dataset.admin === "true");
  if (action === "cancel-order") { await api(`/api/orders/${id}/cancel`, { method: "POST" }); toast("Order cancelled"); await loadOrders(); $("#appView").innerHTML = renderOrders(); }
  if (action === "refresh-notifications") { await loadNotifications({ quiet: false }); $("#appView").innerHTML = renderNotifications(); }
  if (action === "read-notification") { await api(`/api/notifications/${id}/read`, { method: "PUT" }); await loadNotifications(); $("#appView").innerHTML = renderNotifications(); }
  if (action === "admin-section") { state.adminSection = section; $("#appView").innerHTML = renderAdmin(); }
  if (action === "update-order") { const select = $(`[data-order-status="${id}"]`); await api(`/api/admin/orders/${id}/status`, { method: "PUT", body: JSON.stringify({ status: select.value }) }); toast("Order status updated"); await loadAdminData(); $("#appView").innerHTML = renderAdmin(); }
  if (action === "delete-product") { if (confirm("Remove this product from the active catalogue?")) { await api(`/api/admin/products/${id}`, { method: "DELETE" }); toast("Product removed"); await loadAdminData(); $("#appView").innerHTML = renderAdmin(); } }
  if (action === "clear-profiles") { localStorage.removeItem("reycom.testProfiles"); toast("Demo profiles cleared"); renderRoute(); }
});

document.addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = event.target;
  const button = $("button[type=submit]", form);
  if (form.id === "headerSearchForm") { state.shop.search = $("#headerSearch").value.trim(); state.shop.page = 0; navigate("shop"); }
  if (form.id === "loginForm") submitAuth(form, button, false);
  if (form.id === "signupForm") submitAuth(form, button, true);
  if (form.id === "checkoutForm") completeCheckout(form, button);
  if (form.id === "demoProfilesForm") {
    const data = Object.fromEntries(new FormData(form));
    localStorage.setItem("reycom.testProfiles", JSON.stringify({ admin: { email: data.adminEmail, password: data.adminPassword }, client: { email: data.clientEmail, password: data.clientPassword } }));
    toast("Demo profiles saved", "Use quick switch from the account menu.");
  }
  if (form.id === "categoryAdminForm") await withBusy(button, async () => { await api("/api/admin/categories", { method: "POST", body: JSON.stringify(formObject(form)) }); toast("Category created"); form.reset(); await loadAdminData(); $("#appView").innerHTML = renderAdmin(); });
  if (form.id === "productAdminForm") await withBusy(button, async () => { const data = formObject(form); data.imageUrl = data.imageUrl || null; await api("/api/admin/products", { method: "POST", body: JSON.stringify(data) }); toast("Product created"); form.reset(); await loadAdminData(); $("#appView").innerHTML = renderAdmin(); });
  if (form.id === "inventoryAdminForm") await withBusy(button, async () => { await api("/api/admin/inventory", { method: "POST", body: JSON.stringify(formObject(form)) }); toast("Inventory created"); form.reset(); await loadAdminData(); $("#appView").innerHTML = renderAdmin(); });
});

$("#cartButton").addEventListener("click", openCart);
$("#accountButton").addEventListener("click", () => { renderAccountDrawer(); openDrawer("accountDrawer"); });
$("#notificationButton").addEventListener("click", () => navigate("notifications"));
$("#mobileMenuButton").addEventListener("click", (event) => { const open = $("#mobileNav").classList.toggle("open"); event.currentTarget.setAttribute("aria-expanded", String(open)); });
$("#drawerOverlay").addEventListener("click", closeDrawers);
$$('[data-close-drawer]').forEach((button) => button.addEventListener("click", closeDrawers));
$("#closeAuthModal").addEventListener("click", () => closeModal("authModal"));
$("#closeProductModal").addEventListener("click", () => closeModal("productModal"));
$("#authModal").addEventListener("click", (event) => { if (event.target.id === "authModal") closeModal("authModal"); });
$("#productModal").addEventListener("click", (event) => { if (event.target.id === "productModal") closeModal("productModal"); });
$("#shopSort")?.addEventListener?.("change", () => {});

document.addEventListener("change", async (event) => {
  if (event.target.id === "shopSort") { state.shop.sort = event.target.value; state.shop.page = 0; await fetchShopProducts(); $("#appView").innerHTML = renderShop(); }
});

window.addEventListener("hashchange", renderRoute);
window.addEventListener("keydown", (event) => {
  if (event.key !== "Escape") return;
  closeDrawers();
  if (!$("#authModal").hidden) closeModal("authModal");
  if (!$("#productModal").hidden) closeModal("productModal");
});

async function init() {
  syncHeader();
  await Promise.all([loadPublicData(), checkHealth(), loadCart(), loadNotifications()]);
  await renderRoute();
}

init();
