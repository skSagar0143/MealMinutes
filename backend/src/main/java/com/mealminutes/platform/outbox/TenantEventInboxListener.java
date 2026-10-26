package com.mealminutes.platform.outbox;

import com.mealminutes.platform.tenant.domain.ProvisioningStatus;
import com.mealminutes.platform.tenant.domain.TenantContext;
import com.mealminutes.platform.tenant.domain.TenantLifecycleStatus;
import com.mealminutes.platform.tenant.domain.TenantRegistryEntry;
import com.mealminutes.platform.tenant.persistence.TenantRegistryRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Durably receives tenant events into a tenant-local inbox. The insert is
 * idempotent by event ID, so a crash after DB commit but before Kafka offset
 * commit safely tolerates redelivery. Business side effects are intentionally
 * handled by a separate inbox processor.
 */
@Component
@ConditionalOnExpression("${mealminutes.tenant-provisioning.enabled:false} and ${mealminutes.consumer.enabled:false}")
public class TenantEventInboxListener {
    private static final Logger log = LoggerFactory.getLogger(TenantEventInboxListener.class);
    private static final int REQUIRED_INBOX_SCHEMA_VERSION = 10;

    private final TenantRegistryRepository tenants;
    private final JdbcTemplate tenantJdbc;
    private final TransactionTemplate tenantTransaction;

    public TenantEventInboxListener(TenantRegistryRepository tenants,
                                    @Qualifier("tenantBusinessJdbcTemplate") JdbcTemplate tenantJdbc,
                                    @Qualifier("tenantTransactionManager") PlatformTransactionManager transactionManager) {
        this.tenants = tenants;
        this.tenantJdbc = tenantJdbc;
        this.tenantTransaction = new TransactionTemplate(transactionManager);
    }

    @KafkaListener(
            topics = "${mealminutes.consumer.topic:mealminutes.events}",
            groupId = "${mealminutes.consumer.group-id:mealminutes-inbox}")
    public void receive(ConsumerRecord<String, String> record) {
        if (record == null) throw new IllegalArgumentException("Kafka record is required");
        EventEnvelope event = EventEnvelope.from(record);
        TenantRegistryEntry tenant = tenants.findById(event.tenantId())
                .orElseThrow(() -> new IllegalStateException("Event references an unknown tenant"));
        if (tenant.getLifecycleStatus() != TenantLifecycleStatus.ACTIVE
                || tenant.getProvisioningStatus() != ProvisioningStatus.SUCCEEDED
                || tenant.getTenantDatabaseName() == null
                || tenant.getDatabaseSecretReference() == null) {
            throw new IllegalStateException("Event tenant is not active and provisioned");
        }
        if (tenant.getSchemaVersion() == null || tenant.getSchemaVersion() < REQUIRED_INBOX_SCHEMA_VERSION) {
            throw new IllegalStateException("Tenant inbox migration is not applied");
        }

        try (TenantContext.Scope ignored = TenantContext.bind(new TenantContext.TenantDescriptor(
                tenant.getTenantId(), tenant.getSlug(), tenant.getTenantDatabaseName(),
                tenant.getDatabaseSecretReference()))) {
            tenantTransaction.executeWithoutResult(status -> tenantJdbc.update("""
                    INSERT IGNORE INTO inbox_event
                        (event_id, tenant_id, aggregate_type, aggregate_id, event_type,
                         schema_version, correlation_id, payload)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, event.eventId(), event.tenantId(), event.aggregateType(), event.aggregateId(),
                    event.eventType(), event.schemaVersion(), event.correlationId(), record.value()));
            log.debug("Received event {} for tenant {} into durable inbox", event.eventId(), event.tenantId());
        } finally {
            TenantContext.clear();
        }
    }

    record EventEnvelope(String eventId, String tenantId, String aggregateType, String aggregateId,
                         String eventType, int schemaVersion, String correlationId) {
        static EventEnvelope from(ConsumerRecord<String, String> record) {
            String eventId = requiredHeader(record, "event_id");
            String tenantId = requiredHeader(record, "tenant_id");
            String aggregateType = requiredHeader(record, "aggregate_type");
            String eventType = requiredHeader(record, "event_type");
            String schema = requiredHeader(record, "schema_version");
            int schemaVersion;
            try {
                schemaVersion = Integer.parseInt(schema);
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException("schema_version header must be a positive integer", invalid);
            }
            if (schemaVersion < 1) throw new IllegalArgumentException("schema_version header must be positive");
            if (record.value() == null || record.value().isBlank()) {
                throw new IllegalArgumentException("Kafka event payload is required");
            }
            if (record.key() == null || record.key().isBlank()) {
                throw new IllegalArgumentException("Kafka aggregate key is required");
            }
            String correlationId = optionalHeader(record, "correlation_id");
            return new EventEnvelope(eventId, tenantId, aggregateType, record.key(), eventType,
                    schemaVersion, correlationId);
        }

        private static String requiredHeader(ConsumerRecord<String, String> record, String name) {
            String value = optionalHeader(record, name);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Missing required Kafka header: " + name);
            }
            return value;
        }

        private static String optionalHeader(ConsumerRecord<String, String> record, String name) {
            Header header = record.headers().lastHeader(name);
            if (header == null || header.value() == null) return null;
            return new String(header.value(), StandardCharsets.UTF_8);
        }
    }
}
