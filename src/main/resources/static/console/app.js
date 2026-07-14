const state = {
  token: localStorage.getItem("reycom.token") || "",
  user: JSON.parse(localStorage.getItem("reycom.user") || "null"),
};

const titles = {
  auth: ["Auth", "Register, login, and store the bearer token for API testing."],
  catalog: ["Catalog", "Create products and categories, then copy IDs into later flows."],
  inventory: ["Inventory", "Create stock records and verify sellable quantities."],
  cart: ["Cart", "Add products, adjust quantities, and prepare a checkout cart."],
  orders: ["Orders", "Create orders from the current cart and test cancellation."],
  payments: ["Payments", "Simulate payment initiation, success, failure, and retry."],
  notifications: ["Notifications", "Review async Kafka notifications for the current customer."],
  admin: ["Admin", "Inspect all orders/payments and update order status."],
};

const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => [...document.querySelectorAll(selector)];

function configuredApiBase() {
  return window.REYCOM_CONFIG?.apiBaseUrl || "";
}

function apiBase() {
  return $("#apiBase").value.trim().replace(/\/$/, "");
}

function headers(hasBody = true) {
  const result = {};
  if (hasBody) result["Content-Type"] = "application/json";
  if (state.token) result.Authorization = `Bearer ${state.token}`;
  return result;
}

async function api(path, options = {}) {
  const hasBody = options.body !== undefined;
  const response = await fetch(`${apiBase()}${path}`, {
    ...options,
    headers: { ...headers(hasBody), ...(options.headers || {}) },
  });

  const text = await response.text();
  let payload = text ? JSON.parse(text) : null;
  if (!response.ok) {
    const error = new Error(payload?.message || `HTTP ${response.status}`);
    error.payload = payload;
    error.status = response.status;
    throw error;
  }
  return payload;
}

function bodyFromForm(form) {
  const data = new FormData(form);
  const obj = {};
  for (const [key, value] of data.entries()) {
    const input = form.elements[key];
    obj[key] = input?.type === "number" ? Number(value) : value;
  }
  return obj;
}

async function run(action, options = {}) {
  setBusy(true);
  try {
    const result = await action();
    log(result, true);
    if (options.refresh) await options.refresh();
    return result;
  } catch (error) {
    log(error.payload || { success: false, message: error.message, status: error.status }, false);
  } finally {
    setBusy(false);
    renderSession();
  }
}

function setBusy(busy) {
  $$("button").forEach((button) => { button.disabled = busy; });
}

function log(value, ok) {
  $("#responseLog").textContent = JSON.stringify(value, null, 2);
  $("#responseLog").className = ok ? "ok" : "bad";
}

function shortId(value) {
  if (!value) return "None";
  return value.length > 13 ? `${value.slice(0, 8)}...${value.slice(-4)}` : value;
}

function updateSelectedBar() {
  $("#selectedProductChip").textContent = shortId($("#cartProductId")?.value || $("#inventoryProductId")?.value);
  $("#selectedCartItemChip").textContent = shortId($("#cartItemId")?.value);
  $("#selectedOrderChip").textContent = shortId($("#selectedOrderId")?.value || $("#paymentOrderId")?.value || $("#adminOrderId")?.value);
  $("#selectedPaymentChip").textContent = shortId($("#selectedPaymentId")?.value);
}

function renderSession() {
  const user = state.user;
  $("#sessionUser").textContent = user ? user.email : "Not signed in";
  $("#sessionRole").textContent = user ? user.role : state.token ? "Token saved" : "No token";
  $("#sessionRole").classList.toggle("muted", !user);
}

function persistAuth(auth) {
  state.token = auth.token;
  state.user = auth.user;
  localStorage.setItem("reycom.token", state.token);
  localStorage.setItem("reycom.user", JSON.stringify(state.user));
  renderSession();
}

function clearAuth() {
  state.token = "";
  state.user = null;
  localStorage.removeItem("reycom.token");
  localStorage.removeItem("reycom.user");
  renderSession();
}

function itemCard(title, meta = [], actions = []) {
  const actionHtml = actions.map((action) =>
    `<button class="secondary mini" data-action="${action.action}" data-id="${action.id}">${action.label}</button>`
  ).join("");
  return `
    <div class="item">
      <strong>${escapeHtml(title)}</strong>
      <div class="meta">${meta.map((m) => `<span>${escapeHtml(String(m))}</span>`).join("")}</div>
      ${actions.length ? `<div class="mini-actions">${actionHtml}</div>` : ""}
    </div>
  `;
}

function escapeHtml(value) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

async function refreshCategories() {
  const result = await api("/api/categories");
  const categories = result.data || [];
  $("#categoriesBox").innerHTML = categories.length ? categories.map((category) => itemCard(
    category.name,
    [`ID: ${category.id}`, category.description || "No description"],
    [{ label: "Use for product", action: "use-category", id: category.id }]
  )).join("") : `<div class="empty">No categories found.</div>`;
}

async function refreshProducts() {
  const result = await api("/api/products?size=100");
  const products = result.data?.content || result.data || [];
  $("#productsBox").innerHTML = products.length ? products.map((product) => itemCard(
    product.name,
    [`ID: ${product.id}`, `Price: ${product.price}`, `SKU: ${product.sku}`],
    [
      { label: "Use in cart", action: "use-product-cart", id: product.id },
      { label: "Use in inventory", action: "use-product-inventory", id: product.id },
    ]
  )).join("") : `<div class="empty">No products found.</div>`;
}

async function refreshInventory() {
  const result = await api("/api/admin/inventory");
  const rows = result.data || [];
  $("#inventoryBox").innerHTML = rows.length ? `
    <table>
      <thead><tr><th>Product</th><th>Available</th><th>Reserved</th><th>Sellable</th><th>Low</th><th>ID</th></tr></thead>
      <tbody>
        ${rows.map((row) => `
          <tr>
            <td>${escapeHtml(row.productName)}</td>
            <td>${row.quantityAvailable}</td>
            <td>${row.reservedQuantity}</td>
            <td>${row.availableToSell}</td>
            <td>${row.lowStock ? "Yes" : "No"}</td>
            <td>${row.productId}</td>
          </tr>
        `).join("")}
      </tbody>
    </table>
  ` : `<div class="empty">No inventory found.</div>`;
}

async function refreshCart() {
  const result = await api("/api/cart");
  const cart = result.data;
  const items = cart?.items || [];
  $("#cartBox").innerHTML = `
    <div class="meta"><span>Cart: ${cart.cartId}</span><span>Total: ${cart.totalAmount}</span></div>
    <div class="list">
      ${items.length ? items.map((item) => itemCard(
        item.productName,
        [`Item: ${item.itemId}`, `Product: ${item.productId}`, `Qty: ${item.quantity}`, `Line: ${item.lineTotal}`],
        [{ label: "Select item", action: "use-cart-item", id: item.itemId }]
      )).join("") : `<div class="empty">Cart is empty.</div>`}
    </div>
  `;
}

async function refreshOrders() {
  const result = await api("/api/orders");
  renderOrders("#ordersBox", result.data || [], false);
}

async function refreshAdminOrders() {
  const result = await api("/api/admin/orders");
  renderOrders("#adminOrdersBox", result.data || [], true);
}

function renderOrders(selector, orders, admin) {
  $(selector).innerHTML = orders.length ? orders.map((order) => itemCard(
    `${order.orderNumber} · ${order.status}`,
    [`ID: ${order.orderId}`, `Total: ${order.totalAmount}`, `Items: ${order.items.length}`, `User: ${order.userId}`],
    [
      { label: admin ? "Select admin" : "Select", action: admin ? "use-admin-order" : "use-order", id: order.orderId },
      { label: "Use for payment", action: "use-order-payment", id: order.orderId },
    ]
  )).join("") : `<div class="empty">No orders found.</div>`;
}

async function refreshPayments() {
  const result = await api("/api/payments");
  renderPayments("#paymentsBox", result.data || []);
}

async function refreshNotifications() {
  const result = await api("/api/notifications");
  const notifications = result.data || [];
  $("#notificationsBox").innerHTML = notifications.length ? notifications.map((notification) => itemCard(
    `${notification.title}${notification.read ? "" : " · Unread"}`,
    [
      notification.message,
      `Type: ${notification.eventType}`,
      `Reference: ${notification.referenceId}`,
      `Created: ${notification.createdAt}`,
    ],
    [{ label: "Select", action: "use-notification", id: notification.id }]
  )).join("") : `<div class="empty">No notifications found.</div>`;
}

async function refreshAdminPayments() {
  const result = await api("/api/admin/payments");
  renderPayments("#adminPaymentsBox", result.data || []);
}

async function fetchOrderEvents(orderId, admin = false) {
  const path = admin ? `/api/admin/orders/${orderId}/events` : `/api/orders/${orderId}/events`;
  const result = await api(path);
  renderEvents(admin ? "#adminOrderEventsBox" : "#orderEventsBox", result.data || []);
  return result;
}

function renderEvents(selector, events) {
  $(selector).innerHTML = events.length ? events.map((event) => itemCard(
    `${event.eventType} · ${event.eventTime}`,
    [
      event.message,
      `Order: ${event.orderStatus || "n/a"}`,
      `Payment: ${event.paymentStatus || "n/a"}`,
      `Payment ID: ${event.paymentId || "n/a"}`,
    ]
  )).join("") : `<div class="empty">No events found for this order.</div>`;
}

function renderPayments(selector, payments) {
  $(selector).innerHTML = payments.length ? payments.map((payment) => itemCard(
    `${payment.paymentNumber} · ${payment.status}`,
    [`ID: ${payment.paymentId}`, `Order: ${payment.orderNumber}`, `Amount: ${payment.amount}`, `Method: ${payment.paymentMethod}`],
    [{ label: "Select payment", action: "use-payment", id: payment.paymentId }]
  )).join("") : `<div class="empty">No payments found.</div>`;
}

function bindForms() {
  $("#loginForm").addEventListener("submit", (event) => {
    event.preventDefault();
    run(async () => {
      const result = await api("/api/auth/login", { method: "POST", body: JSON.stringify(bodyFromForm(event.target)) });
      persistAuth(result.data);
      return result;
    });
  });

  $("#registerForm").addEventListener("submit", (event) => {
    event.preventDefault();
    run(async () => {
      const result = await api("/api/auth/register", { method: "POST", body: JSON.stringify(bodyFromForm(event.target)) });
      persistAuth(result.data);
      return result;
    });
  });

  $("#categoryForm").addEventListener("submit", (event) => {
    event.preventDefault();
    run(() => api("/api/admin/categories", { method: "POST", body: JSON.stringify(bodyFromForm(event.target)) }), { refresh: refreshCategories });
  });

  $("#productForm").addEventListener("submit", (event) => {
    event.preventDefault();
    const payload = bodyFromForm(event.target);
    payload.imageUrl = payload.imageUrl || null;
    run(() => api("/api/admin/products", { method: "POST", body: JSON.stringify(payload) }), { refresh: refreshProducts });
  });

  $("#inventoryForm").addEventListener("submit", (event) => {
    event.preventDefault();
    run(() => api("/api/admin/inventory", { method: "POST", body: JSON.stringify(bodyFromForm(event.target)) }), { refresh: refreshInventory });
  });

  $("#inventoryUpdateForm").addEventListener("submit", (event) => {
    event.preventDefault();
    const payload = bodyFromForm(event.target);
    const productId = payload.productId;
    delete payload.productId;
    run(() => api(`/api/admin/inventory/${productId}`, { method: "PUT", body: JSON.stringify(payload) }), { refresh: refreshInventory });
  });

  $("#addCartForm").addEventListener("submit", (event) => {
    event.preventDefault();
    run(() => api("/api/cart/items", { method: "POST", body: JSON.stringify(bodyFromForm(event.target)) }), { refresh: refreshCart });
  });

  $("#updateCartForm").addEventListener("submit", (event) => {
    event.preventDefault();
    const payload = bodyFromForm(event.target);
    const itemId = payload.itemId;
    delete payload.itemId;
    run(() => api(`/api/cart/items/${itemId}`, { method: "PUT", body: JSON.stringify(payload) }), { refresh: refreshCart });
  });

  $("#initPaymentForm").addEventListener("submit", (event) => {
    event.preventDefault();
    const payload = bodyFromForm(event.target);
    const orderId = payload.orderId;
    delete payload.orderId;
    run(() => api(`/api/orders/${orderId}/payments`, { method: "POST", body: JSON.stringify(payload) }), { refresh: refreshPayments });
  });

  $("#failPaymentForm").addEventListener("submit", (event) => {
    event.preventDefault();
    const payload = bodyFromForm(event.target);
    const paymentId = payload.paymentId;
    delete payload.paymentId;
    run(() => api(`/api/payments/${paymentId}/fail`, { method: "POST", body: JSON.stringify(payload) }), { refresh: refreshPayments });
  });
}

function bindButtons() {
  $("#logoutBtn").addEventListener("click", clearAuth);
  $("#clearLogBtn").addEventListener("click", () => { $("#responseLog").textContent = "{}"; });
  $("#meBtn").addEventListener("click", () => run(() => api("/api/users/me")));
  $("#refreshCategoriesBtn").addEventListener("click", () => run(refreshCategories));
  $("#refreshProductsBtn").addEventListener("click", () => run(refreshProducts));
  $("#refreshInventoryBtn").addEventListener("click", () => run(refreshInventory));
  $("#refreshCartBtn").addEventListener("click", () => run(refreshCart));
  $("#clearCartBtn").addEventListener("click", () => run(() => api("/api/cart", { method: "DELETE" }), { refresh: refreshCart }));
  $("#removeCartItemBtn").addEventListener("click", () => {
    const itemId = $("#cartItemId").value.trim();
    if (!itemId) return log({ success: false, message: "Select or enter a cart item ID" }, false);
    run(() => api(`/api/cart/items/${itemId}`, { method: "DELETE" }), { refresh: refreshCart });
  });
  $("#createOrderBtn").addEventListener("click", () => run(() => api("/api/orders", { method: "POST" }), { refresh: refreshOrders }));
  $("#refreshOrdersBtn").addEventListener("click", () => run(refreshOrders));
  $("#fetchOrderBtn").addEventListener("click", () => {
    const orderId = $("#selectedOrderId").value.trim();
    if (!orderId) return log({ success: false, message: "Select or enter an order ID" }, false);
    run(() => api(`/api/orders/${orderId}`));
  });
  $("#fetchOrderEventsBtn").addEventListener("click", () => {
    const orderId = $("#selectedOrderId").value.trim();
    if (!orderId) return log({ success: false, message: "Select or enter an order ID" }, false);
    run(() => fetchOrderEvents(orderId));
  });
  $("#cancelOrderBtn").addEventListener("click", () => {
    const orderId = $("#selectedOrderId").value.trim();
    if (!orderId) return log({ success: false, message: "Select or enter an order ID" }, false);
    run(() => api(`/api/orders/${orderId}/cancel`, { method: "POST" }), { refresh: refreshOrders });
  });
  $("#refreshPaymentsBtn").addEventListener("click", () => run(refreshPayments));
  $("#refreshNotificationsBtn").addEventListener("click", () => run(refreshNotifications));
  $("#markNotificationReadBtn").addEventListener("click", () => {
    const notificationId = $("#selectedNotificationId").value.trim();
    if (!notificationId) return log({ success: false, message: "Select or enter a notification ID" }, false);
    run(() => api(`/api/notifications/${notificationId}/read`, { method: "PUT" }), { refresh: refreshNotifications });
  });
  $("#successPaymentBtn").addEventListener("click", () => {
    const paymentId = $("#selectedPaymentId").value.trim();
    if (!paymentId) return log({ success: false, message: "Select or enter a payment ID" }, false);
    run(() => api(`/api/payments/${paymentId}/success`, { method: "POST" }), { refresh: refreshPayments });
  });
  $("#refreshAdminOrdersBtn").addEventListener("click", () => run(refreshAdminOrders));
  $("#refreshAdminPaymentsBtn").addEventListener("click", () => run(refreshAdminPayments));
  $("#updateOrderStatusBtn").addEventListener("click", () => {
    const orderId = $("#adminOrderId").value.trim();
    if (!orderId) return log({ success: false, message: "Select or enter an order ID" }, false);
    run(() => api(`/api/admin/orders/${orderId}/status`, {
      method: "PUT",
      body: JSON.stringify({ status: $("#adminOrderStatus").value }),
    }), { refresh: refreshAdminOrders });
  });
  $("#fetchAdminOrderEventsBtn").addEventListener("click", () => {
    const orderId = $("#adminOrderId").value.trim();
    if (!orderId) return log({ success: false, message: "Select or enter an order ID" }, false);
    run(() => fetchOrderEvents(orderId, true));
  });
}

function bindNavigation() {
  $$(".nav-item").forEach((button) => {
    button.addEventListener("click", () => {
      $$(".nav-item").forEach((item) => item.classList.remove("active"));
      $$(".view").forEach((view) => view.classList.remove("active"));
      button.classList.add("active");
      $(`#${button.dataset.view}`).classList.add("active");
      const [title, subtitle] = titles[button.dataset.view];
      $("#viewTitle").textContent = title;
      $("#viewSubtitle").textContent = subtitle;
      window.scrollTo({ top: 0, behavior: "smooth" });
    });
  });
}

function bindDelegates() {
  document.body.addEventListener("click", (event) => {
    const button = event.target.closest("[data-action]");
    if (!button) return;
    const { action, id } = button.dataset;
    if (action === "use-category") $("#productCategoryId").value = id;
    if (action === "use-product-cart") {
      $("#cartProductId").value = id;
      $("#inventoryProductId").value = id;
      $("#inventoryUpdateProductId").value = id;
    }
    if (action === "use-product-inventory") {
      $("#inventoryProductId").value = id;
      $("#inventoryUpdateProductId").value = id;
      $("#cartProductId").value = id;
    }
    if (action === "use-cart-item") $("#cartItemId").value = id;
    if (action === "use-order") {
      $("#selectedOrderId").value = id;
      $("#paymentOrderId").value = id;
    }
    if (action === "use-admin-order") {
      $("#adminOrderId").value = id;
      $("#selectedOrderId").value = id;
      $("#paymentOrderId").value = id;
    }
    if (action === "use-order-payment") {
      $("#paymentOrderId").value = id;
      $("#selectedOrderId").value = id;
      $("#adminOrderId").value = id;
    }
    if (action === "use-payment") $("#selectedPaymentId").value = id;
    if (action === "use-notification") $("#selectedNotificationId").value = id;
    updateSelectedBar();
  });

  [
    "#cartProductId",
    "#inventoryProductId",
    "#inventoryUpdateProductId",
    "#cartItemId",
    "#selectedOrderId",
    "#paymentOrderId",
    "#adminOrderId",
    "#selectedPaymentId",
    "#selectedNotificationId",
  ].forEach((selector) => {
    const input = $(selector);
    if (input) input.addEventListener("input", updateSelectedBar);
  });
}

function init() {
  $("#apiBase").value = configuredApiBase();
  renderSession();
  updateSelectedBar();
  bindNavigation();
  bindForms();
  bindButtons();
  bindDelegates();
}

init();
