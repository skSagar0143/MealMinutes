package com.mealminutes.business.payment.service;

import com.mealminutes.platform.tenant.domain.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"unchecked","rawtypes"})
class PaymentServiceTests {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final PlatformTransactionManager tx=mock(PlatformTransactionManager.class);
    private final TransactionStatus status=mock(TransactionStatus.class);
    private final PaymentGateway gateway=mock(PaymentGateway.class);
    private final PaymentService service=new PaymentService(jdbc,tx,gateway,new ObjectMapper(),true);

    @BeforeEach void setupTransaction(){when(tx.getTransaction(any(TransactionDefinition.class))).thenReturn(status); when(gateway.providerName()).thenReturn("RAZORPAY");}
    @AfterEach void clearTenant(){TenantContext.clear();}

    @Test void createsProviderIntentAfterReservingIdempotentPaymentAttempt(){
        stubOrder();
        when(jdbc.query(contains("FROM payment_transaction WHERE order_id = ? AND idempotency_key = ?"),
                any(RowMapper.class),eq("order-1"),eq("payment-key-123"))).thenReturn(List.of());
        when(jdbc.update(anyString(),any(Object[].class))).thenReturn(1);
        when(gateway.providerName()).thenReturn("RAZORPAY");
        when(gateway.publicKeyId()).thenReturn("rzp_test_public");
        when(gateway.createOrder(anyString(),eq(1100L),eq("INR")))
                .thenReturn(new PaymentGateway.ProviderOrder("order_rzp_1",1100L,"INR"));
        var response=service.createPaymentIntent("customer-1","order-1","payment-key-123");
        assertThat(response.provider()).isEqualTo("RAZORPAY");
        assertThat(response.providerOrderId()).isEqualTo("order_rzp_1");
        assertThat(response.amountPaise()).isEqualTo(1100L);
        assertThat(response.publicKeyId()).isEqualTo("rzp_test_public");
        verify(jdbc).update(contains("INSERT INTO payment_transaction"),any(Object[].class));
        verify(jdbc).update(contains("SET provider_order_id"),any(Object[].class));
    }

    @Test void replaysExistingIntentAndRejectsIdempotencyKeyFingerprintMismatch(){
        stubOrder();
        when(jdbc.query(contains("FROM payment_transaction WHERE order_id = ? AND idempotency_key = ?"),
                any(RowMapper.class),eq("order-1"),eq("payment-key-123")))
                .thenReturn(List.of(new PaymentService.PaymentAttempt("payment-1","order-1","RAZORPAY",
                        new BigDecimal("11.00"),"INR","PENDING","order_rzp_1",fingerprint("order-1",1100,"INR"),false)));
        when(gateway.providerName()).thenReturn("RAZORPAY");
        when(gateway.publicKeyId()).thenReturn("rzp_public");
        assertThat(service.createPaymentIntent("customer-1","order-1","payment-key-123").providerOrderId())
                .isEqualTo("order_rzp_1");
        reset(jdbc);
        stubOrder();
        when(jdbc.query(contains("FROM payment_transaction WHERE order_id = ? AND idempotency_key = ?"),
                any(RowMapper.class),eq("order-1"),eq("payment-key-123")))
                .thenReturn(List.of(new PaymentService.PaymentAttempt("payment-1","order-1","RAZORPAY",
                        new BigDecimal("11.00"),"INR","PENDING","order_rzp_1","wrong",false)));
        assertThatThrownBy(()->service.createPaymentIntent("customer-1","order-1","payment-key-123"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test void leavesAmbiguousProviderFailureForReconciliationInsteadOfCreatingDuplicate(){
        stubOrder();
        when(jdbc.query(contains("FROM payment_transaction WHERE order_id = ? AND idempotency_key = ?"),
                any(RowMapper.class),eq("order-1"),eq("payment-key-123"))).thenReturn(List.of());
        when(jdbc.update(anyString(),any(Object[].class))).thenReturn(1);
        when(gateway.providerName()).thenReturn("RAZORPAY");
        when(gateway.createOrder(anyString(),eq(1100L),eq("INR")))
                .thenThrow(new PaymentGateway.PaymentGatewayException("network timeout"));
        assertThatThrownBy(()->service.createPaymentIntent("customer-1","order-1","payment-key-123"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e->((ResponseStatusException)e).getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        verify(jdbc).update(contains("PROVIDER_UNCERTAIN"),any(Object[].class));
    }

    @Test void processesCapturedWebhookOnceAndUpdatesOrderAndOutbox(){
        when(gateway.verifyWebhookSignature(anyString(),eq("valid-signature"))).thenReturn(true);
        when(jdbc.update(anyString(),any(Object[].class))).thenReturn(1);
        when(jdbc.query(contains("WHERE provider = ? AND provider_order_id = ?"),any(RowMapper.class),
                eq("RAZORPAY"),eq("order_rzp_1"))).thenReturn(List.of(paymentAttempt("PENDING")));
        String body=capturedBody("payment.captured",1100);
        try(var ignored=TenantContext.bind(tenant())){
            assertThat(service.handleWebhook(body,"valid-signature","evt-1")).isEqualTo(PaymentService.WebhookResult.PROCESSED);
        }
        verify(jdbc).update(contains("UPDATE payment_transaction SET payment_status = 'SUCCEEDED'"),any(Object[].class));
        verify(jdbc).update(contains("SET payment_status = 'SUCCEEDED', order_status = 'CONFIRMED'"),any(Object[].class));
        verify(jdbc).update(contains("INSERT INTO outbox_event"),any(Object[].class));
    }

    @Test void recordsFailedPaymentAndRejectsAmountMismatch(){
        when(gateway.verifyWebhookSignature(anyString(),eq("valid-signature"))).thenReturn(true);
        when(jdbc.update(anyString(),any(Object[].class))).thenReturn(1);
        when(jdbc.query(contains("WHERE provider = ? AND provider_order_id = ?"),any(RowMapper.class),
                eq("RAZORPAY"),eq("order_rzp_1"))).thenReturn(List.of(paymentAttempt("PENDING")));
        try(var ignored=TenantContext.bind(tenant())){
            assertThat(service.handleWebhook(capturedBody("payment.failed",1100,"BAD_REQUEST_ERROR"),
                    "valid-signature","evt-fail")).isEqualTo(PaymentService.WebhookResult.PROCESSED);
        }
        verify(jdbc).update(contains("UPDATE payment_transaction SET payment_status = 'FAILED'"),any(Object[].class));
        reset(jdbc);
        when(gateway.verifyWebhookSignature(anyString(),eq("valid-signature"))).thenReturn(true);
        when(jdbc.update(anyString(),any(Object[].class))).thenReturn(1);
        when(jdbc.query(contains("WHERE provider = ? AND provider_order_id = ?"),any(RowMapper.class),
                eq("RAZORPAY"),eq("order_rzp_1"))).thenReturn(List.of(paymentAttempt("PENDING")));
        try(var ignored=TenantContext.bind(tenant())){
            assertThat(service.handleWebhook(capturedBody("payment.captured",1200),
                    "valid-signature","evt-mismatch")).isEqualTo(PaymentService.WebhookResult.REJECTED);
        }
        verify(jdbc).update(contains("UPDATE payment_webhook_event SET processing_status"),any(Object[].class));
    }

    @Test void rejectsInvalidSignatureAndDeduplicatesWebhookEventId(){
        when(gateway.verifyWebhookSignature(anyString(),eq("invalid"))).thenReturn(false);
        assertThatThrownBy(()->service.handleWebhook(capturedBody("payment.captured",1100),"invalid","evt-1"))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(jdbc);
        reset(jdbc);
        when(gateway.verifyWebhookSignature(anyString(),eq("valid-signature"))).thenReturn(true);
        when(jdbc.update(contains("INSERT INTO payment_webhook_event"),any(Object[].class)))
                .thenThrow(new DuplicateKeyException("duplicate event"));
        assertThat(service.handleWebhook(capturedBody("payment.captured",1100),"valid-signature","evt-1"))
                .isEqualTo(PaymentService.WebhookResult.DUPLICATE);
        verify(jdbc,never()).query(anyString(),any(RowMapper.class),any(Object[].class));
    }

    private void stubOrder(){
        when(jdbc.query(contains("FROM customer_order WHERE order_id = ? AND customer_user_id = ?"),
                any(RowMapper.class),eq("order-1"),eq("customer-1")))
                .thenReturn(List.of(new PaymentService.OrderForPayment("order-1","MM202610261234567890123456",
                        "PAYMENT_PENDING","PENDING","INR",new BigDecimal("11.00"))));
        when(jdbc.query(contains("AND payment_status = 'PENDING' AND provider_order_id IS NULL"),
                any(RowMapper.class),eq("order-1"))).thenReturn(List.of());
    }
    private PaymentService.PaymentAttempt paymentAttempt(String paymentStatus){
        return new PaymentService.PaymentAttempt("payment-1","order-1","RAZORPAY",new BigDecimal("11.00"),
                "INR",paymentStatus,"order_rzp_1",fingerprint("order-1",1100,"INR"),false);
    }
    private String fingerprint(String orderId,long amount,String currency){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest((orderId+"|"+amount+"|"+currency).getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private String capturedBody(String event,long amount){return capturedBody(event,amount,null);}
    private String capturedBody(String event,long amount,String error){
        String suffix=error==null?"":" , \"error_code\":\""+error+"\"";
        return "{\"event\":\""+event+"\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"pay_123\",\"order_id\":\"order_rzp_1\",\"amount\":"+amount+",\"currency\":\"INR\",\"status\":\""+("payment.captured".equals(event)?"captured":"failed")+"\""+suffix+"}}}}";
    }
    private TenantContext.TenantDescriptor tenant(){
        return new TenantContext.TenantDescriptor("tenant-1","abc-food","tenant_abc_food","secret://db");
    }
}
