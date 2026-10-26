package com.mealminutes.platform.outbox;

import com.mealminutes.platform.tenant.domain.ProvisioningStatus;
import com.mealminutes.platform.tenant.domain.TenantContext;
import com.mealminutes.platform.tenant.domain.TenantLifecycleStatus;
import com.mealminutes.platform.tenant.domain.TenantRegistryEntry;
import com.mealminutes.platform.tenant.persistence.TenantRegistryRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"unchecked", "rawtypes"})
class TenantOutboxDispatcherTests {
    private final TenantRegistryRepository tenants = mock(TenantRegistryRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void publishesPendingEventWithTenantAndIdempotencyHeadersThenMarksItPublished() {
        when(tenants.findAll()).thenReturn(List.of(activeTenant()));
        when(jdbc.query(contains("FROM outbox_event"), any(RowMapper.class)))
                .thenReturn(List.of(event(0, "correlation-1")), List.of());
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        var dispatcher = dispatcher(10, 3);

        dispatcher.dispatchPendingEvents();

        var captor = org.mockito.ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(captor.capture());
        ProducerRecord<String, String> record = captor.getValue();
        assertThat(record.topic()).isEqualTo("mealminutes.events");
        assertThat(record.key()).isEqualTo("order-1");
        assertThat(header(record, "event_id")).isEqualTo("event-1");
        assertThat(header(record, "tenant_id")).isEqualTo("tenant-1");
        assertThat(header(record, "event_type")).isEqualTo("order.created");
        assertThat(header(record, "correlation_id")).isEqualTo("correlation-1");
        verify(jdbc).update(contains("SET published_at = CURRENT_TIMESTAMP(6)"), any(Object[].class));
    }

    @Test
    void schedulesRetryWhenKafkaPublishFails() {
        when(tenants.findAll()).thenReturn(List.of(activeTenant()));
        when(jdbc.query(contains("FROM outbox_event"), any(RowMapper.class)))
                .thenReturn(List.of(event(0, null)), List.of());
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
        var dispatcher = dispatcher(10, 3);

        dispatcher.dispatchPendingEvents();

        verify(jdbc).update(contains("SET attempt_count = ?, last_error = ?, next_attempt_at = ?, dead_lettered_at = ?"),
                any(Object[].class));
        verify(jdbc, never()).update(contains("SET published_at = CURRENT_TIMESTAMP(6)"), any(Object[].class));
    }

    @Test
    void movesRepeatedlyFailingEventToDeadLetterStateAtAttemptLimit() {
        when(tenants.findAll()).thenReturn(List.of(activeTenant()));
        when(jdbc.query(contains("FROM outbox_event"), any(RowMapper.class)))
                .thenReturn(List.of(event(2, null)), List.of());
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
        var dispatcher = dispatcher(10, 3);

        dispatcher.dispatchPendingEvents();

        verify(jdbc).update(contains("dead_lettered_at = ?"), any(Object[].class));
    }

    @Test
    void ignoresTenantsThatAreNotActiveAndProvisioned() {
        when(tenants.findAll()).thenReturn(List.of(new TenantRegistryEntry("pending-tenant")));
        var dispatcher = dispatcher(10, 3);

        dispatcher.dispatchPendingEvents();

        verifyNoInteractions(jdbc, kafka);
    }

    @Test
    void skipsActiveTenantsUntilOutboxRetryMigrationIsApplied() {
        when(tenants.findAll()).thenReturn(List.of(activeTenant(8)));
        var dispatcher = dispatcher(10, 3);

        dispatcher.dispatchPendingEvents();

        verifyNoInteractions(jdbc, kafka);
    }

    @Test
    void stopsPollingWhenThereAreNoPendingEvents() {
        when(tenants.findAll()).thenReturn(List.of(activeTenant()));
        when(jdbc.query(contains("FROM outbox_event"), any(RowMapper.class))).thenReturn(List.of());
        var dispatcher = dispatcher(10, 3);

        dispatcher.dispatchPendingEvents();

        verifyNoInteractions(kafka);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    private TenantOutboxDispatcher dispatcher(int batchSize, int maxAttempts) {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(mock(TransactionStatus.class));
        return new TenantOutboxDispatcher(tenants, jdbc, kafka, manager,
                "mealminutes.events", batchSize, maxAttempts, 2, 1000);
    }

    private static TenantRegistryEntry activeTenant() {
        return activeTenant(9);
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

    private static TenantOutboxDispatcher.OutboxEvent event(int attempts, String correlationId) {
        return new TenantOutboxDispatcher.OutboxEvent("event-1", "tenant-1", "CUSTOMER_ORDER",
                "order-1", "order.created", 1, correlationId, "{\"orderId\":\"order-1\"}", attempts);
    }

    private static String header(ProducerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), java.nio.charset.StandardCharsets.UTF_8);
    }
}
