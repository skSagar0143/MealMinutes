package com.mealminutes.business.order.service;

import com.mealminutes.business.order.api.OrderResponse;
import com.mealminutes.platform.tenant.domain.TenantContext;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@ConditionalOnExpression("${mealminutes.tenant-provisioning.enabled:false} and ${mealminutes.order-pricing.enabled:false}")
public class OrderLifecycleService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Autowired
    public OrderLifecycleService(
            @Qualifier("tenantBusinessJdbcTemplate") JdbcTemplate jdbc,
            ObjectMapper objectMapper) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public List<OrderResponse> listBranchOrders(String branchId, int page, int size) {
        if (branchId == null || branchId.isBlank() || branchId.length() > 36) {
            throw new IllegalArgumentException("branchId is required");
        }
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 100");
        }
        long offset = (long) page * size;
        return jdbc.query("""
                SELECT order_id, order_number, order_status, payment_status, fulfillment_type,
                       currency, subtotal, tax_total, delivery_fee, discount_total, grand_total
                FROM customer_order
                WHERE branch_id = ?
                ORDER BY created_at DESC, order_id DESC
                LIMIT ? OFFSET ?
                """, responseMapper(), branchId, size, offset);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public OrderResponse transitionRestaurantOrder(String actorUserId, String orderId,
                                                   String targetStatus, String note) {
        requireId(actorUserId, "actorUserId");
        requireId(orderId, "orderId");
        OrderStatus target = parseStatus(targetStatus);
        OrderSnapshot current = findForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        if (!"SUCCEEDED".equals(current.paymentStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Restaurant processing can begin only after successful payment");
        }
        if (!isAllowedTransition(current, target)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Invalid order status transition from " + current.orderStatus() + " to " + target.name());
        }

        int changed = jdbc.update("""
                UPDATE customer_order SET order_status = ?, updated_at = CURRENT_TIMESTAMP(6)
                WHERE order_id = ? AND order_status = ?
                """, target.name(), orderId, current.orderStatus());
        if (changed != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order status changed concurrently; reload and retry");
        }
        recordTransition(current, target.name(), actorUserId, normalizeNote(note));
        return toResponse(current.withStatus(target.name()));
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public OrderResponse cancelUnpaidCustomerOrder(String customerUserId, String orderId, String note) {
        requireId(customerUserId, "customerUserId");
        requireId(orderId, "orderId");
        OrderSnapshot current = findCustomerOrderForUpdate(orderId, customerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        if (!"PAYMENT_PENDING".equals(current.orderStatus())
                || !("PENDING".equals(current.paymentStatus()) || "FAILED".equals(current.paymentStatus()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only orders awaiting payment can be cancelled through this endpoint");
        }

        List<String> paymentAttempts = jdbc.query("""
                SELECT payment_id FROM payment_transaction WHERE order_id = ? FOR UPDATE
                """, (rs, row) -> rs.getString("payment_id"), orderId);
        if (!paymentAttempts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A payment attempt exists; reconcile its provider status before cancelling the order");
        }

        int changed = jdbc.update("""
                UPDATE customer_order SET order_status = 'CANCELLED', updated_at = CURRENT_TIMESTAMP(6)
                WHERE order_id = ? AND customer_user_id = ? AND order_status = 'PAYMENT_PENDING'
                """, orderId, customerUserId);
        if (changed != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order status changed concurrently; reload and retry");
        }
        recordTransition(current, "CANCELLED", customerUserId, normalizeNote(note));
        return toResponse(current.withStatus("CANCELLED"));
    }

    private java.util.Optional<OrderSnapshot> findForUpdate(String orderId) {
        return jdbc.query("""
                SELECT order_id, order_number, customer_user_id, branch_id, order_status, payment_status,
                       fulfillment_type, currency, subtotal, tax_total, delivery_fee, discount_total, grand_total
                FROM customer_order WHERE order_id = ? FOR UPDATE
                """, snapshotMapper(), orderId).stream().findFirst();
    }

    private java.util.Optional<OrderSnapshot> findCustomerOrderForUpdate(String orderId, String customerUserId) {
        return jdbc.query("""
                SELECT order_id, order_number, customer_user_id, branch_id, order_status, payment_status,
                       fulfillment_type, currency, subtotal, tax_total, delivery_fee, discount_total, grand_total
                FROM customer_order WHERE order_id = ? AND customer_user_id = ? FOR UPDATE
                """, snapshotMapper(), orderId, customerUserId).stream().findFirst();
    }

    private void recordTransition(OrderSnapshot current, String toStatus, String actorUserId, String note) {
        String tenantId = TenantContext.requireCurrent().tenantId();
        String correlationId = MDC.get("correlationId");
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("orderId", current.orderId());
        details.put("fromStatus", current.orderStatus());
        details.put("toStatus", toStatus);
        if (note != null) details.put("note", note);
        String json = writeJson(details);

        jdbc.update("""
                INSERT INTO customer_order_status_history(history_id, order_id, from_status, to_status,
                    actor_user_id, note, correlation_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID().toString(), current.orderId(), current.orderStatus(),
                toStatus, actorUserId, note, correlationId);
        jdbc.update("""
                INSERT INTO outbox_event(event_id, tenant_id, aggregate_type, aggregate_id, event_type,
                    schema_version, correlation_id, payload)
                VALUES (?, ?, 'CUSTOMER_ORDER', ?, 'order.status_changed', 1, ?, ?)
                """, UUID.randomUUID().toString(), tenantId, current.orderId(), correlationId, json);
        jdbc.update("""
                INSERT INTO audit_event(audit_id, actor_user_id, action, resource_type, resource_id,
                    correlation_id, details)
                VALUES (?, ?, 'ORDER_STATUS_CHANGED', 'CUSTOMER_ORDER', ?, ?, ?)
                """, UUID.randomUUID().toString(), actorUserId, current.orderId(), correlationId, json);
    }

    private static boolean isAllowedTransition(OrderSnapshot current, OrderStatus target) {
        return switch (OrderStatus.valueOf(current.orderStatus())) {
            case CONFIRMED -> target == OrderStatus.ACCEPTED;
            case ACCEPTED -> target == OrderStatus.PREPARING;
            case PREPARING -> target == ("PICKUP".equals(current.fulfillmentType())
                    ? OrderStatus.READY_FOR_PICKUP : OrderStatus.OUT_FOR_DELIVERY);
            case READY_FOR_PICKUP, OUT_FOR_DELIVERY -> target == OrderStatus.DELIVERED
                    && (("PICKUP".equals(current.fulfillmentType()) && OrderStatus.READY_FOR_PICKUP == OrderStatus.valueOf(current.orderStatus()))
                    || ("DELIVERY".equals(current.fulfillmentType()) && OrderStatus.OUT_FOR_DELIVERY == OrderStatus.valueOf(current.orderStatus())));
            default -> false;
        };
    }

    private static OrderStatus parseStatus(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("status is required");
        try {
            return OrderStatus.valueOf(value.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown order status");
        }
    }

    private static String normalizeNote(String note) {
        if (note == null || note.isBlank()) return null;
        String normalized = note.strip();
        if (normalized.length() > 500) throw new IllegalArgumentException("note must be at most 500 characters");
        return normalized;
    }

    private static void requireId(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 36) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize order lifecycle event", exception);
        }
    }

    private static OrderSnapshot toSnapshot(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new OrderSnapshot(rs.getString("order_id"), rs.getString("order_number"),
                rs.getString("customer_user_id"), rs.getString("branch_id"), rs.getString("order_status"),
                rs.getString("payment_status"), rs.getString("fulfillment_type"), rs.getString("currency"),
                rs.getBigDecimal("subtotal"), rs.getBigDecimal("tax_total"), rs.getBigDecimal("delivery_fee"),
                rs.getBigDecimal("discount_total"), rs.getBigDecimal("grand_total"));
    }

    private static RowMapper<OrderSnapshot> snapshotMapper() {
        return (rs, row) -> toSnapshot(rs);
    }

    private static RowMapper<OrderResponse> responseMapper() {
        return (rs, row) -> new OrderResponse(rs.getString("order_id"), rs.getString("order_number"),
                rs.getString("order_status"), rs.getString("payment_status"), rs.getString("fulfillment_type"),
                rs.getString("currency"), rs.getBigDecimal("subtotal"), rs.getBigDecimal("tax_total"),
                rs.getBigDecimal("delivery_fee"), rs.getBigDecimal("discount_total"), rs.getBigDecimal("grand_total"));
    }

    private static OrderResponse toResponse(OrderSnapshot snapshot) {
        return new OrderResponse(snapshot.orderId(), snapshot.orderNumber(), snapshot.orderStatus(),
                snapshot.paymentStatus(), snapshot.fulfillmentType(), snapshot.currency(), snapshot.subtotal(),
                snapshot.taxTotal(), snapshot.deliveryFee(), snapshot.discountTotal(), snapshot.grandTotal());
    }

    enum OrderStatus {
        CREATED, PAYMENT_PENDING, CONFIRMED, ACCEPTED, PREPARING, READY_FOR_PICKUP,
        OUT_FOR_DELIVERY, DELIVERED, CANCELLED, REFUND_PENDING, REFUNDED
    }

    record OrderSnapshot(String orderId, String orderNumber, String customerUserId, String branchId,
                         String orderStatus, String paymentStatus, String fulfillmentType, String currency,
                         java.math.BigDecimal subtotal, java.math.BigDecimal taxTotal,
                         java.math.BigDecimal deliveryFee, java.math.BigDecimal discountTotal,
                         java.math.BigDecimal grandTotal) {
        OrderSnapshot withStatus(String status) {
            return new OrderSnapshot(orderId, orderNumber, customerUserId, branchId, status, paymentStatus,
                    fulfillmentType, currency, subtotal, taxTotal, deliveryFee, discountTotal, grandTotal);
        }
    }
}
