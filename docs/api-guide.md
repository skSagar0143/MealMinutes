# API guide

Routes marked tenant-backed require a verified tenant hostname, configured tenant datasource/provisioning, and the stated authentication/authorization. Tenant-backed features are disabled in the default local configuration unless enabled explicitly.

## Health and platform

- `GET /actuator/health` — health probe.
- `GET /actuator/info` — safe application metadata.
- `POST /api/v1/platform/tenants` — register a tenant and create a DNS TXT ownership challenge.
- `GET /api/v1/platform/tenants` — paginated tenant list.
- `POST /api/v1/platform/tenants/{tenantId}/approve` — approve after domain verification.
- `POST /api/v1/platform/tenants/{tenantId}/provision` — provision tenant database and apply tenant migrations.
- `POST /api/v1/platform/tenants/{tenantId}/suspend` and `/reactivate` — lifecycle actions.
- `POST /api/v1/tenant-domains/verify` — verify the DNS TXT challenge.

## Authentication

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`

Protected routes require RSA key configuration. Access tokens use RS256. Refresh tokens rotate and are stored hashed server-side; the refresh token is carried in an HttpOnly cookie. Login throttling, email verification, password reset, and key rotation remain release blockers.

## Public tenant storefront

Requires tenant datasource configuration and a verified tenant host.

- `GET /api/v1/storefront/menu/restaurant`
- `GET /api/v1/storefront/menu/categories`
- `GET /api/v1/storefront/menu/items?page=0&size=20&category={slug}&search={term}`
- `GET /api/v1/storefront/branches` — active branches for the tenant.
- `GET /api/v1/storefront/branches/{branchId}/operating-hours` — hours for an active branch.

Pagination is bounded. Storefront menu queries return only active categories and available items; public branch endpoints do not expose inactive branches.

## Restaurant profile and branch operations

These endpoints require an active tenant membership. OWNER and MANAGER can write; OWNER, MANAGER and STAFF can read.

- `GET /api/v1/restaurant/profile`
- `PUT /api/v1/restaurant/profile` — create or update the tenant's active profile.
- `GET /api/v1/restaurant/branches?includeInactive=false`
- `POST /api/v1/restaurant/branches`
- `PUT /api/v1/restaurant/branches/{branchId}`
- `PATCH /api/v1/restaurant/branches/{branchId}/active` — JSON: `{"active": true}`.
- `GET /api/v1/restaurant/branches/{branchId}/operating-hours`
- `PUT /api/v1/restaurant/branches/{branchId}/operating-hours` — provide one entry for each ISO weekday (1=Monday through 7=Sunday).

Branch slugs are unique within a tenant. An operating-hours replacement must contain all seven days; a closed day must omit opening and closing times. The database enforces valid weekdays and consistent open/closed time fields.

## Restaurant menu management

Requires active membership in the resolved tenant. OWNER and MANAGER can write; OWNER, MANAGER and STAFF can read.

- `GET /api/v1/restaurant/menu/categories?includeInactive=false`
- `POST /api/v1/restaurant/menu/categories`
- `PATCH /api/v1/restaurant/menu/categories/{categoryId}/active`
- `GET /api/v1/restaurant/menu/items?categoryId={id}&includeUnavailable=false&includeInactiveCategories=false`
- `POST /api/v1/restaurant/menu/items`
- `PATCH /api/v1/restaurant/menu/items/{itemId}/availability`

Menu management writes use tenant-scoped transactions and record audit/outbox data.

## Customer addresses

Requires a CUSTOMER platform role and active CUSTOMER membership in the resolved tenant. All operations are scoped to the authenticated token subject.

- `GET /api/v1/customer/addresses`
- `POST /api/v1/customer/addresses`
- `PUT /api/v1/customer/addresses/{addressId}`
- `DELETE /api/v1/customer/addresses/{addressId}`

The first address becomes the default automatically. Deleting the current default promotes another saved address when one exists. A generated unique key enforces at most one default address per customer.

## Customer cart

Requires CUSTOMER role and tenant database configuration.

- `GET /api/v1/customer/cart?branchId={branchId}`
- `POST /api/v1/customer/cart/items` — JSON: `branchId`, `itemId`, `quantity` (1–99), optional `notes`.
- `PUT /api/v1/customer/cart/{cartId}/items/{itemId}?quantity={quantity}`
- `DELETE /api/v1/customer/cart/{cartId}/items/{itemId}`

The customer ID comes from the signed token subject, never a request field. The backend validates active branches and available menu items, uses parameterized SQL, snapshots current menu prices and calculates subtotals server-side.

## Customer orders and checkout

Order endpoints are enabled only when tenant provisioning and order pricing are enabled, with tenant datasource/secrets configured.

- `POST /api/v1/customer/orders` — requires `Idempotency-Key` header and JSON fields `cartId`, `branchId`, `fulfillmentType` (`PICKUP` or `DELIVERY`), optional `deliveryAddressId`, optional `customerNote`.
- `GET /api/v1/customer/orders/{orderId}` — get an order owned by the caller.
- `GET /api/v1/customer/orders?page=0&size=20` — paginated order history.
- `POST /api/v1/customer/orders/{orderId}/cancel` — cancel an order only while payment is pending and no payment attempt exists; an order with a payment attempt must be reconciled first.

Checkout locks and validates the branch/cart/items, checks fulfillment availability, requires a customer-owned address for delivery, recalculates item totals, stores item/address snapshots, uses an idempotency request fingerprint, checks out the cart and writes an `order.created` outbox event. Tax rate and fixed delivery fee are starter configuration values, not a jurisdiction-aware tax engine.

## Restaurant order lifecycle

Requires active OWNER, MANAGER or STAFF membership in the tenant.

- `GET /api/v1/restaurant/orders?branchId={branchId}&page=0&size=20`
- `PATCH /api/v1/restaurant/orders/{orderId}/status` — JSON: `{"status":"ACCEPTED","note":"Optional note"}`.

Allowed paid-order transitions are CONFIRMED → ACCEPTED → PREPARING → READY_FOR_PICKUP (pickup) or OUT_FOR_DELIVERY (delivery) → DELIVERED. Invalid transitions are rejected. Each transition writes status history, an audit record and an outbox event in the same tenant transaction.

## Payments

Payment endpoints require `MEALMINUTES_PAYMENT_ENABLED=true`, tenant provisioning, security and order pricing enabled, and valid provider/key configuration.

- `POST /api/v1/customer/orders/{orderId}/payment-intents` — requires CUSTOMER role and `Idempotency-Key`.
- `POST /api/v1/webhooks/razorpay` — requires `X-Razorpay-Signature` and `X-Razorpay-Event-Id` headers; webhook signatures are verified against the raw request body.

Payment intent creation, signed webhook processing, event deduplication, payment/order state updates and outbox writes are implemented and covered by tests. Refund execution, provider reconciliation jobs, manual recovery tooling and live provider verification are not complete.

## Remaining release gaps

- Inventory reservation and stock consistency.
- Redis cache keys/invalidation and safe outage behavior.
- Kafka outbox dispatch, consumer idempotency, retries and dead-letter recovery.
- Customer web/mobile sign-in, cart/checkout/order history and notifications.
- Restaurant dashboard and platform administration UI.
- Promotions, delivery assignment, notifications and reviews.
- Jurisdiction-aware tax/pricing policy.
- OpenAPI contract generation, end-to-end/load/security testing, production deployment, backups, monitoring and incident runbooks.

Do not treat this application as production-ready until the release-blocking isolation, payment recovery, security and operational checks have passed.
