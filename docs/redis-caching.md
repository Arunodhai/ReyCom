# Redis Caching for ReyCom Catalog APIs

Phase 9 uses Redis only for read-heavy catalog data. PostgreSQL remains the source of truth.

Start Redis locally:

```bash
docker compose up -d redis
```

Check Redis is running:

```bash
docker ps | grep reycom-redis
```

Cached service methods:

- `ProductService.getProducts(...)` uses cache `products`
- `ProductService.getById(...)` uses cache `productDetails`
- `CategoryService.getAll()` uses cache `categories`

Cache invalidation:

- Product create/update/delete evicts `products` and `productDetails`
- Category create/update/delete evicts `categories`, `categoryDetails`, `products`, and `productDetails`

Inspect keys:

```bash
docker exec -it reycom-redis redis-cli KEYS '*'
```

Clear all local Redis data:

```bash
docker exec -it reycom-redis redis-cli FLUSHALL
```
