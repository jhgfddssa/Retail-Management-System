# API overview

Base path `/api/v1`; JSON; authenticate with `Authorization: Bearer <token>`. Mutation retries for sales, transfers, and goods receipts require an `Idempotency-Key`. Lists return `{ "items": [...] }`. Errors use Spring Problem Details.

## Authentication
- `POST /auth/login` `{username,password}` → `{token,username,roles,expiresIn}`
- `GET /auth/me`; `POST /auth/logout`; `GET /health`

## Master data
- `GET/POST /branches`; `PUT /branches/{id}`; `PATCH /branches/{id}/status` (administrator-only writes)
- `GET /users`; `POST /users`; `PUT /users/{id}/roles`; `PUT /users/{id}/branches`; `PATCH /users/{id}/status` (administrator-only)
- `GET /roles`, `GET /permissions`
- `GET/POST /products`; `PUT /products/{id}`; `PATCH /products/{id}/status`; `GET /products/lookup?query=`
- `GET/POST /categories`, `/brands`, `/customers`, `/suppliers`; `GET/POST /units`
- `GET/POST /exchange-rates` (creation administrator-only)

## Operations
- `GET /locations`; `GET /inventory` and `/inventory/balances`; `POST /inventory/adjustments`
- `GET/POST /transfers`; `POST /transfers/{id}/approve|dispatch|receive|cancel`
- `POST /sales`; `GET /sales/{id}`; `POST /sales/{saleId}/refunds`; `POST /sales/{saleId}/refunds/{refundId}/approve`
- `GET/POST /purchase-orders`; `GET /purchase-orders/{id}`; `POST /purchase-orders/{id}/receipts`
- `GET /dashboard/summary`; `GET /audit`

The backend calculates sale totals and validates line discounts. Cashier discount cap is 10%; manager cap is 50% of gross line value; administrator cap is the full line amount. Refund requests remain pending until a manager or administrator approves; approved returns restock only if requested. V1 payments use the invoice currency.


