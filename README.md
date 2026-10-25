# MealMinutes — Multi-Tenant Food Platform

MealMinutes is the repository for a multi-tenant restaurant ordering and operations platform. The product brand is configurable; **Zestivo** is the provisional brand described in the original product specification.

## Architecture direction

- **Backend:** Java 21, Maven, Spring Boot 4.1.2, modular monolith and versioned REST API.
- **Persistence:** MySQL with Flyway migrations. A central platform database owns tenant registration and routing metadata; each restaurant tenant receives a separate business database.
- **Security:** Spring Security, short-lived RS256 access tokens, refresh-token rotation/revocation, explicit tenant membership authorization, validation and audit trails.
- **Infrastructure:** Docker Compose for local development; Redis for bounded tenant-aware caching and Kafka for asynchronous events when their reliability boundaries are implemented.
- **Web:** React + TypeScript; official React documentation currently lists React 19.3.
- **Mobile:** React Native + Expo + TypeScript, added after the core APIs are stable.
- **Quality:** JUnit, integration tests, Testcontainers, JaCoCo, CI checks and operational documentation.

Versions are selected from official project documentation checked on 2026-10-26. Spring Boot 4.1.2 supports Java 21 (Java 17 minimum); use compatible dependency versions managed by the Spring Boot BOM unless an explicit compatibility decision is documented.

## Repository status

This repository started empty. Implementation is incremental and must not be described as production-ready until the release gates in `docs/implementation-plan.md` have verifiable evidence. Current first milestone: establish the build, health endpoint, safe configuration templates, local infrastructure and documentation.

## Local development

See [`docs/development-setup.md`](docs/development-setup.md) for prerequisites and setup. Copy `.env.example` to `.env` for local-only values; never commit real secrets. Docker Compose is intended for local development, not by itself a complete production deployment.

## Documentation

- [Architecture](docs/architecture.md)
- [Implementation plan and release gates](docs/implementation-plan.md)
- [Development setup](docs/development-setup.md)
- [Security and tenant isolation](docs/tenant-isolation.md)
- [Testing](docs/testing.md)

## Important

Tenant hostname resolution is not authorization. The backend must resolve a normalized hostname against trusted platform registry data, verify tenant status, establish and clear request-scoped tenant context, and independently verify that the authenticated principal is authorized for that tenant. Client-provided tenant IDs and database details are never sufficient proof of access.
