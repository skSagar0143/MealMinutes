package com.mealminutes.business.payment.api;

import com.mealminutes.business.payment.service.PaymentService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RazorpayWebhookControllerTests {
    @Test void acknowledgesProcessedAndDuplicateWebhookEvents() {
        PaymentService service=mock(PaymentService.class);
        when(service.handleWebhook("body","signature","event-1")).thenReturn(PaymentService.WebhookResult.PROCESSED);
        when(service.handleWebhook("body","signature","event-2")).thenReturn(PaymentService.WebhookResult.DUPLICATE);
        var controller=new RazorpayWebhookController(service);
        assertThat(controller.receive("body","signature","event-1").getStatusCode().value()).isEqualTo(204);
        assertThat(controller.receive("body","signature","event-2").getStatusCode().value()).isEqualTo(204);
    }

    @Test void acknowledgesRejectedSignedEventsForManualReconciliation() {
        PaymentService service=mock(PaymentService.class);
        when(service.handleWebhook("body","signature","event-1")).thenReturn(PaymentService.WebhookResult.REJECTED);
        var controller=new RazorpayWebhookController(service);
        assertThat(controller.receive("body","signature","event-1").getStatusCode().value()).isEqualTo(202);
    }
}