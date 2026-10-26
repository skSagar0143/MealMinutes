package com.mealminutes.platform.outbox;

import com.mealminutes.platform.tenant.domain.ProvisioningStatus;
import com.mealminutes.platform.tenant.domain.TenantContext;
import com.mealminutes.platform.tenant.domain.TenantLifecycleStatus;
import com.mealminutes.platform.tenant.domain.TenantRegistryEntry;
import com.mealminutes.platform.tenant.persistence.TenantRegistryRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"unchecked", "rawtypes"})
class TenantEventInboxListenerTests {
    private final TenantRegistryRepository tenants = mock(TenantRegistryRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);

    private TenantEventInboxListener listener() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(mock(TransactionStatus.class));
        return new TenantEventInboxListener(tenants, jdbc, transactionManager);
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void persistsValidEventInTenantScopedInbox() {
        when(tenants.findById("tenant-1")).thenReturn(Optional.of(activeTenant(10)));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        listener().receive(eventRecord());

        verify(jdbc).update(contains("INSERT IGNORE INTO inbox_event"),
                eq("event-1"), eq("tenant-1"), eq("ORDER"), eq("order-1"),
                eq("order.created"), eq(1), eq("corr-1"), eq("{\"orderId\":\"order-1\"}"));
        assertThat(TenantContext.currentOrNull()).isNull();
    }

    @Test
    void acceptsRedeliveryThroughIdempotentInsertRatherThanRepeatingSideEffects() {
        when(tenants.findById("tenant-1")).thenReturn(Optional.of(activeTenant(10)));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1, 0);
        TenantEventInboxListener listener = listener();

        listener.receive(eventRecord());
        listener.receive(eventRecord());

        verify(jdbc, times(2)).update(contains("INSERT IGNORE INTO inbox_event"), any(Object[].class));
    }

    @Test
    void rejectsRecordMissingRequiredMetadataBeforeDatabaseAccess() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("mealminutes.events", 0, 0L,
                "order-1", "{\"orderId\":\"order-1\"}");
        record.headers().add(new RecordHeader("event_id", bytes("event-1")));

        assertThatThrownBy(() -> listener().receive(record))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenant_id");
        verifyNoInteractions(tenants, jdbc);
    }

    @Test
    void rejectsUnknownTenant() {
        when(tenants.findById("tenant-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> listener().receive(eventRecord()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown tenant");
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsTenantWithoutInboxMigration() {
        when(tenants.findById("tenant-1")).thenReturn(Optional.of(activeTenant(9)));

        assertThatThrownBy(() -> listener().receive(eventRecord()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inbox migration");
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsInvalidSchemaVersionHeader() {
        ConsumerRecord<String, String> record = eventRecord();
        record.headers().remove("schema_version");
        record.headers().add(new RecordHeader("schema_version", bytes("not-a-number")));

        assertThatThrownBy(() -> listener().receive(record))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive integer");
        verifyNoInteractions(tenants, jdbc);
    }

    private static TenantRegistryEntry activeTenant(int schemaVersion) {
        TenantRegistryEntry tenant = new TenantRegistryEntry("tenant-a");
        tenant.transitionTo(TenantLifecycleStatus.PENDING_VERIFICATION);
        tenant.transitionTo(TenantLifecycleStatus.APPROVED);
        tenant.beginProvisioning();
        tenant.completeProvisioning("tenant_a_db", "secret://tenant-a", schemaVersion);
        tenant.transitionTo(TenantLifecycleStatus.ACTIVE);
        return tenant;
    }

    private static ConsumerRecord<String, String> eventRecord() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("mealminutes.events", 0, 0L,
                "order-1", "{\"orderId\":\"order-1\"}");
        record.headers().add(new RecordHeader("event_id", bytes("event-1")));
        record.headers().add(new RecordHeader("tenant_id", bytes("tenant-1")));
        record.headers().add(new RecordHeader("aggregate_type", bytes("ORDER")));
        record.headers().add(new RecordHeader("event_type", bytes("order.created")));
        record.headers().add(new RecordHeader("schema_version", bytes("1")));
        record.headers().add(new RecordHeader("correlation_id", bytes("corr-1")));
        return record;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
