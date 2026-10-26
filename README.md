# MealMinutes — Multi-Tenant Food Platform

MealMinutes is the repository for a multi-tenant restaurant ordering and operations platform. **Zestivo** is the provisional brand from the product specification; the brand must remain configurable.

## Target architecture

- Backend: Java 21, Maven, Spring Boot 4.1.2 and a modular monolith with versioned REST APIs.
- Data: MySQL, Flyway, a platform control-plane database and a separate business database for each restaurant tenant.
- Security: Spring Security, RS256 access tokens, refresh rotation/revocation, tenant membership authorization and audit trails.
- Infrastructure: Docker Compose for local MySQL and Redis; Kafka for asynchronous workflows when event reliability patterns are implemented.
- Web: React + TypeScript (official React docs list 19.3 as of 2026-10-26).
- Mobile: React Native + Expo + TypeScript after core APIs stabilize.
- Quality: JUnit, integration tests, Testcontainers, JaCoCo, CI and operational documentation.

Spring Boot 4.1.2 is the stable release selected from the official docs checked on 2026-10-26 and supports Java 21. Dependencies should use the Spring Boot BOM unless a documented compatibility decision requires otherwise.

## Actual implementation status

The repository was empty at the start. The foundation now contains a Spring Boot entry point, externalized configuration, an application-context smoke test, JaCoCo wiring, local MySQL/Redis Compose services, Java 21 backend CI, and architecture/setup/testing documentation. The tenant foundation includes strict hostname normalization, verified-domain lookup, active/provisioned tenant checks, request-scoped context cleanup, lifecycle/provisioning state transitions, a bounded LRU datasource-pool component, and unit/integration tests. A fail-closed Spring Security baseline has now been added: health/info are explicitly allow-listed while all other requests are denied until real authentication and authorization are implemented. Flyway auto-configuration was corrected to use the Spring Boot 4 starter.

**The business platform is not yet implemented or production-ready.** Tenant database provisioning and tenant-aware JPA routing, tenant provisioning infrastructure implementation, frontend and production wiring for login/refresh/revocation, complete membership-protected business APIs, restaurant/menu/order/payment APIs, Redis integration, Kafka workflows, web UI, mobile app, and broad end-to-end/security tests remain pending. The last verified backend CI run before identity/authentication changes passed 44 tests and uploaded a JaCoCo report. The latest run after adding identity/authentication code exposed one test failure around blank-email validation; that defect is being fixed and the updated build must pass before this increment is considered verified. The fail-closed security baseline denies API requests until configured RS256 authentication is enabled. The repository now includes optional login, short-lived signed access tokens, SHA-256-hashed rotating refresh sessions in HttpOnly SameSite cookies, account lock/disable state, a platform-admin bootstrap hook, and tenant membership authorization primitives. Configure RSA key files and the trusted frontend origin before enabling it; without keys, API routes remain denied. Track progress in [the implementation plan](docs/implementation-plan.md); a feature is complete only after its code and relevant test evidence exist.

## Local development

Prerequisites: JDK 21, Maven, Git and Docker Desktop. See [development setup](docs/development-setup.md).

1. Copy `.env.example` to `.env` for disposable local development only. The default values are intentionally insecure and must never be used in production.
2. Start the local dependencies: `docker compose up -d`.
3. Check container status: `docker compose ps`.
4. Run the backend tests: `mvn -f backend/pom.xml verify`.
5. Start the API: `mvn -f backend/pom.xml spring-boot:run`.
6. Check `http://localhost:8080/actuator/health`.

These commands describe the intended local workflow; they are not claims that a build or test has already run in this environment.

## Documentation

- [Architecture](docs/architecture.md)
- [Implementation plan and release gates](docs/implementation-plan.md)
- [Development setup](docs/development-setup.md)
- [Tenant isolation requirements](docs/tenant-isolation.md)
- [Testing strategy and JaCoCo](docs/testing.md)\n- [Security design and gaps](docs/security.md)\n- [Database ownership](docs/database-design.md)\n- [API guide](docs/api-guide.md)
- [ADR 0001: modular monolith](docs/architecture-decisions/0001-modular-monolith.md)
- [ADR 0002: database-per-tenant](docs/architecture-decisions/0002-database-per-tenant.md)

## Security principle

A tenant hostname identifies a tenant; it does not authorize a user. The backend must resolve normalized hostnames through trusted registry data, verify tenant status, establish and clear request-scoped context, and independently verify that the authenticated principal is authorized for that tenant. Client-provided tenant IDs and database details are never sufficient proof of access.
