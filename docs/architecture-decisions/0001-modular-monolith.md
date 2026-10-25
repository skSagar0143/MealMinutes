# ADR 0001: Start with a modular monolith

- Status: Accepted
- Date: 2026-10-26

## Context

The product has several business domains, but early operation does not justify the deployment, networking, observability and consistency overhead of many independently deployed services. The repository started empty and needs a maintainable foundation before feature implementation.

## Decision

Start with one Spring Boot application, explicit domain/module boundaries, DTOs and domain services. Keep persistence and event publication boundaries explicit. Extract a module into a service only when deployment independence, scale or ownership provides a clear benefit.

## Consequences

- Local development and debugging are simpler for a small team.
- Module boundaries and architecture tests must prevent a monolith from becoming tightly coupled.
- Asynchronous Kafka workflows still require idempotency, outbox/retry/DLT design; a modular monolith does not make database and broker operations atomic.
