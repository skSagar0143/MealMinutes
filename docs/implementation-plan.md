# Implementation plan and release gates

Updated: 2026-10-26. This is a living tracker; a feature is complete only when its code and relevant test evidence exist.

## Phase 1 — Foundation

- [x] Java 21 / Spring Boot 4.1.2 Maven build, externalized configuration and Actuator health probes.
- [x] Docker Compose local MySQL and Redis services.
- [x] GitHub Actions backend CI and JaCoCo HTML/XML artifact upload.
- [x] Current verified backend run: 146 tests, zero failures/errors; 80.65% instruction, 82.24% line, 65.58% branch and 80.10% method coverage.
- [ ] Verify clean local Windows/STS startup and `mvn clean verify` outside GitHub Actions.

## Phase 2 — Tenant data and provisioning

- [x] Platform tenant registry/domain schema and JPA persistence.
- [x] Verified hostname/DNS TXT ownership proof and tenant lifecycle transitions.
- [x] Restricted tenant database provisioning, encrypted local secret storage and per-tenant Flyway migration orchestration components.
- [x] Bounded tenant datasource pool, tenant-aware JDBC adapter and separate tenant transaction manager when provisioning is enabled.
- [x] Tenant schema migrations V1–V3 and MySQL-backed migration integration test.
- [ ] Prove full provisioning retries/recovery with deployment-like credentials and failure injection.
- [ ] Add concurrent cross-tenant read/write isolation tests against separate real databases.

## Phase 3 — Security (release-blocking)

- [x] Optional RS256 access tokens, password hashing, hashed refresh sessions with rotation/revocation and HttpOnly refresh cookies.
- [x] Fail-closed security baseline and tenant membership/role authorization.
- [x] Audit and outbox writes for menu management.
- [ ] Distributed login throttling, password reset/email verification, key rotation and comprehensive JWT/session security tests.
- [ ] Prove forged-host/header isolation, cross-tenant authorization and concurrent datasource eviction behavior.
- [ ] Production secret manager, TLS, security headers/CSP, dependency/container scans and external security review.

## Phase 4 — Restaurant, cart and order workflows

- [x] Public tenant restaurant/menu read APIs with bounded pagination and validation.
- [x] Tenant membership/role-protected menu category/item management.
- [x] Customer cart read/add/update/remove, quantity bounds, price snapshots and server-calculated subtotals.
- [x] Database uniqueness for one ACTIVE cart per customer/branch and duplicate-create recovery.
- [x] Idempotent checkout/order creation with request fingerprint, branch/availability checks, address and item snapshots, configured tax/delivery calculation, cart checkout and outbox event.
- [x] Customer order detail and paginated history endpoints.
- [ ] Payment-provider integration and payment-intent lifecycle.
- [ ] Verified payment webhooks, replay protection, refunds, reconciliation and idempotent payment recovery.
- [ ] Restaurant branch/profile/operating-hours management, inventory reservation, order state transitions, cancellations and audit history.
- [ ] Customer address CRUD, coupons, delivery assignment, notifications and reviews.
- [ ] Replace global starter pricing with a jurisdiction-aware tax and delivery pricing policy before accepting real orders.

## Phase 5 — Redis and Kafka

- [ ] Tenant-aware Redis cache keys, TTLs, invalidation and safe outage behavior.
- [ ] Transactional outbox dispatcher to Kafka, versioned event schemas, consumer idempotency, retries and dead-letter recovery.
- [ ] Real MySQL/Redis/Kafka integration tests, replay tooling and operational dashboards.

## Phase 6 — Web and mobile

- [x] React + TypeScript landing page and tenant menu storefront with responsive layout, theme toggle and loading/error/empty states.
- [x] React Native + Expo menu-browsing foundation with search/category filters and loading/error/empty states.
- [ ] Customer web/mobile sign-in, secure token storage, cart, checkout, order history and notifications.
- [ ] Restaurant dashboard, branch/menu operations and platform admin portal.
- [ ] Accessibility, device/browser compatibility, frontend end-to-end tests and mobile runtime tests.

## Phase 7 — Release readiness

- [x] Backend CI runs tests and enforces 75% instruction / 60% branch coverage; JaCoCo report artifact uploaded.
- [x] Web CI unit tests, TypeScript check and production build.
- [x] Initial mobile TypeScript CI.
- [ ] Raise coverage toward 90%+ instruction and 80%+ branch coverage, then test all critical failure paths.
- [ ] API contract/OpenAPI checks, load tests, security tests, migration rollback/recovery tests and disaster-recovery drill.
- [ ] Production Docker images, deployment manifests, TLS/domain setup, secret rotation, backup/restore, monitoring/alerting, SLOs, incident runbooks and payment-provider live verification.

**Release rule:** do not call this project production-ready until every release-blocking isolation, security, payment, recovery and operational gate above has passed with evidence.
