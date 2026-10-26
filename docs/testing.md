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

## Latest verified CI snapshot

[Backend CI run 40253109523](https://github.com/skSagar0143/MealMinutes/actions/runs/40253109523) passed **146 tests with zero failures/errors**, applied tenant migrations V1–V3 against MySQL, generated the JaCoCo report and passed the configured quality gates.

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
- Redis invalidation/outage, Kafka outbox delivery, consumer idempotency, retry/DLT/replay.
- Frontend and mobile end-to-end/runtime tests, accessibility, load tests and external security review.

Coverage thresholds should rise toward 90%+ instruction and 80%+ branch coverage as critical workflows are added; do not lower them just to make a failing build green.
