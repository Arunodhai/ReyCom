# DynamoDB Local for ReyCom Order Events

Phase 8 uses DynamoDB only for append-only order event timeline data. PostgreSQL still stores transactional data such as users, carts, orders, inventory, and payments.

Start DynamoDB Local:

```bash
docker compose up -d dynamodb
```

Create the local table:

```bash
aws dynamodb create-table \
  --table-name reycom_order_events \
  --attribute-definitions AttributeName=orderId,AttributeType=S AttributeName=eventTime,AttributeType=S \
  --key-schema AttributeName=orderId,KeyType=HASH AttributeName=eventTime,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST \
  --endpoint-url http://localhost:8000 \
  --region ap-south-1
```

List tables:

```bash
aws dynamodb list-tables \
  --endpoint-url http://localhost:8000 \
  --region ap-south-1
```
