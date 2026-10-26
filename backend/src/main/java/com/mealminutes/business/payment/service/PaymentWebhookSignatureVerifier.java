package com.mealminutes.business.payment.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

public final class PaymentWebhookSignatureVerifier {
    private PaymentWebhookSignatureVerifier() { }

    public static boolean verifyHmacSha256(String rawBody, String signature, String secret) {
        if (rawBody == null || signature == null || signature.isBlank() || secret == null || secret.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)))
                    .getBytes(StandardCharsets.US_ASCII);
            byte[] supplied = signature.strip().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
            return MessageDigest.isEqual(expected, supplied);
        } catch (Exception invalidConfiguration) {
            return false;
        }
    }
}
