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

The repository was empty at the start. The initial foundation now contains a Spring Boot entry point, externalized configuration, an application-context smoke test, JaCoCo wiring, local MySQL/Redis Compose services, a Java 21 backend CI workflow, and architecture/setup/testing documentation. The tenant foundation now also includes strict hostname normalization, exact verified-domain lookup, active/provisioned tenant checks, request-scoped context cleanup, an explicit lifecycle/provisioning state machine, focused unit tests, and initial security/database/API/testing documentation. Flyway auto-configuration was corrected to use the Spring Boot 4 starter after CI exposed that the earlier dependency set did not execute migrations.

**The business platform is not yet implemented or production-ready.** Actual tenant database provisioning/routing, authentication and authorization, business APIs, Redis integration, Kafka workflows, web UI, mobile app, and broad end-to-end/security tests remain pending. CI has exposed a Flyway wiring failure; the missing Spring Boot 4 Flyway starter has been added, and the subsequent run must be checked before the build can be considered green. Track progress in [the implementation plan](docs/implementation-plan.md); a feature is complete only after its code and relevant test evidence exist.

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
