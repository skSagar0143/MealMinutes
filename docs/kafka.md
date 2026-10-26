# Kafka eventing and recovery

## Current implementation boundary

The backend implements a feature-flagged tenant-aware transactional outbox dispatcher and a feature-flagged Kafka inbox receiver. The receiver validates required metadata, resolves the tenant from the platform registry, routes to that tenant's database and stores each event in an `inbox_event` table using an idempotent insert. This is durable receipt, not yet execution of business side effects. Consumer-side business processing, explicit schema compatibility policy, dead-letter re-drive UI and operational dashboards remain pending.

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

## Enable the inbox receiver

The receiver is disabled by default and requires tenant provisioning, tenant datasource/secret configuration, and the V10 tenant migration. Failed listener deliveries use a bounded retry policy (initial delivery plus the configured retries); exhausted records are published to `<source-topic>.DLT` by default. Provision the DLT with at least as many partitions as the source topic and ensure the consumer principal can publish to it before enabling the receiver. Configure `MEALMINUTES_CONSUMER_RETRY_INTERVAL_MS`, `MEALMINUTES_CONSUMER_RETRY_ATTEMPTS` (0–10), and `MEALMINUTES_CONSUMER_DLT_SUFFIX` as needed. If DLT publication fails, the error is surfaced rather than treating recovery as successful. In a configured local environment:

```powershell
$env:MEALMINUTES_TENANT_PROVISIONING_ENABLED = "true"
$env:MEALMINUTES_CONSUMER_ENABLED = "true"
$env:MEALMINUTES_CONSUMER_TOPIC = "mealminutes.events"
$env:MEALMINUTES_CONSUMER_GROUP_ID = "mealminutes-inbox"
```

The listener commits the durable inbox write before returning, so the Kafka container can commit the offset afterwards. If the process crashes after the database commit but before the offset commit, Kafka may redeliver the record; `INSERT IGNORE` and the tenant-local primary key make that receipt idempotent. Invalid records or unavailable tenant databases throw rather than being acknowledged as successfully received.

## Delivery semantics

Delivery is **at least once**, not exactly once end to end. If Kafka accepts a record but the application fails before committing `published_at`, the event can be published again. The inbox receiver deduplicates durable receipt by `event_id` within the resolved tenant database. A future business processor must separately claim inbox rows, record processing attempts and make each side effect idempotent before marking an event processed. Consumer offsets alone do not make an external side effect exactly once.

The dispatcher records attempts and bounded error messages. Failures use exponential backoff; after the configured attempt limit the outbox row is marked dead-lettered in MySQL. The consumer now retries listener failures a bounded number of times and routes exhausted records to a topic named `<source-topic>.DLT` by default. Configure/create the DLT with at least as many partitions as the source topic before enabling the consumer; dead-letter publication failure is surfaced instead of silently acknowledged. DLT replay tooling and an audited operator workflow remain pending. Do not assume that publishing to a DLT alone repairs tenant data or performs business side effects.

## Verification checklist

- [x] Business transaction writes an outbox row.
- [x] Dispatcher adds event metadata headers and sends to the configured topic.
- [x] Outbox row is marked published only after broker acknowledgement.
- [x] Send failures schedule bounded exponential retry and eventually enter database dead-letter state.
- [x] Inbox receiver validates required metadata, positive schema version, active tenant and V10 schema readiness.
- [x] Inbox receiver uses a tenant-local primary key and `INSERT IGNORE` to tolerate duplicate delivery.
- [ ] Add an explicit supported-schema compatibility policy and real-broker tests proving invalid events reach the dead-letter topic after bounded retries.
- [ ] Implement inbox business processing with durable attempts, idempotent side effects and processed-event ledger.
- [ ] Consumer crash/rebalance/replay scenarios are tested against a real Kafka broker.
- [ ] Dead-letter re-drive is audited and does not repeat irreversible side effects.
- [ ] Consumer lag, retry volume and dead-letter counts have operational metrics/alerts.

See [implementation plan](implementation-plan.md) for the current release gates.
