# Security design and current status

## Current controls implemented

- Tenant hostname input is normalized with IDN-to-ASCII conversion, lowercase canonicalization, label/length checks, and rejection of ports, paths, user-info, wildcards, and malformed labels.
- Tenant resolution accepts only an exact hostname mapping whose domain status is `VERIFIED`.
- The resolved tenant must be `ACTIVE`, have successful provisioning status, and have a database name recorded before a tenant context is created.
- Tenant context is thread-local and removed in a `finally` path; the scope also removes it when closed.
- Provisioning metadata stores a secret-manager reference, not a plaintext database password.
- Hibernate schema auto-update is disabled; Flyway migrations are intended to own schema changes.
- Public error responses do not include stack traces or exception details.

## Important limitations — not production-ready

- Authentication, RS256 JWT validation, refresh-token rotation/revocation, password hashing, MFA, account throttling, and role/permission authorization are not implemented yet.
- Hostname resolution is **not authorization**. A hostname or tenant context must never be used as proof that a user belongs to that tenant. Membership and role checks must be implemented before exposing business APIs.
- A bounded, access-ordered tenant datasource pool registry now exists with validated database names, secret-reference credential resolution, eviction, and close semantics. It is not yet wired into a tenant-aware EntityManagerFactory/router; the current JPA datasource is still the platform database only. Do not query tenant business data through it.
- Domain ownership verification is not implemented. The `markVerified` domain model method is an internal state transition only and must never be exposed through an unauthenticated public endpoint.
- No production secret manager, TLS deployment, rate limiter, security scanner, or external penetration test is configured.

## Release blockers

1. Implement and test RSA/RS256 token signing/validation using external key material and key IDs.
2. Add tenant membership and permission checks independent of hostname.
3. Implement tenant datasource lifecycle management with bounded pools and explicit context propagation.
4. Add integration tests for forged host/header input, context cleanup, concurrency, and cross-tenant reads/writes.
5. Verify deployment TLS, trusted reverse-proxy configuration, secrets, backups, monitoring, and incident response before launch.

Never commit real credentials, private keys, provider secrets, or production database passwords.


## Current fail-closed baseline

Spring Security is now present. The current filter chain is stateless, explicitly permits only health and limited info endpoints, and denies all other requests. This is intentionally not a usable authentication implementation; do not expose the service as a working API until RS256 authentication, refresh-token rotation/revocation, and authorization are implemented and tested. Do not replace this with `permitAll()` as a workaround.
