package com.mealminutes.business.payment.service;

import org.junit.jupiter.api.Test;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import static org.assertj.core.api.Assertions.assertThat;

class PaymentWebhookSignatureVerifierTests {
    @Test void verifiesValidHmacAndRejectsTamperingOrMissingInputs() throws Exception {
        String body="{\"event\":\"payment.captured\"}";
        String secret="webhook-secret";
        Mac mac=Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        String signature=HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        assertThat(PaymentWebhookSignatureVerifier.verifyHmacSha256(body,signature,secret)).isTrue();
        assertThat(PaymentWebhookSignatureVerifier.verifyHmacSha256(body,signature.toUpperCase(),secret)).isTrue();
        assertThat(PaymentWebhookSignatureVerifier.verifyHmacSha256(body+" ",signature,secret)).isFalse();
        assertThat(PaymentWebhookSignatureVerifier.verifyHmacSha256(body,null,secret)).isFalse();
        assertThat(PaymentWebhookSignatureVerifier.verifyHmacSha256(body,signature," ")).isFalse();
    }
}