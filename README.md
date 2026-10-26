# MealMinutes — Multi-Tenant Food-Tech Platform

MealMinutes is a multi-tenant restaurant ordering and operations platform. **Zestivo** is the provisional product brand; the UI should keep branding configurable.

## Architecture

- **Backend:** Java 21, Maven, Spring Boot 4.1.2, modular monolith.
- **Data:** MySQL and Flyway, with a platform control-plane database and a separate business database for each restaurant tenant.
- **Security:** Spring Security, optional RS256 access tokens, hashed refresh sessions with rotation/revocation, tenant membership authorization and audit records.
- **Messaging:** Kafka transactional outbox dispatcher with event metadata, bounded polling, retries and database dead-letter state; consumer-side idempotency/re-drive is not complete.
- **Local infrastructure:** Docker Compose for MySQL, Redis and Kafka.
- **Web:** React + TypeScript.
- **Mobile:** React Native + Expo + TypeScript.
- **Quality:** JUnit/Mockito, MySQL-backed integration tests, JaCoCo and GitHub Actions.

## Implemented foundations

- Tenant registry, normalized hostnames, DNS TXT domain-ownership verification and tenant lifecycle transitions.
- Restricted tenant database provisioning components, encrypted local-development credential storage, per-tenant Flyway migrations, bounded tenant datasource pools and tenant-aware JDBC/transaction management.
- Optional RS256 authentication, password hashing, hashed refresh-token sessions, refresh rotation/revocation and HttpOnly refresh cookies.
- Tenant membership and role authorization for restaurant operations.
- Restaurant profile, branch and operating-hours management.
- Public tenant menu/storefront reads and protected menu category/item management.
- Customer address CRUD, customer cart APIs, server-calculated subtotals and a database constraint for one active cart per customer/branch.
- Idempotent checkout/order creation, customer order detail/history, restaurant order transitions, order history/audit/outbox records and unpaid-order cancellation rules.
- Razorpay payment-intent creation, raw-body webhook signature verification, webhook event deduplication, payment/order state changes and transactional outbox writes.
- React landing page/storefront and React Native/Expo menu-browsing foundation.
- Tenant-aware Redis storefront caching with tenant/version-scoped keys, TTLs, after-commit version invalidation and safe database fallback on cache errors.
- Docker Compose definitions for MySQL, Redis and Kafka.

## Latest verified backend CI

[Backend CI run 40380957054](https://github.com/skSagar0143/MealMinutes/actions/runs/40380957054) completed successfully with **195 tests, zero failures/errors**, a generated JaCoCo report and the configured coverage gates. The gates currently require at least 75% instruction coverage and 60% branch coverage. This is not 100% coverage and does not prove production readiness.

The latest verified run validates the repository state at that commit. Each subsequent code change must be revalidated by its own CI run.

## Local development

Prerequisites: JDK 21, Maven, Node.js LTS and Docker Desktop. Review `.env.example` and replace local-only values as needed. Do not commit real credentials or private keys.

Start local infrastructure:

```powershell
docker compose up -d mysql redis kafka
```

Run backend tests and generate coverage:

```powershell
mvn -B -f backend/pom.xml clean verify
```

JaCoCo reports are generated at:

- HTML: `backend/target/site/jacoco/index.html`
- XML: `backend/target/site/jacoco/jacoco.xml`

The tenant-backed workflows are feature-flagged and require the platform database, tenant datasource/provisioning configuration, valid tenant secrets and RSA key configuration. Payment and outbox features also require their corresponding flags and provider/broker configuration. See the API guide and development setup before enabling them.

## Remaining release blockers

This project is **not production-ready yet**. Major remaining work includes:

- Real-Redis integration tests for tenant isolation, transaction rollback invalidation, cold-cache behavior and load/outage validation.
- Kafka consumer idempotency, schema compatibility, retry/replay handling and dead-letter re-drive tooling.
- Customer registration/verification, login throttling, password reset, key rotation and stronger security testing.
- Customer web/mobile sign-in, cart, checkout, order history and notification experiences.
- Restaurant dashboard and platform administration UI.
- Inventory reservation, refunds and payment reconciliation, delivery assignment, coupons/promotions, notifications and reviews.
- Jurisdiction-aware tax/pricing policy, API/OpenAPI contract checks and end-to-end/load tests.
- Production Docker image hardening, TLS/domain configuration, secret management, backup/restore, monitoring/alerting and incident runbooks.

Do not mark the platform production-ready until tenant isolation, payment recovery, security, integration testing and operational release gates pass with evidence.

## Documentation

- [Architecture](docs/architecture.md)
- [Implementation plan and release gates](docs/implementation-plan.md)
- [Development setup](docs/development-setup.md)
- [Tenant isolation](docs/tenant-isolation.md)
- [Security](docs/security.md)
- [Database design](docs/database-design.md)
- [API guide](docs/api-guide.md)
- [Testing and JaCoCo](docs/testing.md)
- [Redis caching](docs/redis.md)
- [Kafka eventing and recovery](docs/kafka.md)
- [Deployment and release operations](docs/deployment.md)
- [Backup and recovery](docs/backup-and-recovery.md)
