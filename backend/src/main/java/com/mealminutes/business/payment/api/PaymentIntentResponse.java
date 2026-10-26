package com.mealminutes.business.payment.api;

public record PaymentIntentResponse(
        String paymentId,
        String provider,
        String providerOrderId,
        String publicKeyId,
        long amountPaise,
        String currency,
        String status) { }
