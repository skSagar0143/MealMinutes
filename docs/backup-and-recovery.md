# Backup and recovery

## Current status

This document defines the recovery approach to implement and verify. It does not claim that production backups, point-in-time recovery or a disaster-recovery environment are currently configured.

## Data that must be protected

- Platform database: tenant registry, verified host mappings, tenant lifecycle/provisioning state, platform accounts, memberships and refresh sessions.
- Every tenant database: restaurant/branch/menu data, addresses, carts, orders, order history, payment attempts, webhook deduplication records, audit records and transactional outbox rows.
- External configuration: encrypted secret-manager references, RSA key identifiers/material in the approved key-management system, provider configuration and deployment configuration.
- Operational state: Kafka topic/ACL/schema configuration and any consumer idempotency/replay state once implemented.

Redis is a cache, not the source of truth for orders or payments. Kafka messages are not a substitute for backing up the MySQL business records and outbox. Never rely on a cache snapshot as the only recovery copy of business data.

## Backup policy to configure

Before production, define and approve:

- Recovery Point Objective (RPO) and Recovery Time Objective (RTO).
- Automated full backups and, where supported, binary-log/point-in-time recovery for MySQL.
- Separate coverage for the platform database and **every** tenant database.
- Encryption in transit and at rest, least-privilege backup identities, retention periods and access auditing.
- Off-host or cross-region copies appropriate to the business risk.
- Alerts for missed/failed backups and exhausted storage.
- A documented retention/deletion process that respects legal and contractual obligations.

Do not put backup files, real credentials or unencrypted database dumps into this repository.

## Restore drill

Perform a restore drill in an isolated non-production environment:

1. Select a dated backup and record its checksum and source database identity.
2. Restore the platform database to an isolated MySQL instance.
3. Restore one or more tenant databases independently, preserving their tenant-to-database mapping.
4. Apply only the documented migration/recovery steps needed to bring schemas to the target compatible version.
5. Verify tenant registry references, schema versions, foreign keys, unique constraints and critical order/payment records.
6. Confirm that tenant A and tenant B still route to separate databases and cannot read one another's records.
7. Validate payment webhook deduplication and outbox state so recovery does not silently duplicate irreversible external actions.
8. Verify the application starts with restored data and health probes behave as expected.
9. Record elapsed recovery time, data-loss window, errors and corrective actions.

Never restore a tenant database over another tenant's database because names or local IDs look similar. Validate the immutable tenant ID and database mapping before restore.

## Kafka and outbox recovery

The transactional outbox in MySQL is the recoverable record for business events awaiting publication. Kafka delivery is at least once: a process can fail after the broker accepts an event but before the database marks the outbox row as published. Consumers must deduplicate by event ID and make side effects idempotent.

The dispatcher currently has retry/backoff and database dead-letter state. A supported dead-letter re-drive interface and full consumer replay workflow remain pending. Until those are implemented and tested, use a reviewed, audited manual recovery procedure rather than editing production rows casually.

## Redis recovery

Menu cache entries are rebuildable from tenant database reads. Cache loss should cause cache misses and database fallback, not loss of business records. Cache invalidation and outage behavior must be verified for each tenant before enabling the cache in production. Do not restore cached authorization or tenant-routing values as authoritative state.

## Incident checklist

- Stop unsafe automated retries if they may repeat external payments or notifications.
- Preserve logs, correlation IDs, event IDs and provider references without exposing secrets or unnecessary personal data.
- Identify affected tenant IDs and verify the exact database mapping.
- Restore into an isolated environment first whenever practical.
- Reconcile payment-provider state before changing payment/order status.
- Record all manual changes in an audit trail.
- Validate isolation and critical business invariants before reopening traffic.
- Complete a post-incident review and update recovery tests/runbooks.
