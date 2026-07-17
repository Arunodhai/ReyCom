# ReyCom Demo Guide

This runbook shows how to start ReyCom, demonstrate the complete application flow, and verify every supporting service. ReyCom uses Docker Compose for the demo; Kubernetes is not required.

## What the demo runs

Docker Compose starts:

- ReyCom Spring Boot API
- ReyCom API Console
- PostgreSQL
- Redis
- DynamoDB Local
- Redpanda (Kafka-compatible event broker)
- Redpanda Console (Kafka UI)
- Prometheus
- Grafana

## 1. Prepare before the demo

Do this at least 15 minutes before presenting:

1. Start Docker Desktop and wait until it reports that Docker is running.
2. Connect the laptop to power.
3. Close applications that use ports `3000`, `3001`, `5432`, `6379`, `8000`, `8080`, `8081`, `9090`, or `9092`.
4. Keep this file open as a checklist.
5. Do not update dependencies or reset Docker volumes immediately before the demo.

From the repository root:

```bash
cd /Users/arunodhaiv/Desktop/SpringBoot/reycom
docker compose up -d --build
```

The first build can take several minutes. Later starts are usually faster.

Check every container:

```bash
docker compose ps -a
```

Expected result:

- `reycom-api`, `reycom-console`, PostgreSQL, Redis, DynamoDB Local, Kafka, Kafka UI, Prometheus, and Grafana are `Up`.
- PostgreSQL, Redis, and Kafka become `healthy`.
- `dynamodb-init` shows `Exited (0)`. This is expected: it creates/verifies the table and then finishes successfully.

Watch API startup if necessary:

```bash
docker compose logs -f reycom-api
```

Press `Ctrl+C` to stop following the logs. This does not stop the container.

## 2. Verify the system before presenting

Run these quick smoke checks:

```bash
curl --fail http://localhost:8080/actuator/health
curl --fail http://localhost:3000/
curl --fail http://localhost:8081/
curl --fail http://localhost:9090/-/ready
curl --fail http://localhost:3001/api/health
```

The API health response should contain `"status":"UP"`. Its database and Redis components should also be `UP`.

Open these tabs in advance:

| Purpose | URL | Expected result |
| --- | --- | --- |
| ReyCom Storefront | http://localhost:3000 | Customer and Admin application |
| Developer API Console | http://localhost:3000/developer/ | Direct endpoint testing |
| API health | http://localhost:8080/actuator/health | Status `UP` |
| Swagger UI | http://localhost:8080/swagger-ui/index.html | API documentation |
| Kafka UI | http://localhost:8081 | Topics and messages |
| Prometheus targets | http://localhost:9090/targets | `reycom-api` target is `UP` |
| Grafana | http://localhost:3001 | ReyCom dashboard |

Grafana's local default login is `admin` / `admin` unless it was changed in `.env`.

## 3. Configure the two test identities once

Open http://localhost:3000, click the account avatar, and sign in once with each demo account. You can save or update the quick-switch credentials under **My profile > Demo quick switch**.

The suggested local profiles are:

- Admin: `admin@reycom.local`
- Client: `helen@reycom.local`

The storefront saves demo credentials only in that browser's local storage. Afterwards, open the account menu and select **Switch to Admin** or **Switch to Client** without typing credentials again.

Use **My profile > Clear profiles** to remove both saved profiles.

### If the admin password is unavailable

Do not try to read a password from PostgreSQL; passwords are stored as hashes. Instead:

1. Create a new customer account from the Storefront, using an email and password you know.
2. Promote that user locally:

```bash
docker exec reycom-postgres \
  psql -U reycom_user -d reycom_db \
  -c "UPDATE users SET role = 'ADMIN' WHERE email = 'your-email@example.com';"
```

3. Log in again so the new JWT contains the `ADMIN` authority.
4. Save that email and password in the Admin test identity.

This promotion is only for the local demo database.

## 4. Complete demo flow

Follow this order to demonstrate both customer and administrator experiences.

### A. Show authentication

1. Click the account avatar and sign out if necessary.
2. Show the dedicated **Sign in** and **Create account** tabs.
3. Sign in as the Client and open **My profile**.
4. Point out the authenticated email and `CUSTOMER` role.
5. Use the account menu to switch to Admin and show the role-protected **Admin workspace**.

Explain: ReyCom uses Spring Security, BCrypt password hashing, JWT bearer authentication, and role-based endpoint authorization.

### B. Create catalog data as Admin

Remain logged in as Admin.

1. Open **Admin > Catalog**.
2. Create a category, for example:
   - Name: `Demo Electronics`
   - Description: `Products created during the ReyCom demo`
3. Create the category.
4. Create a product, for example:
   - Name: `Demo Wireless Keyboard`
   - Price: `1499.00`
   - SKU: use a new value for every run, such as `DEMO-KEY-01`
5. Create the product and show it in the Admin product table.
6. Open **Shop** in another tab or return to the Storefront to show that it appears immediately.

Avoid reusing a SKU from an earlier demo because SKUs are unique.

### C. Add inventory as Admin

1. Open **Admin > Inventory**.
2. Choose the product from the product selector.
3. Set Available to `25`, Reserved to `0`, and Low stock to `5`.
4. Click **Create**.
5. Show the available, reserved, sellable, and low-stock values in the stock table.

Explain: PostgreSQL stores transactional data and Redis caches catalog reads.

### D. Create an order as Client

1. Open the account menu and select **Switch to Client**.
2. Open **Shop** and find the newly created product.
3. Open the product details and click **Add to cart**.
4. Open the cart drawer and show quantity controls, subtotal, and free demo delivery.
5. Click **Proceed to checkout**.
6. Choose `UPI` or `CARD` and click **Pay securely**.
7. ReyCom creates the order and completes the demo payment through the real backend APIs.
8. The Storefront opens **Orders** and shows the new paid order.
9. Click **View timeline** to show its DynamoDB event history.

Explain: order creation reserves inventory, persists the order in PostgreSQL, records an event in DynamoDB, and publishes an asynchronous Kafka event.

### E. Explain the payment flow

The Storefront checkout intentionally presents a real customer experience instead of exposing internal payment-state buttons. Behind the **Pay securely** action, it calls the order creation, payment initiation, and demo payment-success APIs in sequence. Open the order timeline to show `ORDER_CREATED`, `PAYMENT_INITIATED`, and `PAYMENT_SUCCESS` events.

Explain: payment changes generate Kafka events, update the order workflow, write audit-style events to DynamoDB, and produce notification records asynchronously.

### F. Show asynchronous notifications

1. Wait a few seconds for Kafka processing.
2. Click the notification bell or open **Notifications** from the account menu.
3. Click **Refresh**.
4. Show the order/payment notification.
5. Click **Mark read**.

## 5. Verify infrastructure during the demo

### Kafka / Redpanda

Open http://localhost:8081 and show:

- `reycom.order.events`
- `reycom.payment.events`
- Messages created by the order and payment flow
- Consumer group activity

### DynamoDB Local

Use **View timeline** in the Orders page. This proves the API can read the order-event history stored in the `reycom_order_events` DynamoDB table.

To verify table initialization from the terminal:

```bash
docker compose logs dynamodb-init
```

Look for:

```text
DynamoDB table is ready: reycom_order_events
```

### Prometheus

Open http://localhost:9090/targets and show that `reycom-api` is `UP`.

The API metrics endpoint is:

```text
http://localhost:8080/actuator/prometheus
```

### Grafana

1. Open http://localhost:3001.
2. Open **Dashboards > ReyCom > ReyCom Overview**.
3. Select a recent time range, such as **Last 5 minutes**.
4. Wait for the next Prometheus scrape (up to approximately 15 seconds).
5. Show request traffic, latency, errors, JVM metrics, orders, payments, and notifications.

## 6. Suggested presentation script

Use this short explanation:

> ReyCom is a production-style Spring Boot e-commerce backend. The complete local environment is reproducible with Docker Compose. PostgreSQL stores transactional commerce data, Redis provides caching, DynamoDB stores order-event history, and Kafka handles asynchronous order and payment events. The browser console exercises the real secured APIs, while Prometheus and Grafana provide health, technical metrics, and business metrics. GitHub Actions validates the project and publishes the backend image to ECR, and Terraform documents the planned AWS infrastructure.

Be precise about deployment:

> This is a complete local demo environment. The AWS infrastructure and deployment path are prepared, but I am not presenting the local Docker environment as a production deployment.

## 7. Run automated tests

The Maven test suite does not require the Compose services:

```bash
./mvnw test
```

Validate the Compose file:

```bash
docker compose config --quiet
```

## 8. Troubleshooting

### API does not become available

```bash
docker compose ps -a
docker compose logs --tail=200 reycom-api
```

Restart only the API:

```bash
docker compose restart reycom-api
```

### Console cannot reach the API

1. Confirm `http://localhost:8080/actuator/health` opens.
2. Confirm the Console API base URL is `http://localhost:8080`.
3. Click **Test** beside the API base URL.
4. Rebuild the console if required:

```bash
docker compose up -d --build reycom-console
```

### An endpoint returns 401

- The session is missing or expired.
- Click Admin or Client again to obtain a fresh token.

### An admin endpoint returns 403

- The current user is a Client, not an Admin.
- Click **Admin** in the sidebar and retry.

### Product or category creation fails

- Use a unique SKU and, if necessary, a unique category name.
- Read the exact API error in the Console's **Last response** inspector.

### A port is already in use

On macOS, find the process using the port:

```bash
lsof -nP -iTCP:8080 -sTCP:LISTEN
```

Replace `8080` with the conflicting port and stop the unrelated application safely.

### See all service logs

```bash
docker compose logs --tail=100
```

## 9. Stop after the demo

Stop the complete system without deleting data:

```bash
docker compose down
```

Do not add `-v` unless you intentionally want to delete PostgreSQL, Prometheus, and Grafana Docker volumes. For a normal demo shutdown, use only `docker compose down`.

## Final checklist

- [ ] Docker Compose services are running.
- [ ] API health is `UP`.
- [ ] Admin and Client quick login profiles work.
- [ ] A unique demo product SKU is ready.
- [ ] Kafka UI tabs are open.
- [ ] Prometheus target is `UP`.
- [ ] Grafana is showing the recent time range.
- [ ] The full Client purchase flow has been rehearsed.
- [ ] The laptop is connected to power.
