# Security design and current status

This document describes implemented controls and remaining release blockers. A passing unit/integration test suite does not by itself establish production readiness.

## Controls implemented

- Tenant hostname input is normalized with IDN-to-ASCII conversion, lowercase canonicalization, label/length checks, and rejection of ports, paths, user-info, wildcards, and malformed labels.
- Tenant resolution accepts only an exact hostname mapping whose domain status is `VERIFIED`. The tenant must be `ACTIVE`, provisioned successfully, and have a database name before a tenant context is created.
- Tenant context is scoped and cleared in a `finally` path; its scope also clears context when closed.
- Tenant business data uses a tenant-aware datasource and separate tenant transaction manager when tenant provisioning is enabled. A real MySQL integration test verifies that two tenant contexts route to separate databases.
- Hibernate schema auto-update is disabled; Flyway owns tenant schema changes. Tenant schema migrations are tested against MySQL in CI.
- Spring Security is stateless and fail-closed by default. Only explicitly allowed health/info endpoints are public in the default security configuration.
- Optional RS256 JWT signing/validation loads PKCS#8 private and X.509 public PEM keys from configured resource locations. Access tokens are short-lived.
- Passwords use BCrypt. Opaque refresh tokens are stored only as SHA-256 hashes; refresh rotation locks the session row and revokes the previous token. Refresh cookies are HttpOnly, SameSite=Strict, scoped to `/api/v1/auth`, and Secure by default.
- Login, refresh and logout require the configured exact Origin. A platform administrator can be bootstrapped from `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD`; credentials must be supplied through environment configuration and must never be logged.
- Tenant membership and role checks are enforced for restaurant menu, branch/profile, customer-address and restaurant-order operations. Customer address and order lookups are scoped to the authenticated user subject.
- Payment webhook signatures are verified against the raw request body; event IDs are deduplicated. Payment/order state changes and outbox records are persisted transactionally.
- Menu/profile/order lifecycle writes use tenant-scoped transactions and audit/outbox records where implemented. Public error responses do not include stack traces or internal exception details.

## Configuration and key custody

Authentication and payment routes remain disabled unless their feature flags and required tenant/security/order-pricing configuration are enabled. Configure RSA key locations, trusted origins, payment provider credentials and database credentials through deployment secret management. Never commit private keys, real credentials, provider secrets, or production database passwords.

The current local encrypted file secret store is not a substitute for a production secret manager. Key generation, custody, rotation and emergency revocation remain operator responsibilities.

## Important limitations

- Distributed login throttling, MFA, email verification, password reset and comprehensive account-compromise recovery are not implemented.
- RSA key rotation and a production secret manager are not configured.
- A real MySQL integration test verifies routing to separate tenant databases, but forged-host/header tests, concurrent cross-tenant writes, context-propagation tests and datasource-eviction race tests remain incomplete.
- Tenant hostname resolution is not authorization. A hostname or tenant context must never be treated as proof of user membership; business endpoints must independently verify the required tenant membership and role.
- Payment refund execution, provider reconciliation jobs, manual recovery tooling and live provider verification remain incomplete.
- TLS/reverse-proxy hardening, security headers/CSP review, CORS review, dependency/container scanning, load testing and external penetration testing have not been completed.

## Release blockers

1. Add distributed authentication throttling, email verification/password reset and key/session recovery controls.
2. Complete forged-host/header, tenant-membership, concurrent datasource lifecycle and cross-tenant write isolation tests.
3. Configure production TLS, trusted proxy headers, secrets, key rotation, security headers/CSP and dependency/container scanning.
4. Complete payment refunds/reconciliation and failure-injection tests before processing real orders.
5. Verify backups, restore procedures, monitoring, alerting, SLOs and incident response before launch.

Do not replace the fail-closed security baseline with `permitAll()` as a workaround. Do not expose tenant-backed routes publicly without the required authentication, tenant resolution and membership authorization.
