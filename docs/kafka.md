# Kafka eventing and recovery

## Current implementation boundary

The backend currently implements a **feature-flagged tenant-aware transactional outbox dispatcher**. It reads committed event rows from each eligible tenant database and publishes them to the configured Kafka topic. A consumer-side idempotency ledger, business-event consumers, schema compatibility enforcement, dead-letter re-drive UI and operational dashboards are still pending. Do not treat producer publication as end-to-end event processing.

## Local broker

Start the local services from the repository root:

```powershell
docker compose up -d mysql kafka
docker compose ps
```

The Compose broker uses the Apache Kafka KRaft image and publishes port 9092 to the host. The default local bootstrap address is `localhost:9092`. Verify the broker health before enabling the outbox dispatcher.

## Enable the dispatcher

The dispatcher is disabled by default. It requires tenant provisioning and tenant datasource credentials in addition to Kafka:

```powershell
$env:KAFKA_BOOTSTRAP_SERVERS = "localhost:9092"
$env:MEALMINUTES_TENANT_PROVISIONING_ENABLED = "true"
$env:MEALMINUTES_OUTBOX_ENABLED = "true"
```

Configure the platform datasource, tenant database admin credentials and approved tenant secret-store configuration as described in [development setup](development-setup.md). Do not use the example credentials outside local development.

Relevant properties:

| Property | Default | Purpose |
|---|---|---|
| `mealminutes.outbox.enabled` | `false` | Enables the scheduled dispatcher |
| `mealminutes.outbox.topic` | `mealminutes.events` | Kafka destination topic |
| `mealminutes.outbox.poll-interval-ms` | `1000` | Delay between poll cycles |
| `mealminutes.outbox.batch-size` | `10` | Maximum events processed per tenant per cycle, bounded to 100 |
| `mealminutes.outbox.max-attempts` | `12` | Attempts before database dead-letter state |
| `mealminutes.outbox.base-backoff-seconds` | `2` | Exponential retry base |
| `mealminutes.outbox.send-timeout-ms` | `5000` | Broker acknowledgement timeout |

## Event contract

Each record is keyed by aggregate ID and carries headers:

- `event_id`: stable unique event identifier.
- `tenant_id`: immutable tenant identifier.
- `aggregate_type`: aggregate family.
- `event_type`: business event name.
- `schema_version`: event schema version.
- `correlation_id`: request correlation ID when available.

The JSON payload is written in the same tenant database transaction as the business state change. The dispatcher polls only active, successfully provisioned tenants whose recorded schema version supports the outbox. It claims one row using `FOR UPDATE SKIP LOCKED`, waits for Kafka acknowledgement, then marks the row published.

Do not put passwords, private keys, raw payment credentials or unnecessary personal data into event payloads.

## Delivery semantics

Delivery is **at least once**, not exactly once end to end. If Kafka accepts a record but the application fails before committing `published_at`, the event can be published again. Future consumers must deduplicate by `event_id` in a durable store and apply business side effects idempotently. Consumer offsets alone do not make an external side effect exactly once.

The dispatcher records attempts and bounded error messages. Failures use exponential backoff; after the configured attempt limit the outbox row is marked dead-lettered in MySQL. This is database dead-letter state, not yet a separate Kafka dead-letter topic. Re-drive tooling is not implemented; use a reviewed, audited recovery process until that tooling exists.

## Verification checklist

- [x] Business transaction writes an outbox row.
- [x] Dispatcher adds event metadata headers and sends to the configured topic.
- [x] Outbox row is marked published only after broker acknowledgement.
- [x] Send failures schedule bounded exponential retry and eventually enter database dead-letter state.
- [ ] Consumer validates required headers and supported schema versions.
- [ ] Consumer uses a tenant-scoped processed-event ledger for deduplication.
- [ ] Consumer crash/rebalance/replay scenarios are tested against Kafka.
- [ ] Dead-letter re-drive is audited and does not repeat irreversible side effects.
- [ ] Consumer lag, retry volume and dead-letter counts have operational metrics/alerts.

See [implementation plan](implementation-plan.md) for the current release gates.
