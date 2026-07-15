# ReyCom

[![CI](https://github.com/Arunodhai/ReyCom/actions/workflows/ci.yml/badge.svg)](https://github.com/Arunodhai/ReyCom/actions/workflows/ci.yml)

ReyCom is a Spring Boot e-commerce backend with a lightweight browser console for testing the APIs.

## Run Locally Without Docker

Start the local infrastructure services:

```bash
docker compose up -d postgres redis dynamodb-local dynamodb-init kafka kafka-ui
```

Run the backend from your machine:

```bash
./mvnw spring-boot:run
```

Open:

- Backend API: http://localhost:8080
- Spring-served ReyCom Console: http://localhost:8080/console/
- Swagger/OpenAPI: http://localhost:8080/swagger-ui/index.html
- DynamoDB Local: http://localhost:8000
- Kafka UI: http://localhost:8081

In this mode, Spring Boot uses the default `dev` profile and connects to services through `localhost`.

## Run Full System With Docker Compose

Create your local environment file:

```bash
cp .env.example .env
```

Edit `.env` and replace `JWT_SECRET` with a long local secret. Do not commit `.env`.

Start everything:

```bash
docker compose up --build
```

Open:

- Backend API: http://localhost:8080
- ReyCom Console: http://localhost:3000
- Swagger/OpenAPI: http://localhost:8080/swagger-ui/index.html
- DynamoDB Local: http://localhost:8000
- Kafka UI: http://localhost:8081

Stop the system:

```bash
docker compose down
```

Reset local Docker data:

```bash
docker compose down -v
```

## Docker Networking

When the backend runs directly on your machine, it connects to dependencies with `localhost`, for example `localhost:5432` and `localhost:9092`.

When the backend runs inside Docker Compose, `localhost` means the backend container itself. The Docker profile uses Compose service names instead:

- PostgreSQL: `postgres:5432`
- Redis: `redis:6379`
- DynamoDB Local: `http://dynamodb-local:8000`
- Kafka: `kafka:9092`

Your browser still uses normal host ports such as `http://localhost:3000` for the console and `http://localhost:8080` for the API.

## DynamoDB Local Table

The `dynamodb-init` Compose service waits for DynamoDB Local and creates the `reycom_order_events` table if it does not already exist.

Table definition:

- Partition key: `orderId` String
- Sort key: `eventTime` String
- Billing mode: `PAY_PER_REQUEST`

## Console API Base URL

The Docker console is served by Nginx on port `3000`. It receives `VITE_API_BASE_URL` from `.env` and writes it into `env-config.js` at container startup. The default value is:

```bash
VITE_API_BASE_URL=http://localhost:8080
```

The console served from Spring Boot at `http://localhost:8080/console/` uses the same origin by default.

## Tests

Run the API integration tests:

```bash
./mvnw test
```

Tests use the `test` profile with H2 and a simple cache manager. They do not require Docker, PostgreSQL, Redis, DynamoDB Local, or Kafka to be running.

## Continuous Integration

GitHub Actions runs CI for every push and pull request targeting `main` or `master`. The workflow:

- runs the backend tests with Java 21 and Maven
- builds the Spring Boot JAR
- builds the ReyCom API Docker image
- installs and builds the browser console with Node.js 20
- builds the ReyCom Console Docker image
- validates the Docker Compose configuration

The workflow is defined in [`.github/workflows/ci.yml`](.github/workflows/ci.yml). Test failures and build logs are available from the repository's **Actions** tab.

## Publishing Backend Docker Image to AWS ECR

The ECR publishing workflow runs when code is pushed to `main` or `master`. It can also be started manually from the repository's **Actions** tab by selecting **Publish Backend to ECR** and choosing **Run workflow**.

Create the private ECR repository before running the workflow. `reycom-api` is the recommended repository name. Add these values under **Settings > Secrets and variables > Actions > Repository secrets**:

| Secret | Purpose |
| --- | --- |
| `AWS_ACCESS_KEY_ID` | Access key for an IAM identity allowed to push to ECR |
| `AWS_SECRET_ACCESS_KEY` | Secret key for the IAM identity |
| `AWS_REGION` | Region containing the ECR repository, such as `ap-south-1` |
| `AWS_ACCOUNT_ID` | AWS account that owns the ECR repository |
| `ECR_REPOSITORY` | Existing ECR repository name, such as `reycom-api` |

The workflow is defined in [`.github/workflows/ecr-publish.yml`](.github/workflows/ecr-publish.yml). It runs the backend tests, builds the JAR, builds the backend Docker image, and pushes two tags:

- `latest`
- the full Git commit SHA, for example `a1b2c3d4...`

To verify a published image, open **AWS Console > Elastic Container Registry > Private registry > Repositories**, select the configured repository, and check that both tags are listed under **Images**. AWS credentials are read only from GitHub repository secrets; do not add them to `.env` or commit them to the repository.

## Monitoring

Prometheus scrapes Spring Boot Actuator metrics from the ReyCom API, and Grafana reads those metrics from Prometheus. Start the complete local stack with:

```bash
docker compose up --build
```

Monitoring URLs:

- ReyCom API health: http://localhost:8080/actuator/health
- Prometheus metrics: http://localhost:8080/actuator/prometheus
- Actuator metric names: http://localhost:8080/actuator/metrics
- Prometheus UI: http://localhost:9090
- Grafana: http://localhost:3001

Grafana uses `admin` / `admin` by default. The username and password can be changed with `GRAFANA_ADMIN_USER` and `GRAFANA_ADMIN_PASSWORD` in `.env`. Open **Dashboards > ReyCom > ReyCom Overview** to view API traffic, latency, errors, JVM health, and business counters.

Custom counters:

- `reycom.orders.created`
- `reycom.orders.cancelled`
- `reycom.payments.initiated`
- `reycom.payments.success`
- `reycom.payments.failed`
- `reycom.notifications.created`

Micrometer exposes counters in Prometheus format with underscores and a `_total` suffix. Prometheus reserves the `_created` suffix, so the logical meter `reycom.orders.created` is exported as `reycom_orders_total` (and `reycom.notifications.created` as `reycom_notifications_total`). Inspect the currently available names with:

```bash
curl -s http://localhost:8080/actuator/prometheus | grep '^reycom_'
```

To verify the business panels, use the ReyCom Console to create an order, initiate its payment, and mark the payment successful or failed. Refresh Grafana after the next Prometheus scrape, which occurs every 15 seconds. Kafka-created notifications update the notifications counter when their database transaction commits.

The local monitoring configuration is stored under `monitoring/`. This phase does not add EC2 deployment automation: `.github/workflows/ci.yml` continues to test and build, while `.github/workflows/ecr-publish.yml` continues only to publish the backend image to ECR.
