package com.mealminutes.business.payment.api;

import com.mealminutes.business.payment.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks/razorpay")
@ConditionalOnExpression("${mealminutes.payment.enabled:false} and ${mealminutes.tenant-provisioning.enabled:false} and ${mealminutes.security.enabled:false} and ${mealminutes.order-pricing.enabled:false}")
public class RazorpayWebhookController {
    private static final Logger log = LoggerFactory.getLogger(RazorpayWebhookController.class);
    private final PaymentService payments;
    public RazorpayWebhookController(PaymentService payments) { this.payments = payments; }

    @PostMapping(consumes = "application/json")
    public ResponseEntity<Void> receive(
            @RequestBody String rawBody,
            @RequestHeader("X-Razorpay-Signature") String signature,
            @RequestHeader("X-Razorpay-Event-Id") String eventId) {
        PaymentService.WebhookResult result = payments.handleWebhook(rawBody, signature, eventId);
        if (result == PaymentService.WebhookResult.REJECTED) {
            log.error("Razorpay webhook {} was rejected after signature validation; investigate payment reconciliation", eventId);
            return ResponseEntity.accepted().build();
        }
        return ResponseEntity.noContent().build();
    }
}