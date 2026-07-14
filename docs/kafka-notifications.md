# Kafka Event Processing and Notifications

Kafka is used for asynchronous order and payment events. PostgreSQL still stores the main business data, and DynamoDB still stores the order timeline.

## Start Kafka Locally

This project uses Redpanda as a Kafka-compatible local broker.

```bash
docker compose up -d kafka
```

Start the supporting services:

```bash
docker compose up -d postgres dynamodb redis kafka
```

Then start the backend:

```bash
./mvnw spring-boot:run
```

## Topics

- `reycom.order.events`
- `reycom.payment.events`

The application creates these topics on startup when Kafka is enabled.

## Events Published

Order events:

- `ORDER_CREATED`
- `ORDER_CANCELLED`
- `ORDER_STATUS_UPDATED`

Payment events:

- `PAYMENT_INITIATED`
- `PAYMENT_SUCCESS`
- `PAYMENT_FAILED`

## Manual Verification

1. Create an order from a customer's cart.
2. Initiate payment for that order.
3. Mark the payment successful or failed.
4. Fetch notifications:

```bash
curl -H "Authorization: Bearer <customer-token>" \
  http://localhost:8080/api/notifications
```

5. Mark a notification as read:

```bash
curl -X PUT -H "Authorization: Bearer <customer-token>" \
  http://localhost:8080/api/notifications/<notification-id>/read
```

## Test Profile

Kafka is disabled in `application-test.yml` with:

```yaml
reycom:
  kafka:
    enabled: false
```

Normal tests do not need a Kafka broker running.
