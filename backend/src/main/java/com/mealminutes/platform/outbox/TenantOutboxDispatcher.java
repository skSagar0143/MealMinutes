package com.mealminutes.platform.outbox;

import com.mealminutes.platform.tenant.domain.ProvisioningStatus;
import com.mealminutes.platform.tenant.domain.TenantContext;
import com.mealminutes.platform.tenant.domain.TenantLifecycleStatus;
import com.mealminutes.platform.tenant.domain.TenantRegistryEntry;
import com.mealminutes.platform.tenant.persistence.TenantRegistryRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@EnableScheduling
@ConditionalOnExpression("${mealminutes.tenant-provisioning.enabled:false} and ${mealminutes.outbox.enabled:false}")
public class TenantOutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(TenantOutboxDispatcher.class);
    private static final String EVENT_ID_HEADER = "event_id";
    private static final String TENANT_ID_HEADER = "tenant_id";
    private static final String AGGREGATE_TYPE_HEADER = "aggregate_type";
    private static final String EVENT_TYPE_HEADER = "event_type";
    private static final String SCHEMA_VERSION_HEADER = "schema_version";
    private static final String CORRELATION_ID_HEADER = "correlation_id";

    private final TenantRegistryRepository tenants;
    private final JdbcTemplate tenantJdbc;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate tenantTransaction;
    private final String topic;
    private final int batchSize;
    private final int maxAttempts;
    private final long baseBackoffSeconds;
    private final long sendTimeoutMs;

    public TenantOutboxDispatcher(TenantRegistryRepository tenants,
                                  @Qualifier("tenantBusinessJdbcTemplate") JdbcTemplate tenantJdbc,
                                  KafkaTemplate<String, String> kafka,
                                  @Qualifier("tenantTransactionManager") PlatformTransactionManager transactionManager,
                                  @Value("${mealminutes.outbox.topic:mealminutes.events}") String topic,
                                  @Value("${mealminutes.outbox.batch-size:10}") int batchSize,
                                  @Value("${mealminutes.outbox.max-attempts:12}") int maxAttempts,
                                  @Value("${mealminutes.outbox.base-backoff-seconds:2}") long baseBackoffSeconds,
                                  @Value("${mealminutes.outbox.send-timeout-ms:5000}") long sendTimeoutMs) {
        if (batchSize < 1 || batchSize > 100) throw new IllegalArgumentException("outbox batch size must be between 1 and 100");
        if (maxAttempts < 1) throw new IllegalArgumentException("outbox max attempts must be positive");
        if (baseBackoffSeconds < 1 || sendTimeoutMs < 1) throw new IllegalArgumentException("outbox timeouts must be positive");
        this.tenants = tenants;
        this.tenantJdbc = tenantJdbc;
        this.kafka = kafka;
        this.topic = topic;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.baseBackoffSeconds = baseBackoffSeconds;
        this.sendTimeoutMs = sendTimeoutMs;
        this.tenantTransaction = new TransactionTemplate(transactionManager);
        this.tenantTransaction.setTimeout((int) Math.max(5, Math.min(30, (sendTimeoutMs / 1000) + 5)));
    }

    @Scheduled(fixedDelayString = "${mealminutes.outbox.poll-interval-ms:1000}")
    public void dispatchPendingEvents() {
        List<TenantRegistryEntry> eligible = tenants.findAll().stream()
                .filter(tenant -> tenant.getLifecycleStatus() == TenantLifecycleStatus.ACTIVE)
                .filter(tenant -> tenant.getProvisioningStatus() == ProvisioningStatus.SUCCEEDED)
                .filter(tenant -> tenant.getTenantDatabaseName() != null && tenant.getDatabaseSecretReference() != null)
                .toList();
        for (TenantRegistryEntry tenant : eligible) {
            try (TenantContext.Scope ignored = TenantContext.bind(new TenantContext.TenantDescriptor(
                    tenant.getTenantId(), tenant.getSlug(), tenant.getTenantDatabaseName(),
                    tenant.getDatabaseSecretReference()))) {
                for (int i = 0; i < batchSize; i++) {
                    Boolean worked = tenantTransaction.execute(status -> dispatchOne());
                    if (!Boolean.TRUE.equals(worked)) break;
                }
            } catch (RuntimeException failure) {
                log.warn("Outbox polling failed for tenant {}", tenant.getTenantId(), failure);
            } finally {
                TenantContext.clear();
            }
        }
    }

    private boolean dispatchOne() {
        List<OutboxEvent> pending = tenantJdbc.query("""
                SELECT event_id, tenant_id, aggregate_type, aggregate_id, event_type, schema_version,
                       correlation_id, payload, attempt_count
                FROM outbox_event
                WHERE published_at IS NULL
                  AND dead_lettered_at IS NULL
                  AND (next_attempt_at IS NULL OR next_attempt_at <= CURRENT_TIMESTAMP(6))
                ORDER BY created_at ASC, event_id ASC
                LIMIT 1
                FOR UPDATE SKIP LOCKED
                """, (rs, row) -> new OutboxEvent(rs.getString("event_id"), rs.getString("tenant_id"),
                rs.getString("aggregate_type"), rs.getString("aggregate_id"), rs.getString("event_type"),
                rs.getInt("schema_version"), rs.getString("correlation_id"), rs.getString("payload"),
                rs.getInt("attempt_count")));
        if (pending.isEmpty()) return false;

        OutboxEvent event = pending.getFirst();
        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, event.aggregateId(), event.payload());
            addHeader(record, EVENT_ID_HEADER, event.eventId());
            addHeader(record, TENANT_ID_HEADER, event.tenantId());
            addHeader(record, AGGREGATE_TYPE_HEADER, event.aggregateType());
            addHeader(record, EVENT_TYPE_HEADER, event.eventType());
            addHeader(record, SCHEMA_VERSION_HEADER, Integer.toString(event.schemaVersion()));
            if (event.correlationId() != null) addHeader(record, CORRELATION_ID_HEADER, event.correlationId());
            kafka.send(record).get(sendTimeoutMs, TimeUnit.MILLISECONDS);
            int updated = tenantJdbc.update("""
                    UPDATE outbox_event
                    SET published_at = CURRENT_TIMESTAMP(6), attempt_count = attempt_count + 1,
                        last_error = NULL, next_attempt_at = NULL
                    WHERE event_id = ? AND published_at IS NULL
                    """, event.eventId());
            if (updated != 1) throw new IllegalStateException("Outbox event state changed during publish");
            log.debug("Published outbox event {} for tenant {} to topic {}", event.eventId(), event.tenantId(), topic);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            recordFailure(event, interrupted);
        } catch (Exception failure) {
            recordFailure(event, failure);
        }
        return true;
    }

    private void recordFailure(OutboxEvent event, Exception failure) {
        int attempts = event.attemptCount() + 1;
        String message = failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage());
        if (message.length() > 1000) message = message.substring(0, 1000);
        boolean deadLetter = attempts >= maxAttempts;
        long multiplier = 1L << Math.min(Math.max(0, event.attemptCount()), 12);
        long delaySeconds = Math.min(3600, baseBackoffSeconds * multiplier);
        Timestamp nextAttempt = deadLetter ? null : Timestamp.from(Instant.now().plusSeconds(delaySeconds));
        Timestamp deadLetteredAt = deadLetter ? Timestamp.from(Instant.now()) : null;
        tenantJdbc.update("""
                UPDATE outbox_event
                SET attempt_count = ?, last_error = ?, next_attempt_at = ?, dead_lettered_at = ?
                WHERE event_id = ? AND published_at IS NULL
                """, attempts, message, nextAttempt, deadLetteredAt, event.eventId());
        if (deadLetter) {
            log.error("Outbox event {} for tenant {} entered dead-letter state after {} attempts",
                    event.eventId(), event.tenantId(), attempts, failure);
        } else {
            log.warn("Outbox event {} for tenant {} failed; retry scheduled in {} seconds",
                    event.eventId(), event.tenantId(), delaySeconds, failure);
        }
    }

    private static void addHeader(ProducerRecord<String, String> record, String key, String value) {
        record.headers().add(key, value.getBytes(StandardCharsets.UTF_8));
    }

    record OutboxEvent(String eventId, String tenantId, String aggregateType, String aggregateId,
                       String eventType, int schemaVersion, String correlationId, String payload,
                       int attemptCount) { }
}
