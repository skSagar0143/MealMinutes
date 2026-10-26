package com.mealminutes.business.payment.api;

import com.mealminutes.business.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PaymentControllerTests {
    @Test void forwardsSignedCustomerAndIdempotencyKey() {
        PaymentService service=mock(PaymentService.class);
        Jwt jwt=mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("customer-1");
        var expected=new PaymentIntentResponse("payment-1","RAZORPAY","order_rzp_1","rzp_public",1100,"INR","PENDING");
        when(service.createPaymentIntent("customer-1","order-1","payment-key-123")).thenReturn(expected);
        var controller=new PaymentController(service);
        assertThat(controller.create(jwt,"order-1","payment-key-123")).isEqualTo(expected);
        verify(service).createPaymentIntent("customer-1","order-1","payment-key-123");
    }
}