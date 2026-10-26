package com.mealminutes.business.payment.service;

public interface PaymentGateway {
    String providerName();
    String publicKeyId();
    ProviderOrder createOrder(String receipt, long amountPaise, String currency);
    boolean verifyWebhookSignature(String rawBody, String signature);

    record ProviderOrder(String orderId, long amountPaise, String currency) { }

    final class PaymentGatewayException extends RuntimeException {
        public PaymentGatewayException(String message) { super(message); }
        public PaymentGatewayException(String message, Throwable cause) { super(message, cause); }
    }
}
