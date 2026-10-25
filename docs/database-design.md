# Database design

## Platform/control-plane database

The platform database owns tenant identity and routing metadata. The initial Flyway migration creates:

- `platform_tenant_registry`: immutable tenant ID, unique slug, lifecycle/provisioning state, tenant database name, secret reference, schema version, and timestamps.
- `platform_tenant_domain`: unique normalized hostname, tenant reference, verification status, primary flag, and creation time.

The registry intentionally stores a secret reference instead of a plaintext database password. Provisioning code must validate generated database names and must use a restricted database-administration credential, not accept SQL/database names from public input.

## Tenant databases — planned, not implemented yet

Restaurant operational data belongs in a separate database for each tenant:

- Restaurant and branch profiles, hours, delivery/pickup settings.
- Menu categories, items, variants/modifiers, branch availability, and pricing.
- Tenant staff and memberships.
- Inventory, orders, immutable order-time item/price/tax snapshots, status history, and tenant promotions/settings.
- Tenant-scoped operational/audit records.

Customer identity may be platform-wide to support a customer using multiple restaurants, but addresses, order history, and authorization relationships must have explicit tenant-aware policies. Payment provider IDs and transaction states must be minimized and access-controlled. Raw card numbers and CVVs must never be stored.

## Invariants

- IDs can repeat between separate tenant databases; every request must route to the database selected from the trusted registry.
- A hostname is a routing hint, not authorization.
- Never accept a client-supplied JDBC URL, database name, or secret reference as the target datasource.
- Use Flyway for schema evolution; keep `spring.jpa.hibernate.ddl-auto=validate`.
- Store money in fixed-precision decimal types, use UTC timestamps, and use database constraints for unique/idempotency invariants.
- Tenant provisioning, migration, retry, deprovisioning, and restore must be auditable and idempotent.

The current repository only has the platform registry/domain schema. Tenant database provisioning, tenant migrations, connection routing, and business schemas remain release blockers.
