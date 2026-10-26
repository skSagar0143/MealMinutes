# MealMinutes — Multi-Tenant Food Platform

MealMinutes is a multi-tenant restaurant ordering and operations platform. **Zestivo** is the provisional brand from the product specification and must remain configurable.

## Target architecture

- Backend: Java 21, Maven, Spring Boot 4.1.2, modular monolith.
- Data: MySQL, Flyway, platform control-plane database plus a separate business database for each restaurant tenant.
- Security: Spring Security, optional RS256 access tokens, refresh-token rotation/revocation, tenant membership authorization, audit and outbox records.
- Infrastructure: Docker Compose for local MySQL/Redis; Kafka is planned for reliable asynchronous workflows.
- Web: React + TypeScript.
- Mobile: React Native + Expo + TypeScript.
- Quality: JUnit, MySQL-backed integration tests, JaCoCo, GitHub Actions and operational documentation.

## Current implementation status

Implemented foundations and workflows include:

- Tenant registry, verified hostname/DNS TXT ownership verification, tenant lifecycle state machine and protected tenant onboarding.
- Restricted tenant database provisioning components, encrypted local development secret storage, per-tenant Flyway orchestration, bounded tenant datasource pools and a separate tenant JDBC transaction manager.
- Optional RS256 authentication, password hashing, hashed refresh sessions with rotation/revocation, HttpOnly refresh cookies and tenant membership/role authorization.
- Public tenant restaurant/menu read APIs and role-protected menu category/item management with audit/outbox writes.
- Customer cart read/add/update/remove APIs, current price snapshots, server-calculated subtotals and a database constraint for one active cart per customer/branch.
- Idempotent checkout/order creation and customer order read/list APIs. Checkout records a `PAYMENT_PENDING` order, snapshots order items/address, calculates configured tax/delivery totals, checks out the cart and writes an outbox event.
- Responsive React landing/storefront and React Native/Expo menu-browsing foundation.

**This is not production-ready yet.** Order pricing is a configurable starter policy, not jurisdiction-aware tax compliance. Checkout does not yet create or verify a real payment. Still pending: payment-provider integration, verified webhook/replay handling, refunds and reconciliation; order state transitions and restaurant branch operations; customer address management; inventory concurrency; Redis cache/rate limiting; Kafka outbox dispatch/consumer idempotency/retry/dead-letter recovery; complete restaurant/admin dashboards and mobile authentication/cart/checkout; end-to-end cross-tenant security tests; deployment hardening, TLS, backups, monitoring and runbooks.

## Latest verified backend CI

[Backend CI run 40253109523](https://github.com/skSagar0143/MealMinutes/actions/runs/40253109523) completed successfully: **146 tests, zero failures/errors**, Flyway tenant migrations V1–V3, and an uploaded JaCoCo report. Measured aggregate coverage: 80.65% instructions, 82.24% lines, 65.58% branches and 80.10% methods. The configured gates are 75% instruction and 60% branch coverage; this is an improving baseline, not 100% coverage or proof of production readiness.

Web CI has passed unit tests, TypeScript checking and a production build. The initial mobile TypeScript CI has passed. Current commits must be revalidated by CI after each change.

## Local development

Use Java 21 and Docker Desktop. Copy `.env.example` to `.env`, review every local-only secret, then run:

```powershell
docker compose up -d mysql redis
mvn -B -f backend/pom.xml clean verify
```

Never use example credentials or the local encrypted-file secret store as a production secret-management system.

## Documentation

- [Architecture](docs/architecture.md)
- [Implementation plan and release gates](docs/implementation-plan.md)
- [Development setup](docs/development-setup.md)
- [Tenant isolation and security](docs/tenant-isolation.md)
- [Security design and gaps](docs/security.md)
- [Database design](docs/database-design.md)
- [API guide](docs/api-guide.md)
- [Testing and JaCoCo](docs/testing.md)
