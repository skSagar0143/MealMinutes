# Implementation plan and release gates

Updated: 2026-10-26. This is a living tracker; statuses must reflect verified repository evidence.

## Phase 1 — Foundation

- [x] Initialize repository documentation and Java 21 / Spring Boot build.
- [x] Add externalized configuration and Actuator health probes.
- [x] Add application context smoke test and JaCoCo HTML/XML report wiring with instruction/branch checks.
- [x] Get the backend CI build green with Flyway migration, 32 tests, JaCoCo report upload, and configured coverage gates.
- [x] Add Docker Compose configuration for local MySQL and Redis.
- [ ] Verify clean local startup and Maven tests on Java 21 outside GitHub Actions.

## Phase 2 — Data and module boundaries

- [x] Add initial MySQL/Flyway platform tenant registry and domain migration.
- [x] Add tenant registry/domain JPA models and repositories.
- [x] Add explicit tenant lifecycle transition policy and provisioning metadata invariants.
- [x] Document platform-vs-tenant data ownership.
- [ ] Implement restricted tenant database provisioning, per-tenant Flyway migration orchestration, safe retries and recovery.

## Phase 3 — Tenant isolation and security (release-blocking)

- [x] Normalize hostnames and resolve exact verified registry mappings for active/provisioned tenants.
- [x] Bind a request-scoped tenant descriptor and clear it on success and exception paths.
- [x] Add unit tests for malformed hosts, tenant lifecycle transitions, resolver rejection, and context cleanup.
- [x] Implement expiring DNS TXT domain ownership proof/verification workflow.
- [x] Implement an isolated bounded LRU tenant datasource pool registry with validated database names and secret-reference credentials.\n- [ ] Wire tenant pools into a tenant-aware EntityManagerFactory/router and prove isolation with integration tests.
- [x] Add tenant membership authorization primitives independent of hostname.
- [x] Add fail-closed stateless Spring Security baseline; only health/info are allow-listed while authentication is not configured.
- [x] Implement optional RS256 access tokens, BCrypt password hashing, opaque hashed refresh sessions with rotation/revocation, and HttpOnly refresh cookies.
- [ ] Add distributed login throttling, password reset/email verification, signing-key rotation, and comprehensive authentication integration tests.
- [ ] Add real cross-tenant, forged-host/header, and concurrent datasource-isolation integration tests.

## Phase 4 — Core business workflows

- [ ] Restaurant/branch and menu management.
- [ ] Customer addresses, cart, checkout and idempotent order creation.
- [ ] Order state machine, snapshots, inventory concurrency and audit history.
- [ ] Payment adapter, verified webhook replay protection, refunds and reconciliation.
- [ ] Delivery assignment, promotions, notifications and reviews.

## Phase 5 — Redis and Kafka

- [ ] Tenant-aware cache keys, TTLs, invalidation and outage behavior.
- [ ] Versioned events, transactional outbox, consumer idempotency, retry and dead-letter recovery.
- [ ] Integration tests using real MySQL/Redis/Kafka containers.

## Phase 6 — Web and mobile

- [ ] React + TypeScript public landing, tenant storefront, restaurant dashboard and admin portal.
- [ ] Accessible responsive glassmorphism design, themes and real API loading/error states.
- [ ] React Native + Expo customer app with secure credential storage and resilient checkout.

## Phase 7 — Delivery quality and operations

- [ ] CI: backend tests, JaCoCo thresholds, static/dependency checks, frontend lint/typecheck/tests/build.
- [ ] Dockerfiles, local Compose health checks and resource limits.
- [x] Add initial security, database ownership, API surface, and testing documentation.
- [ ] Add deployment, TLS/DNS, secrets, backup/restore, monitoring, rollback and incident-response runbooks.
- [ ] Run load/security testing and external production review.

## Release gates

A gate is PASS only when code exists and evidence (commands, output, test results, or deployment verification) is recorded. Otherwise it remains FAIL or BLOCKED. A successful compile alone is not production readiness. The application is not currently production-ready.
