# Deployment and release operations

## Current status

This repository does **not** currently have a verified production deployment, purchased domain, TLS certificate, cloud account configuration, or production secret manager. The instructions below are a deployment checklist, not a claim that a live environment exists.

Docker Compose is intended for local development and integration testing. Do not expose the Compose ports directly to the public internet as a production deployment.

## Local startup

From PowerShell at the repository root:

```powershell
docker compose up -d mysql redis kafka
docker compose ps
mvn -B -f backend/pom.xml clean verify
```

Confirm that the containers are healthy before running integration workflows. The backend's default feature flags keep tenant provisioning, security, payment and the outbox dispatcher disabled until their required configuration is supplied.

## Production configuration checklist

1. Provision a managed or appropriately operated MySQL instance for the platform control-plane database.
2. Provision tenant databases using a restricted database-administration identity. The application runtime identity must not have unrestricted CREATE/DROP privileges.
3. Configure a supported Redis deployment if the tenant-aware cache is enabled. Use authentication, TLS where supported, bounded connection/read timeouts and a documented outage policy.
4. Configure a supported Kafka cluster if the outbox dispatcher is enabled. Use authenticated TLS connections, appropriate ACLs, durable topic configuration and monitored consumer groups.
5. Store DB credentials, Redis credentials, Kafka credentials, RSA private keys and payment-provider secrets in a production secret manager. Never commit them to Git or bake them into images.
6. Configure Java 21 runtime settings, resource limits, graceful shutdown, structured logs and health endpoints.
7. Configure a reverse proxy or ingress to terminate TLS and route API, platform-admin, restaurant portal and verified tenant hostnames.
8. Use exact CORS origins. Do not use wildcard credentialed CORS.
9. Configure Actuator access so public health responses reveal no component details, credentials, internal hostnames or stack traces.
10. Configure backups, restore testing, metrics, alerting, incident contacts and rollback procedures before production traffic.

## Hostnames and TLS

The intended host convention is:

- `zestivo.com`: public landing page.
- `*.zestivo.com`: tenant storefronts after verification.
- `restaurant.zestivo.com`: restaurant portal.
- `admin.zestivo.com`: platform administration.
- `api.zestivo.com`: backend API.

These are proposed names only. A wildcard DNS record does not prove ownership of a custom domain and does not automatically create a valid TLS certificate. Configure wildcard DNS, certificate issuance/renewal, host-header validation, reserved hostnames and verified tenant mappings separately.

Do not trust a client-supplied tenant header as authorization. Tenant identity must come from the verified host registry, and the authenticated user must have membership/permission for the resolved tenant.

## Release pipeline

Before each deployment:

- [ ] CI succeeds for backend tests, JaCoCo checks, web tests/typecheck/build and mobile typecheck.
- [ ] Integration tests pass against clean MySQL migrations; Redis/Kafka tests run when those integrations are enabled.
- [ ] Dependency and container vulnerability reports are reviewed.
- [ ] Tenant isolation, authorization and payment-webhook replay scenarios pass.
- [ ] New database migrations are reviewed for backward-compatible rollout and recovery.
- [ ] Required secrets and feature flags are verified in the target environment.
- [ ] Backups and restore procedure are current and recently tested.
- [ ] Rollback version and responsible operator are known.

## Rollout and rollback

Use a staged rollout. Apply backward-compatible schema changes before deploying code that requires them. Avoid destructive column/table changes in the same release that first stops using them. Keep the previous application artifact available until smoke tests pass. If a migration cannot be rolled back safely, use a forward-fix migration and restore data only under an approved recovery procedure.

Smoke tests should include health checks, tenant hostname resolution, authorized/unauthorized requests, menu reads, cart/checkout idempotency, payment webhook verification in provider test mode, and outbox dispatch. Do not send real payments during deployment smoke tests.

## Known blockers before production

The project still needs customer-facing web/mobile workflows, inventory consistency, refund/reconciliation and notification delivery, complete consumer idempotency/dead-letter re-drive, login throttling/account recovery/key rotation, end-to-end and load tests, production images/manifests, secret-manager integration, verified TLS/DNS, and operational monitoring/runbooks. Do not mark a release ready until the implementation tracker and test evidence show these gates passing.
