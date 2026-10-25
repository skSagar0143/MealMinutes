# ADR 0002: Separate database for each restaurant tenant

- Status: Accepted as target architecture; implementation pending
- Date: 2026-10-26

## Context

Restaurant tenants require strong separation of business records and independent lifecycle/migration operations. A shared platform control plane must resolve tenant hostnames and manage provisioning without exposing tenant connection details to clients.

## Decision

Use a platform database for tenant registry, immutable IDs, verified host mappings, lifecycle/provisioning state and secret references. Store tenant business data in separate MySQL databases. Provisioning and datasource administration must be restricted, idempotent and observable. Database passwords are never stored as plaintext in registry rows.

Hostname resolution identifies the tenant but does not authorize the user. Each protected request must also verify authenticated tenant membership and permissions. Tenant datasource pools must be bounded and tenant context must be cleared on every completion path.

## Consequences and open implementation work

- Stronger isolation and simpler per-tenant restore/deletion boundaries, with higher operational and connection-pool management complexity.
- Decide ownership of customer identity, delivery coordination and cross-tenant reporting before implementing those modules.
- Add real multi-database integration tests, concurrent-request tests and context-leak tests before claiming the isolation gate passes.
