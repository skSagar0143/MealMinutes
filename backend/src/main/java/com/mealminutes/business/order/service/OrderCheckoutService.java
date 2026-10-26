package com.mealminutes.business.order.service;

import com.mealminutes.business.order.api.OrderCheckoutRequest;
import com.mealminutes.business.order.api.OrderResponse;
import com.mealminutes.platform.tenant.domain.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@ConditionalOnExpression("${mealminutes.tenant-provisioning.enabled:false} and ${mealminutes.order-pricing.enabled:false}")
public class OrderCheckoutService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final BigDecimal taxRate;
    private final BigDecimal configuredDeliveryFee;

    @Autowired
    public OrderCheckoutService(
            @Qualifier("tenantBusinessJdbcTemplate") JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            @Value("${mealminutes.order-pricing.tax-rate}") BigDecimal taxRate,
            @Value("${mealminutes.order-pricing.delivery-fee}") BigDecimal deliveryFee) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.taxRate = Objects.requireNonNull(taxRate, "taxRate");
        this.configuredDeliveryFee = Objects.requireNonNull(deliveryFee, "deliveryFee");
        if (taxRate.signum() < 0 || taxRate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("tax-rate must be between 0 and 1");
        }
        if (deliveryFee.signum() < 0) throw new IllegalArgumentException("delivery-fee must be zero or greater");
        this.configuredDeliveryFee.setScale(2, RoundingMode.UNNECESSARY);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public OrderResponse createOrder(String customerId, String idempotencyKey, OrderCheckoutRequest request) {
        validateRequest(customerId, idempotencyKey, request);
        String fingerprint = fingerprint(customerId, request);
        StoredOrder existing = findExisting(customerId, idempotencyKey);
        if (existing != null) return replayOrConflict(existing, fingerprint);

        BranchCapabilities branch = jdbc.query("""
                SELECT pickup_enabled, delivery_enabled FROM restaurant_branch
                WHERE branch_id = ? AND is_active = TRUE FOR UPDATE
                """, branchMapper(), request.branchId()).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active branch not found"));
        if (request.fulfillmentType() == OrderCheckoutRequest.FulfillmentType.PICKUP && !branch.pickupEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pickup is not available at this branch");
        }
        if (request.fulfillmentType() == OrderCheckoutRequest.FulfillmentType.DELIVERY && !branch.deliveryEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Delivery is not available at this branch");
        }
        if (request.fulfillmentType() == OrderCheckoutRequest.FulfillmentType.DELIVERY
                && (request.deliveryAddressId() == null || request.deliveryAddressId().isBlank())) {
            throw new IllegalArgumentException("deliveryAddressId is required for delivery");
        }
        if (request.fulfillmentType() == OrderCheckoutRequest.FulfillmentType.PICKUP
                && request.deliveryAddressId() != null && !request.deliveryAddressId().isBlank()) {
            throw new IllegalArgumentException("deliveryAddressId must be omitted for pickup");
        }

        List<String> carts = jdbc.query("""
                SELECT cart_id FROM customer_cart
                WHERE cart_id = ? AND customer_user_id = ? AND branch_id = ? AND cart_status = 'ACTIVE'
                FOR UPDATE
                """, (rs, row) -> rs.getString("cart_id"), request.cartId(), customerId, request.branchId());
        if (carts.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Active cart not found");

        List<OrderLine> lines = jdbc.query("""
                SELECT ci.item_id, ci.quantity, ci.item_notes, i.name, i.description,
                       i.price, i.currency, i.is_available
                FROM customer_cart_item ci
                INNER JOIN menu_item i ON i.item_id = ci.item_id
                WHERE ci.cart_id = ?
                ORDER BY ci.created_at ASC, ci.cart_item_id ASC
                FOR UPDATE
                """, lineMapper(), request.cartId());
        if (lines.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Cart is empty");
        if (lines.stream().anyMatch(line -> !line.available())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "One or more cart items are no longer available");
        }
        String currency = lines.getFirst().currency();
        if (lines.stream().anyMatch(line -> !currency.equals(line.currency()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "All items in an order must use the same currency");
        }
        BigDecimal subtotal = lines.stream()
                .map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxTotal = subtotal.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal deliveryFee = request.fulfillmentType() == OrderCheckoutRequest.FulfillmentType.DELIVERY
                ? configuredDeliveryFee.setScale(2, RoundingMode.UNNECESSARY) : BigDecimal.ZERO.setScale(2);
        BigDecimal discountTotal = BigDecimal.ZERO.setScale(2);
        BigDecimal grandTotal = subtotal.add(taxTotal).add(deliveryFee).subtract(discountTotal).setScale(2, RoundingMode.HALF_UP);
        String addressSnapshot = request.fulfillmentType() == OrderCheckoutRequest.FulfillmentType.DELIVERY
                ? loadAddressSnapshot(customerId, request.deliveryAddressId()) : null;

        String orderId = UUID.randomUUID().toString();
        String orderNumber = "MM" + LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        OrderResponse response = new OrderResponse(orderId, orderNumber, "PAYMENT_PENDING", "PENDING",
                request.fulfillmentType().name(), currency, subtotal, taxTotal, deliveryFee, discountTotal, grandTotal);
        try {
            jdbc.update("""
                    INSERT INTO customer_order(order_id, order_number, customer_user_id, branch_id,
                        order_status, fulfillment_type, payment_status, currency, subtotal, tax_total,
                        delivery_fee, discount_total, grand_total, idempotency_key, request_fingerprint,
                        delivery_address_snapshot, customer_note)
                    VALUES (?, ?, ?, ?, 'PAYMENT_PENDING', ?, 'PENDING', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, orderId, orderNumber, customerId, request.branchId(), request.fulfillmentType().name(),
                    currency, subtotal, taxTotal, deliveryFee, discountTotal, grandTotal, idempotencyKey,
                    fingerprint, addressSnapshot, normalizeNote(request.customerNote()));
        } catch (DuplicateKeyException duplicate) {
            StoredOrder raced = findExisting(customerId, idempotencyKey);
            if (raced != null) return replayOrConflict(raced, fingerprint);
            throw duplicate;
        }

        for (OrderLine line : lines) {
            String customization = line.notes() == null || line.notes().isBlank()
                    ? null : toJson(Map.of("notes", line.notes().strip()));
            BigDecimal lineTotal = line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())).setScale(2, RoundingMode.HALF_UP);
            jdbc.update("""
                    INSERT INTO customer_order_item(order_item_id, order_id, item_id, name_snapshot,
                        description_snapshot, unit_price_snapshot, quantity, line_total, customization_snapshot)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID().toString(), orderId, line.itemId(), line.name(), line.description(),
                    line.unitPrice(), line.quantity(), lineTotal, customization);
        }
        int checkedOut = jdbc.update("""
                UPDATE customer_cart SET cart_status = 'CHECKED_OUT', updated_at = CURRENT_TIMESTAMP(6)
                WHERE cart_id = ? AND customer_user_id = ? AND cart_status = 'ACTIVE'
                """, request.cartId(), customerId);
        if (checkedOut != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Cart state changed during checkout");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId);
        payload.put("orderNumber", orderNumber);
        payload.put("status", response.status());
        payload.put("currency", currency);
        payload.put("grandTotal", grandTotal);
        jdbc.update("""
                INSERT INTO outbox_event(event_id, tenant_id, aggregate_type, aggregate_id, event_type,
                    schema_version, correlation_id, payload)
                VALUES (?, ?, 'CUSTOMER_ORDER', ?, 'order.created', 1, ?, ?)
                """, UUID.randomUUID().toString(), TenantContext.requireCurrent().tenantId(), orderId,
                org.slf4j.MDC.get("correlationId"), toJson(payload));
        return response;
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public OrderResponse getOrder(String customerId, String orderId) {
        requireId(customerId, "customerId");
        requireId(orderId, "orderId");
        return jdbc.query("""
                SELECT order_id, order_number, order_status, payment_status, fulfillment_type, currency,
                       subtotal, tax_total, delivery_fee, discount_total, grand_total
                FROM customer_order WHERE customer_user_id = ? AND order_id = ?
                """, orderMapper(), customerId, orderId).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<OrderResponse> listOrders(String customerId, int page, int size) {
        requireId(customerId, "customerId");
        if (page < 0) throw new IllegalArgumentException("page must be zero or greater");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
        final int offset;
        try { offset = Math.multiplyExact(page, size); }
        catch (ArithmeticException ex) { throw new IllegalArgumentException("page is too large", ex); }
        return jdbc.query("""
                SELECT order_id, order_number, order_status, payment_status, fulfillment_type, currency,
                       subtotal, tax_total, delivery_fee, discount_total, grand_total
                FROM customer_order WHERE customer_user_id = ?
                ORDER BY created_at DESC, order_id DESC LIMIT ? OFFSET ?
                """, orderMapper(), customerId, size, offset);
    }

    static String fingerprint(String customerId, OrderCheckoutRequest request) {
        String canonical = String.join("\n", customerId, request.cartId(), request.branchId(),
                request.fulfillmentType().name(), request.deliveryAddressId() == null ? "" : request.deliveryAddressId(),
                normalizeNote(request.customerNote()) == null ? "" : normalizeNote(request.customerNote()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private StoredOrder findExisting(String customerId, String idempotencyKey) {
        return jdbc.query("""
                SELECT order_id, order_number, order_status, payment_status, fulfillment_type, currency,
                       subtotal, tax_total, delivery_fee, discount_total, grand_total, request_fingerprint
                FROM customer_order WHERE customer_user_id = ? AND idempotency_key = ? FOR UPDATE
                """, (rs, row) -> new StoredOrder(rs.getString("request_fingerprint"),
                new OrderResponse(rs.getString("order_id"), rs.getString("order_number"), rs.getString("order_status"),
                        rs.getString("payment_status"), rs.getString("fulfillment_type"), rs.getString("currency"),
                        rs.getBigDecimal("subtotal"), rs.getBigDecimal("tax_total"), rs.getBigDecimal("delivery_fee"),
                        rs.getBigDecimal("discount_total"), rs.getBigDecimal("grand_total"))),
                customerId, idempotencyKey).stream().findFirst().orElse(null);
    }

    private OrderResponse replayOrConflict(StoredOrder existing, String fingerprint) {
        if (!existing.fingerprint().equals(fingerprint)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key was already used for a different checkout request");
        }
        return existing.response();
    }

    private String loadAddressSnapshot(String customerId, String addressId) {
        List<AddressSnapshot> addresses = jdbc.query("""
                SELECT label, recipient_name, recipient_phone, address_line1, address_line2,
                       city, region, postal_code, country_code, delivery_instructions
                FROM customer_address WHERE address_id = ? AND customer_user_id = ?
                """, (rs, row) -> new AddressSnapshot(rs.getString("label"), rs.getString("recipient_name"),
                rs.getString("recipient_phone"), rs.getString("address_line1"), rs.getString("address_line2"),
                rs.getString("city"), rs.getString("region"), rs.getString("postal_code"),
                rs.getString("country_code"), rs.getString("delivery_instructions")), addressId, customerId);
        if (addresses.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery address not found");
        return toJson(addresses.getFirst());
    }

    private String toJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JacksonException ex) { throw new IllegalStateException("Unable to serialize order payload", ex); }
    }

    private static void validateRequest(String customerId, String key, OrderCheckoutRequest request) {
        requireId(customerId, "customerId");
        Objects.requireNonNull(request, "request");
        requireId(request.cartId(), "cartId");
        requireId(request.branchId(), "branchId");
        Objects.requireNonNull(request.fulfillmentType(), "fulfillmentType");
        if (key == null || !key.matches("[A-Za-z0-9][A-Za-z0-9._:-]{7,127}")) {
            throw new IllegalArgumentException("Idempotency-Key must be 8–128 safe characters");
        }
        if (request.customerNote() != null && request.customerNote().length() > 1000) {
            throw new IllegalArgumentException("customerNote must be at most 1000 characters");
        }
    }
    private static String normalizeNote(String note) { return note == null || note.isBlank() ? null : note.strip(); }
    private static void requireId(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 36) throw new IllegalArgumentException(field + " is required");
    }
    private static RowMapper<OrderResponse> orderMapper() {
        return (rs, row) -> new OrderResponse(rs.getString("order_id"), rs.getString("order_number"),
                rs.getString("order_status"), rs.getString("payment_status"), rs.getString("fulfillment_type"),
                rs.getString("currency"), rs.getBigDecimal("subtotal"), rs.getBigDecimal("tax_total"),
                rs.getBigDecimal("delivery_fee"), rs.getBigDecimal("discount_total"), rs.getBigDecimal("grand_total"));
    }
    private static RowMapper<BranchCapabilities> branchMapper() {
        return (rs, row) -> new BranchCapabilities(rs.getBoolean("pickup_enabled"), rs.getBoolean("delivery_enabled"));
    }
    private static RowMapper<OrderLine> lineMapper() {
        return (rs, row) -> new OrderLine(rs.getString("item_id"), rs.getInt("quantity"), rs.getString("item_notes"),
                rs.getString("name"), rs.getString("description"), rs.getBigDecimal("price"),
                rs.getString("currency"), rs.getBoolean("is_available"));
    }
    record BranchCapabilities(boolean pickupEnabled, boolean deliveryEnabled) { }
    record OrderLine(String itemId, int quantity, String notes, String name, String description,
                             BigDecimal unitPrice, String currency, boolean available) { }
    public record AddressSnapshot(String label, String recipientName, String recipientPhone, String addressLine1,
                                   String addressLine2, String city, String region, String postalCode,
                                   String countryCode, String deliveryInstructions) { }
    record StoredOrder(String fingerprint, OrderResponse response) { }
}
