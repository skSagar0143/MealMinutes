# Tenant isolation

Tenant isolation is a release-blocking security requirement.

## Required design

- Normalize the hostname and resolve it through the trusted platform registry.
- Reject unknown, suspended, or not-yet-provisioned tenants.
- Bind immutable tenant identity to request context only after registry validation.
- Authorize the authenticated user against tenant membership and permission for every protected operation.
- Never accept a client-provided tenant ID, database name, datasource URL or secret as authorization.
- Clear request context in finally/completion paths, including exceptions.
- Background jobs and Kafka consumers must carry explicit tenant IDs and establish isolated context for each operation.
- Include tenant identity in cache keys and enforce tenant-aware file-access rules.
- Keep tenant pools bounded and evict/dispose them safely.

## Mandatory tests

1. ABC tenant cannot read/update XYZ data.
2. Identical row IDs in distinct tenant databases cannot cross-route.
3. Unknown, suspended and unprovisioned tenants are rejected.
4. Forged tenant headers do not override hostname/registry resolution.
5. Exceptions leave no tenant context behind.
6. Concurrent tenant requests never mix datasource connections.
7. Cache, Kafka and file storage preserve tenant boundaries.

Do not mark these requirements as implemented until the corresponding code and automated tests exist and pass.
