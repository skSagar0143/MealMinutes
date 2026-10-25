# Implementation plan and release gates

Updated: 2026-10-26. This is a living tracker; statuses must reflect verified repository evidence.

## Phase 1 — Foundation

- [x] Initialize repository documentation and Java 21 / Spring Boot build.
- [x] Add safe default configuration and actuator health probes.
- [x] Add an application context smoke test and JaCoCo report wiring.
- [ ] Run Maven build and tests in a Java 21 environment.
- [x] Add Docker Compose configuration for local MySQL and Redis.\n- [ ] Verify clean local startup and Maven tests in a Java 21 environment.

## Phase 2 — Data and module boundaries

- [x] Add initial MySQL and Flyway platform registry migrations.
- [ ] Define tenant registry and lifecycle state machine.
- [ ] Document platform-vs-tenant data ownership.
- [ ] Add tenant database provisioning/migration orchestration with safe retries.

## Phase 3 — Tenant isolation and security (release-blocking)

- [ ] Trusted hostname resolution and request-scoped tenant context cleanup.
- [ ] Bounded tenant datasource/pool lifecycle.
- [ ] Tenant membership authorization independent of hostname.
- [ ] RS256 access tokens, refresh rotation/revocation, password hashing and throttling.
- [ ] Cross-tenant, forged-header, concurrency and context-leak tests.

## Phase 4 — Core business workflows

- [ ] Restaurant/branch and menu management.
- [ ] Customer addresses, cart, checkout and idempotent order creation.
- [ ] Order state machine, snapshots, inventory concurrency and audit history.
- [ ] Payment adapter, verified webhook replay protection, refunds and reconciliation.
- [ ] Delivery assignment, promotions, notifications and reviews.

## Phase 5 — Redis and Kafka

- [ ] Tenant-aware cache keys, TTLs, invalidation and outage behavior.
- [ ] Versioned events, outbox, consumer idempotency, retry and dead-letter recovery.
- [ ] Integration tests using real MySQL/Redis/Kafka containers where feasible.

## Phase 6 — Web and mobile

- [ ] React + TypeScript public landing, tenant storefront, restaurant dashboard and admin portal.
- [ ] Accessible responsive glassmorphism design, themes and real API loading/error states.
- [ ] React Native + Expo customer app with secure credential storage and resilient checkout.

## Phase 7 — Delivery quality and operations

- [ ] CI: backend tests, JaCoCo, static/dependency checks, frontend lint/typecheck/tests/build.
- [ ] Dockerfiles, local Compose health checks and resource limits.
- [ ] Runbooks for deployment, TLS/DNS, secrets, backup/restore, monitoring and rollback.
- [ ] Load/security testing and external production review.

## Release gates

A gate is PASS only when code exists and evidence (commands, output, test results, or deployment verification) is recorded. Otherwise it remains FAIL or BLOCKED. A successful compile alone is not production readiness.
