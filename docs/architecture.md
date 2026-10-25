# Architecture

## Initial decision

Start with a modular monolith. Keep business capabilities separated by package/module boundaries and expose versioned REST APIs. Do not split into microservices until operational scale, deployment independence or team ownership justifies the added distributed-systems complexity.

## Planned modules

- Platform tenant registry and onboarding
- Identity, authentication and authorization
- Restaurant profiles and branches
- Menu/catalog and availability
- Customer profile and addresses
- Cart, checkout and order lifecycle
- Inventory
- Payment adapter, webhooks and refunds
- Delivery assignment and tracking abstraction
- Promotions and coupons
- Notifications
- Reviews and ratings
- Reporting, audit and observability

## Persistence boundaries

A platform database owns tenant registry, immutable tenant IDs, verified host mappings, lifecycle/provisioning state, subscription metadata, migration status, and secret references. It must not store tenant database passwords in plaintext.

Each tenant has a separately provisioned MySQL database for restaurant-specific business records. Tenant datasource creation must be bounded; pools need limits, timeouts, lifecycle/eviction and safe failure behavior. Flyway migrations run per database and their progress is tracked.

Shared customer identity, cross-tenant search, delivery coordination and platform-level reporting require explicit data ownership decisions before implementation. Avoid silently copying sensitive records between tenants.

## Request flow

1. Normalize the request hostname and reject malformed/unknown hosts.
2. Resolve it through trusted platform registry data.
3. Verify tenant lifecycle and provisioning state.
4. Establish an immutable request-scoped tenant context.
5. Verify the authenticated principal has membership/permission for that tenant.
6. Route persistence to the correct tenant datasource.
7. Clear context in all completion/error paths.
8. Never use an untrusted tenant header, database name or datasource URL as authorization.

Background tasks and Kafka consumers must carry explicit tenant IDs and establish/clear context within their own execution scope.

## Eventing and cache

Redis is a cache/coordination aid, never the durable order store. Tenant-specific cache keys include immutable tenant IDs; TTL and invalidation are explicit. Kafka events use stable event IDs, schema versions, tenant and aggregate IDs, correlation IDs and timestamps. Business transactions that require reliable event publication use a transactional outbox. Consumers are idempotent and support retry/DLT/replay.

## ADRs

Architecture decisions will be recorded in docs/architecture-decisions/ as implementation progresses.
