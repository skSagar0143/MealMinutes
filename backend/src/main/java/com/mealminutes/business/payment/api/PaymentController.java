package com.mealminutes.business.payment.api;

import com.mealminutes.business.payment.service.PaymentService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customer/orders/{orderId}/payment-intents")
@ConditionalOnExpression("${mealminutes.payment.enabled:false} and ${mealminutes.tenant-provisioning.enabled:false} and ${mealminutes.security.enabled:false} and ${mealminutes.order-pricing.enabled:false}")
@PreAuthorize("hasRole('CUSTOMER')")
public class PaymentController {
    private final PaymentService payments;
    public PaymentController(PaymentService payments) { this.payments = payments; }

    @PostMapping
    public PaymentIntentResponse create(@AuthenticationPrincipal Jwt jwt,
                                        @PathVariable String orderId,
                                        @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey) {
        return payments.createPaymentIntent(jwt.getSubject(), orderId, idempotencyKey);
    }
}