# Testing and coverage

## Local commands

Run from the repository root in PowerShell after Java 21 and Docker Desktop are available:

```powershell
docker compose up -d mysql
mvn -B -f backend/pom.xml clean verify
```

The build runs unit tests, MySQL-backed Spring context/repository and tenant migration integration tests, generates JaCoCo HTML/XML reports, and enforces instruction/branch thresholds.

Reports:
- HTML: `backend/target/site/jacoco/index.html`
- XML: `backend/target/site/jacoco/jacoco.xml`

GitHub Actions uploads the JaCoCo report directory as the `jacoco-report` workflow artifact.

## Verified CI snapshots

The pre-Redis main baseline [run 40380957054](https://github.com/skSagar0143/MealMinutes/actions/runs/40380957054) passed **195 tests with zero failures/errors** and generated the JaCoCo report. The tenant-aware Redis cache feature branch [run 40381216478](https://github.com/skSagar0143/MealMinutes/actions/runs/40381216478) passed **204 tests with zero failures/errors** and generated the report. Check the [latest main-branch workflow](https://github.com/skSagar0143/MealMinutes/actions) for post-merge verification.

| Metric | Measured coverage |
|---|---:|
| Instructions | 80.65% |
| Lines | 82.24% |
| Branches | 65.58% |
| Methods | 80.10% |

Configured gates: 75% instruction and 60% branch coverage. These are aggregate project metrics, not 100% coverage. The report is a regression guard, not proof that every critical scenario is tested.

## Covered test categories

- Tenant hostname normalization, DNS verification, lifecycle and resolver rejection.
- Tenant context binding/filter cleanup and datasource-pool behavior.
- Tenant provisioning components, secret storage and Flyway migration verification.
- RS256/authentication service and tenant membership authorization.
- Public storefront query validation, menu management, role authorization and audit/outbox writes.
- Cart concurrency, quantity limits, item validation and subtotal calculation.
- Idempotent order creation, request fingerprint conflict handling, pickup/delivery pricing calculation, address snapshots, cart checkout and order read/pagination.

## Required additional coverage

- Cross-tenant read/write isolation and concurrent datasource eviction against separate databases.
- Production provisioning retries/recovery and secret-manager behavior.
- JWT expiry/revocation, login throttling, key rotation and comprehensive authorization tests.
- Payment provider success/failure, webhook signature verification/replay, refunds and reconciliation.
- Order transitions, inventory contention, cancellation/refund recovery.
- Redis tenant-scoped cache-key/TTL/invalidation/outage unit tests; real-Redis integration and rollback tests remain pending. Kafka outbox dispatcher delivery/retry/dead-letter unit tests pass; consumer idempotency, real-broker replay and dead-letter re-drive remain pending.
- Frontend and mobile end-to-end/runtime tests, accessibility, load tests and external security review.

Coverage thresholds should rise toward 90%+ instruction and 80%+ branch coverage as critical workflows are added; do not lower them just to make a failing build green.
