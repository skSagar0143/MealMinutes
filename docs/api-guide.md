# API guide

## Current API surface

The application currently exposes Spring Boot Actuator health/info endpoints according to the configured exposure policy. The core restaurant, customer, ordering, payment, delivery, and administrative REST APIs are not implemented yet.

- Health: `GET /actuator/health`
- Readiness/liveness probes: Spring Boot Actuator health probe paths, subject to runtime configuration.
- Info: `GET /actuator/info` (environment details are disabled).

## Tenant-hosted requests

Non-platform paths currently pass through the tenant context filter. A request is resolved by its normalized server hostname against a verified platform domain mapping. Unknown, inactive, or unprovisioned tenants receive a generic 404 response.

This is only an early routing boundary. Do not treat it as authentication or tenant authorization. Business controllers must not be added until tenant membership/permission checks and tenant datasource routing are implemented.

## Error handling and API conventions to implement

Future `/api/v1` endpoints must use validated request DTOs, standardized Problem Details responses, pagination/filtering, correlation IDs, safe logging, and explicit idempotency behavior for checkout and payment operations. Never serialize JPA entities or datasource metadata directly.
