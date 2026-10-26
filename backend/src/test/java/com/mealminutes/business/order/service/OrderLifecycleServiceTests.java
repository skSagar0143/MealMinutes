package com.mealminutes.business.order.service;

import com.mealminutes.platform.tenant.domain.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"unchecked", "rawtypes"})
class OrderLifecycleServiceTests {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final OrderLifecycleService service = new OrderLifecycleService(jdbc, new ObjectMapper());

    @BeforeEach
    void setup() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void advancesPaidPickupOrderAndWritesHistoryOutboxAndAudit() {
        when(jdbc.query(contains("FROM customer_order WHERE order_id = ? FOR UPDATE"),
                any(RowMapper.class), eq("order-1")))
                .thenReturn(List.of(snapshot("CONFIRMED", "SUCCEEDED", "PICKUP")));

        try (TenantContext.Scope ignored = TenantContext.bind(tenant())) {
            var response = service.transitionRestaurantOrder("staff-1", "order-1", "ACCEPTED", "Accepted by kitchen");
            assertThat(response.status()).isEqualTo("ACCEPTED");
        }

        verify(jdbc).update(contains("UPDATE customer_order SET order_status = ?"), any(Object[].class));
        verify(jdbc).update(contains("INSERT INTO customer_order_status_history"), any(Object[].class));
        verify(jdbc).update(contains("INSERT INTO outbox_event"), any(Object[].class));
        verify(jdbc).update(contains("INSERT INTO audit_event"), any(Object[].class));
    }

    @Test
    void rejectsSkippingRestaurantOrderStates() {
        when(jdbc.query(contains("FROM customer_order WHERE order_id = ? FOR UPDATE"),
                any(RowMapper.class), eq("order-1")))
                .thenReturn(List.of(snapshot("ACCEPTED", "SUCCEEDED", "PICKUP")));

        try (TenantContext.Scope ignored = TenantContext.bind(tenant())) {
            assertThatThrownBy(() -> service.transitionRestaurantOrder("staff-1", "order-1",
                    "READY_FOR_PICKUP", null))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.CONFLICT);
        }

        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void cancelsUnpaidOrderOnlyWhenNoPaymentAttemptExists() {
        when(jdbc.query(contains("AND customer_user_id = ? FOR UPDATE"), any(RowMapper.class),
                eq("order-1"), eq("customer-1")))
                .thenReturn(List.of(snapshot("PAYMENT_PENDING", "PENDING", "PICKUP")));
        when(jdbc.query(contains("SELECT payment_id FROM payment_transaction"), any(RowMapper.class),
                eq("order-1"))).thenReturn(List.of());

        try (TenantContext.Scope ignored = TenantContext.bind(tenant())) {
            var response = service.cancelUnpaidCustomerOrder("customer-1", "order-1", "Changed my mind");
            assertThat(response.status()).isEqualTo("CANCELLED");
        }

        verify(jdbc).update(contains("SET order_status = 'CANCELLED'"), any(Object[].class));
        verify(jdbc).update(contains("INSERT INTO customer_order_status_history"), any(Object[].class));
        verify(jdbc).update(contains("INSERT INTO outbox_event"), any(Object[].class));
    }

    @Test
    void refusesCancellationWhenPaymentAttemptNeedsReconciliation() {
        when(jdbc.query(contains("AND customer_user_id = ? FOR UPDATE"), any(RowMapper.class),
                eq("order-1"), eq("customer-1")))
                .thenReturn(List.of(snapshot("PAYMENT_PENDING", "PENDING", "PICKUP")));
        when(jdbc.query(contains("SELECT payment_id FROM payment_transaction"), any(RowMapper.class),
                eq("order-1"))).thenReturn(List.of("payment-1"));

        try (TenantContext.Scope ignored = TenantContext.bind(tenant())) {
            assertThatThrownBy(() -> service.cancelUnpaidCustomerOrder("customer-1", "order-1", null))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.CONFLICT);
        }

        verify(jdbc, never()).update(contains("SET order_status = 'CANCELLED'"), any(Object[].class));
    }

    private static OrderLifecycleService.OrderSnapshot snapshot(String status, String paymentStatus,
                                                                 String fulfillmentType) {
        return new OrderLifecycleService.OrderSnapshot("order-1", "MM202610260001",
                "customer-1", "branch-1", status, paymentStatus, fulfillmentType, "INR",
                new BigDecimal("10.00"), new BigDecimal("1.00"), BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2), new BigDecimal("11.00"));
    }

    private static TenantContext.TenantDescriptor tenant() {
        return new TenantContext.TenantDescriptor("tenant-1", "abc-food", "tenant_abc_food", "secret://db");
    }
}
