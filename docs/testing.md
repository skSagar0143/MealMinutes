# Testing and coverage

## Local commands

Run from the repository root in PowerShell after Java 21 and Maven are available and the local MySQL service is running:

```powershell
docker compose up -d mysql
mvn -B -f backend/pom.xml clean verify
```

The build runs unit tests, MySQL-backed Spring context/repository integration tests, generates JaCoCo HTML and XML reports, and checks configured instruction/branch coverage thresholds.

Reports are generated at:

- HTML: `backend/target/site/jacoco/index.html`
- XML: `backend/target/site/jacoco/jacoco.xml`

The GitHub Actions workflow uploads the JaCoCo report directory as a workflow artifact when it exists.

## Current test categories

- Hostname normalization and malformed input.
- Tenant context binding, nested context rejection, and cleanup.
- Tenant lifecycle transition and provisioning invariants.
- Tenant resolver acceptance/rejection paths using mocked repositories.
- Tenant context filter success, unknown-host handling, path exclusions, and exception cleanup.
- Platform tenant registry persistence and application context startup against MySQL.

## Required but not yet covered

Cross-tenant data reads/writes and concurrent requests, real tenant datasource provisioning and migrations, JWT expiry/revocation and role authorization, branch membership authorization, concurrent inventory, idempotent checkout, payment webhook replay/refunds, Redis invalidation/outage, Kafka retry/DLT/replay, sensitive-data checks, frontend lint/typecheck/tests/build, mobile tests, load testing, and external security testing.

Coverage is a regression guard, not proof of correctness. Tests must assert business invariants and failure modes. The configured coverage thresholds are an initial quality gate and should be raised as tested modules are added; do not lower them merely to make a failing build green.


## Latest verified CI snapshot

GitHub Actions run [40248829743](https://github.com/skSagar0143/MealMinutes/actions/runs/40248829743) completed successfully with 81 tests, Flyway migrations V1–V3, and an uploaded `jacoco-report` artifact. The report measured 80.42% instruction coverage, 81.86% line coverage, and 63.35% branch coverage. The verified gate at that commit was 70% instruction and 55% branch; the next change raises those gates to 75% and 60%, respectively. These are aggregate project figures, not evidence that every critical scenario is covered.
