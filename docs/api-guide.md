# API guide

Routes marked as tenant-backed require a verified tenant hostname, configured tenant datasource/provisioning, and appropriate authentication/authorization. They are not available in the default local configuration.

## Health and platform

- `GET /actuator/health` — health probe.
- `GET /actuator/info` — safe application metadata.
- `POST /api/v1/platform/tenants` — register tenant and create a DNS TXT ownership challenge.
- `GET /api/v1/platform/tenants` — paginated tenant list.
- `POST /api/v1/platform/tenants/{tenantId}/approve` — approve after domain verification.
- `POST /api/v1/platform/tenants/{tenantId}/provision` — provision tenant database and apply tenant migrations.
- `POST /api/v1/platform/tenants/{tenantId}/suspend` and `/reactivate` — lifecycle actions.
- `POST /api/v1/tenant-domains/verify` — verify the DNS TXT challenge.

## Authentication

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`

Protected routes require RSA key configuration. Refresh tokens rotate and are stored hashed server-side; the refresh token is carried in an HttpOnly cookie.

## Public tenant storefront

Requires tenant datasource configuration:
- `GET /api/v1/storefront/menu/restaurant`
- `GET /api/v1/storefront/menu/categories`
- `GET /api/v1/storefront/menu/items?page=0&size=20&category={slug}&search={term}`

Pagination is bounded; only active categories and available items are returned.

## Restaurant menu management

Requires active membership in the resolved tenant. OWNER and MANAGER can write; OWNER, MANAGER and STAFF can read.

- `GET /api/v1/restaurant/menu/categories?includeInactive=false`
- `POST /api/v1/restaurant/menu/categories`
- `PATCH /api/v1/restaurant/menu/categories/{categoryId}/active`
- `GET /api/v1/restaurant/menu/items?categoryId={id}&includeUnavailable=false&includeInactiveCategories=false`
- `POST /api/v1/restaurant/menu/items`
- `PATCH /api/v1/restaurant/menu/items/{itemId}/availability`

Writes use tenant-scoped transactions and record audit/outbox data.

## Customer cart

Requires CUSTOMER role and tenant database configuration.

- `GET /api/v1/customer/cart?branchId={branchId}`
- `POST /api/v1/customer/cart/items` — JSON: `branchId`, `itemId`, `quantity` (1–99), optional `notes`.
- `PUT /api/v1/customer/cart/{cartId}/items/{itemId}?quantity={quantity}`
- `DELETE /api/v1/customer/cart/{cartId}/items/{itemId}`

The customer ID comes from the signed token subject, never a request field. The backend validates active branches and available menu items, uses parameterized SQL, snapshots current menu prices and calculates subtotals server-side.

## Customer orders and checkout

Order endpoints are enabled only when both `MEALMINUTES_TENANT_PROVISIONING_ENABLED=true` and `MEALMINUTES_ORDER_PRICING_ENABLED=true` are configured, with tenant datasource/secrets also set.

- `POST /api/v1/customer/orders` — requires `Idempotency-Key` header and JSON fields `cartId`, `branchId`, `fulfillmentType` (`PICKUP` or `DELIVERY`), optional `deliveryAddressId`, optional `customerNote`.
- `GET /api/v1/customer/orders/{orderId}` — get an order owned by the caller.
- `GET /api/v1/customer/orders?page=0&size=20` — paginated order history.

Checkout locks and validates the branch/cart/items, checks fulfillment availability, requires a customer-owned address for delivery, recalculates item totals, stores item/address snapshots, uses an idempotency request fingerprint, checks out the cart and writes an `order.created` outbox event. The initial order status is `PAYMENT_PENDING`; **payment creation, verification and capture are not implemented yet**.

Tax rate and fixed delivery fee are configured by `MEALMINUTES_ORDER_TAX_RATE` and `MEALMINUTES_ORDER_DELIVERY_FEE`. These are starter configuration values, not a jurisdiction-aware tax engine; do not enable real checkout until pricing rules have been reviewed for the operating locations.

## Known gaps

Payment intents, verified payment webhooks, refunds/reconciliation, order state transitions, inventory reservation, branch administration, delivery, promotions, Redis/Kafka workflows, OpenAPI generation and cross-tenant end-to-end isolation tests remain pending.
