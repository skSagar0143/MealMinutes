package com.mealminutes.business.payment.service;

import com.mealminutes.business.payment.api.PaymentIntentResponse;
import com.mealminutes.platform.tenant.domain.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@ConditionalOnExpression("${mealminutes.payment.enabled:false} and ${mealminutes.tenant-provisioning.enabled:false} and ${mealminutes.security.enabled:false} and ${mealminutes.order-pricing.enabled:false}")
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final PaymentGateway gateway;
    private final ObjectMapper objectMapper;

    @Autowired
    public PaymentService(@Qualifier("tenantBusinessJdbcTemplate") JdbcTemplate jdbc,
                          @Qualifier("tenantTransactionManager") PlatformTransactionManager transactionManager,
                          PaymentGateway gateway, ObjectMapper objectMapper) {
        this(jdbc, transactionManager, gateway, objectMapper, true);
    }

    PaymentService(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
                   PaymentGateway gateway, ObjectMapper objectMapper, boolean testOrWiredConstructor) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager"));
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public PaymentIntentResponse createPaymentIntent(String customerId, String orderId, String idempotencyKey) {
        validateId(customerId, "customerId");
        validateId(orderId, "orderId");
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9][A-Za-z0-9._:-]{7,127}")) {
            throw new IllegalArgumentException("Idempotency-Key must be 8–128 safe characters");
        }
        PaymentAttempt attempt = transactions.execute(status -> reserveAttempt(customerId, orderId, idempotencyKey));
        if (attempt.providerOrderId() != null && !attempt.providerOrderId().isBlank()) {
            return response(attempt);
        }
        if (!attempt.newlyCreated()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Payment attempt is already being processed; do not create a second attempt");
        }

        long amountPaise = toPaise(attempt.amount());
        try {
            PaymentGateway.ProviderOrder providerOrder = gateway.createOrder(attempt.paymentId(), amountPaise, attempt.currency());
            if (providerOrder.amountPaise() != amountPaise || !attempt.currency().equals(providerOrder.currency())
                    || providerOrder.orderId() == null || providerOrder.orderId().isBlank()) {
                throw new PaymentGateway.PaymentGatewayException("Payment provider returned mismatched order details");
            }
            Integer updated = transactions.execute(status -> jdbc.update("""
                    UPDATE payment_transaction
                    SET provider_order_id = ?, provider_reference = ?, failure_code = NULL, updated_at = CURRENT_TIMESTAMP(6)
                    WHERE payment_id = ? AND payment_status = 'PENDING'
                    """, providerOrder.orderId(), providerOrder.orderId(), attempt.paymentId()));
            if (updated == null || updated != 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment attempt state changed unexpectedly");
            }
            return new PaymentIntentResponse(attempt.paymentId(), gateway.providerName(), providerOrder.orderId(),
                    gateway.publicKeyId(), amountPaise, attempt.currency(), "PENDING");
        } catch (PaymentGateway.PaymentGatewayException failure) {
            transactions.execute(status -> jdbc.update("""
                    UPDATE payment_transaction SET failure_code = 'PROVIDER_UNCERTAIN', updated_at = CURRENT_TIMESTAMP(6)
                    WHERE payment_id = ? AND payment_status = 'PENDING'
                    """, attempt.paymentId()));
            log.warn("Payment provider outcome is uncertain for internal payment {}", attempt.paymentId());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Payment provider could not confirm the attempt; reconcile it before retrying", failure);
        }
    }

    public WebhookResult handleWebhook(String rawBody, String signature, String eventId) {
        if (rawBody == null || rawBody.isBlank() || rawBody.length() > 1_000_000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment webhook body");
        }
        if (!gateway.verifyWebhookSignature(rawBody, signature)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid payment webhook signature");
        }
        if (eventId == null || eventId.isBlank() || eventId.length() > 160) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment webhook event ID");
        }
        Map<?, ?> envelope = readMap(rawBody);
        String eventType = text(envelope.get("event"));
        if (eventType == null || eventType.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment webhook event type is required");
        }
        String payloadHash = sha256(rawBody);
        return transactions.execute(status -> processWebhook(eventId, eventType, payloadHash, envelope));
    }

    private PaymentAttempt reserveAttempt(String customerId, String orderId, String idempotencyKey) {
        OrderForPayment order = jdbc.query("""
                SELECT order_id, order_number, order_status, payment_status, currency, grand_total
                FROM customer_order WHERE order_id = ? AND customer_user_id = ? FOR UPDATE
                """, orderForPaymentMapper(), orderId, customerId).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (!"INR".equals(order.currency())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Razorpay checkout currently supports INR orders only");
        }
        long amountPaise = toPaise(order.grandTotal());
        if (amountPaise < 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Order total must be positive for online payment");
        String fingerprint = sha256(orderId + "|" + amountPaise + "|" + order.currency());

        PaymentAttempt existing = jdbc.query("""
                SELECT payment_id, order_id, provider, amount, currency, payment_status,
                       provider_order_id, request_fingerprint
                FROM payment_transaction WHERE order_id = ? AND idempotency_key = ? FOR UPDATE
                """, paymentAttemptMapper(false), orderId, idempotencyKey).stream().findFirst().orElse(null);
        if (existing != null) {
            if (!existing.requestFingerprint().equals(fingerprint)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Idempotency key was already used for a different payment request");
            }
            return existing;
        }
        if (!"PAYMENT_PENDING".equals(order.orderStatus()) || !"PENDING".equals(order.paymentStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order is not awaiting payment");
        }
        String paymentId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO payment_transaction(payment_id, order_id, provider, amount, currency,
                    payment_status, idempotency_key, request_fingerprint)
                VALUES (?, ?, ?, ?, ?, 'PENDING', ?, ?)
                """, paymentId, orderId, gateway.providerName(), order.grandTotal(), order.currency(),
                idempotencyKey, fingerprint);
        return new PaymentAttempt(paymentId, orderId, gateway.providerName(), order.grandTotal(),
                order.currency(), "PENDING", null, fingerprint, true);
    }

    private WebhookResult processWebhook(String eventId, String eventType, String payloadHash, Map<?, ?> envelope) {
        try {
            jdbc.update("""
                    INSERT INTO payment_webhook_event(webhook_event_id, provider, event_type, payload_sha256, processing_status)
                    VALUES (?, ?, ?, ?, 'RECEIVED')
                    """, eventId, gateway.providerName(), eventType, payloadHash);
        } catch (DuplicateKeyException duplicate) {
            return WebhookResult.DUPLICATE;
        }
        if (!"payment.captured".equals(eventType) && !"payment.failed".equals(eventType)) {
            markWebhook(eventId, "PROCESSED", null);
            return WebhookResult.IGNORED;
        }

        Map<?, ?> payload = asMap(envelope.get("payload"));
        Map<?, ?> paymentWrapper = asMap(payload.get("payment"));
        Map<?, ?> entity = asMap(paymentWrapper.get("entity"));
        String providerOrderId = text(entity.get("order_id"));
        String providerPaymentId = text(entity.get("id"));
        String currency = text(entity.get("currency"));
        String errorCode = text(entity.get("error_code"));
        long amountPaise = number(entity.get("amount"));
        if (providerOrderId == null || providerPaymentId == null || currency == null || amountPaise < 1) {
            markWebhook(eventId, "FAILED", "MISSING_PAYMENT_FIELDS");
            return WebhookResult.REJECTED;
        }
        PaymentAttempt attempt = jdbc.query("""
                SELECT payment_id, order_id, provider, amount, currency, payment_status,
                       provider_order_id, request_fingerprint
                FROM payment_transaction WHERE provider = ? AND provider_order_id = ? FOR UPDATE
                """, paymentAttemptMapper(false), gateway.providerName(), providerOrderId)
                .stream().findFirst().orElse(null);
        if (attempt == null) {
            markWebhook(eventId, "FAILED", "PAYMENT_ATTEMPT_NOT_FOUND");
            return WebhookResult.REJECTED;
        }
        if (toPaise(attempt.amount()) != amountPaise || !attempt.currency().equals(currency)) {
            markWebhook(eventId, "FAILED", "AMOUNT_OR_CURRENCY_MISMATCH");
            log.error("Payment webhook amount/currency mismatch for payment {}", attempt.paymentId());
            return WebhookResult.REJECTED;
        }
        if ("SUCCEEDED".equals(attempt.paymentStatus())) {
            markWebhook(eventId, "PROCESSED", null);
            return WebhookResult.IGNORED;
        }

        if ("payment.captured".equals(eventType)) {
            int updated = jdbc.update("""
                    UPDATE payment_transaction SET payment_status = 'SUCCEEDED', provider_payment_id = ?,
                        provider_reference = ?, webhook_event_id = ?, failure_code = NULL, updated_at = CURRENT_TIMESTAMP(6)
                    WHERE payment_id = ? AND payment_status IN ('PENDING', 'FAILED')
                    """, providerPaymentId, providerPaymentId, eventId, attempt.paymentId());
            if (updated == 0) {
                markWebhook(eventId, "PROCESSED", null);
                return WebhookResult.IGNORED;
            }
            jdbc.update("""
                    UPDATE customer_order SET payment_status = 'SUCCEEDED', order_status = 'CONFIRMED',
                        updated_at = CURRENT_TIMESTAMP(6)
                    WHERE order_id = ? AND order_status = 'PAYMENT_PENDING' AND payment_status IN ('PENDING', 'FAILED')
                    """, attempt.orderId());
            insertOutbox(attempt.orderId(), "order.payment_succeeded", Map.of(
                    "orderId", attempt.orderId(), "paymentId", attempt.paymentId(), "providerPaymentId", providerPaymentId));
        } else {
            int updated = jdbc.update("""
                    UPDATE payment_transaction SET payment_status = 'FAILED', provider_payment_id = ?,
                        provider_reference = ?, webhook_event_id = ?, failure_code = ?, updated_at = CURRENT_TIMESTAMP(6)
                    WHERE payment_id = ? AND payment_status = 'PENDING'
                    """, providerPaymentId, providerPaymentId, eventId,
                    errorCode == null ? "PAYMENT_FAILED" : errorCode, attempt.paymentId());
            if (updated == 0) {
                markWebhook(eventId, "PROCESSED", null);
                return WebhookResult.IGNORED;
            }
            jdbc.update("""
                    UPDATE customer_order SET payment_status = 'FAILED', updated_at = CURRENT_TIMESTAMP(6)
                    WHERE order_id = ? AND order_status = 'PAYMENT_PENDING' AND payment_status = 'PENDING'
                    """, attempt.orderId());
            insertOutbox(attempt.orderId(), "order.payment_failed", Map.of(
                    "orderId", attempt.orderId(), "paymentId", attempt.paymentId(), "providerPaymentId", providerPaymentId));
        }
        markWebhook(eventId, "PROCESSED", null);
        return WebhookResult.PROCESSED;
    }

    private void insertOutbox(String orderId, String eventType, Map<String, Object> payload) {
        jdbc.update("""
                INSERT INTO outbox_event(event_id, tenant_id, aggregate_type, aggregate_id, event_type,
                    schema_version, correlation_id, payload)
                VALUES (?, ?, 'CUSTOMER_ORDER', ?, ?, 1, ?, ?)
                """, UUID.randomUUID().toString(), TenantContext.requireCurrent().tenantId(), orderId,
                eventType, org.slf4j.MDC.get("correlationId"), writeJson(payload));
    }

    private void markWebhook(String eventId, String state, String error) {
        jdbc.update("""
                UPDATE payment_webhook_event SET processing_status = ?, last_error = ?,
                    processed_at = CURRENT_TIMESTAMP(6)
                WHERE webhook_event_id = ?
                """, state, error, eventId);
    }

    private PaymentIntentResponse response(PaymentAttempt attempt) {
        return new PaymentIntentResponse(attempt.paymentId(), attempt.provider(), attempt.providerOrderId(),
                gateway.publicKeyId(), toPaise(attempt.amount()), attempt.currency(), attempt.paymentStatus());
    }

    private String writeJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JacksonException ex) { throw new IllegalStateException("Unable to serialize payment event", ex); }
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> readMap(String rawBody) {
        try { return objectMapper.readValue(rawBody, Map.class); }
        catch (JacksonException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment webhook JSON", ex); }
    }
    private static Map<?, ?> asMap(Object value) { return value instanceof Map<?, ?> map ? map : Map.of(); }
    private static String text(Object value) { return value instanceof String string ? string : null; }
    private static long number(Object value) { return value instanceof Number number ? number.longValue() : -1; }
    private static long toPaise(BigDecimal amount) {
        try { return amount.movePointRight(2).longValueExact(); }
        catch (ArithmeticException ex) { throw new IllegalArgumentException("Payment amount must have at most two decimal places", ex); }
    }
    private static void validateId(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 36) throw new IllegalArgumentException(field + " is required");
    }
    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 is unavailable", impossible); }
    }
    private static RowMapper<OrderForPayment> orderForPaymentMapper() {
        return (rs, row) -> new OrderForPayment(rs.getString("order_id"), rs.getString("order_number"),
                rs.getString("order_status"), rs.getString("payment_status"), rs.getString("currency"),
                rs.getBigDecimal("grand_total"));
    }
    private static RowMapper<PaymentAttempt> paymentAttemptMapper(boolean newlyCreated) {
        return (rs, row) -> new PaymentAttempt(rs.getString("payment_id"), rs.getString("order_id"),
                rs.getString("provider"), rs.getBigDecimal("amount"), rs.getString("currency"),
                rs.getString("payment_status"), rs.getString("provider_order_id"),
                rs.getString("request_fingerprint"), newlyCreated);
    }

    record OrderForPayment(String orderId, String orderNumber, String orderStatus, String paymentStatus,
                           String currency, BigDecimal grandTotal) { }
    record PaymentAttempt(String paymentId, String orderId, String provider, BigDecimal amount, String currency,
                          String paymentStatus, String providerOrderId, String requestFingerprint, boolean newlyCreated) { }
    public enum WebhookResult { PROCESSED, DUPLICATE, IGNORED, REJECTED }
}
