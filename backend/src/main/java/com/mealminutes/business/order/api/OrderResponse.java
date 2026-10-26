package com.mealminutes.business.order.api;

import java.math.BigDecimal;

public record OrderResponse(
        String orderId, String orderNumber, String status, String paymentStatus,
        String fulfillmentType, String currency, BigDecimal subtotal, BigDecimal taxTotal,
        BigDecimal deliveryFee, BigDecimal discountTotal, BigDecimal grandTotal) { }
