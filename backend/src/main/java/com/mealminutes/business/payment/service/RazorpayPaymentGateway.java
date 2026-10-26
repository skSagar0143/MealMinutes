package com.mealminutes.business.payment.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

@Component
@ConditionalOnExpression("${mealminutes.payment.enabled:false} and ${mealminutes.tenant-provisioning.enabled:false} and ${mealminutes.security.enabled:false} and ${mealminutes.order-pricing.enabled:false}")
public class RazorpayPaymentGateway implements PaymentGateway {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String keyId;
    private final String keySecret;
    private final String webhookSecret;
    private final String apiBaseUrl;

    public RazorpayPaymentGateway(ObjectMapper objectMapper,
            @Value("${mealminutes.payment.razorpay.key-id}") String keyId,
            @Value("${mealminutes.payment.razorpay.key-secret}") String keySecret,
            @Value("${mealminutes.payment.razorpay.webhook-secret}") String webhookSecret,
            @Value("${mealminutes.payment.razorpay.api-base-url:https://api.razorpay.com/v1}") String apiBaseUrl) {
        this.objectMapper = objectMapper;
        this.keyId = required(keyId, "Razorpay key ID");
        this.keySecret = required(keySecret, "Razorpay key secret");
        this.webhookSecret = required(webhookSecret, "Razorpay webhook secret");
        this.apiBaseUrl = required(apiBaseUrl, "Razorpay API base URL").replaceAll("/+$", "");
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    @Override public String providerName() { return "RAZORPAY"; }
    @Override public String publicKeyId() { return keyId; }

    @Override
    @SuppressWarnings("unchecked")
    public ProviderOrder createOrder(String receipt, long amountPaise, String currency) {
        if (amountPaise < 1) throw new IllegalArgumentException("Payment amount must be positive");
        if (receipt == null || receipt.isBlank() || receipt.length() > 40) {
            throw new IllegalArgumentException("Payment receipt must be 1–40 characters");
        }
        if (!"INR".equals(currency)) throw new IllegalArgumentException("Razorpay checkout currently supports INR only");
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "amount", amountPaise,
                    "currency", currency,
                    "receipt", receipt,
                    "notes", Map.of("mealminutes_payment_id", receipt)));
            String credentials = Base64.getEncoder().encodeToString(
                    (keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(URI.create(apiBaseUrl + "/orders"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Basic " + credentials)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new PaymentGatewayException("Razorpay order creation returned HTTP " + response.statusCode());
            }
            Map<String, Object> payload = objectMapper.readValue(response.body(), Map.class);
            Object id = payload.get("id");
            Object amount = payload.get("amount");
            Object responseCurrency = payload.get("currency");
            if (!(id instanceof String providerOrderId) || providerOrderId.isBlank()
                    || !(amount instanceof Number providerAmount) || !(responseCurrency instanceof String providerCurrency)) {
                throw new PaymentGatewayException("Razorpay response is missing required order fields");
            }
            if (providerAmount.longValue() != amountPaise || !currency.equals(providerCurrency)) {
                throw new PaymentGatewayException("Razorpay response amount or currency did not match the request");
            }
            return new ProviderOrder(providerOrderId, providerAmount.longValue(), providerCurrency);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new PaymentGatewayException("Razorpay request was interrupted", interrupted);
        } catch (PaymentGatewayException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new PaymentGatewayException("Razorpay response could not be processed", failure);
        }
    }

    @Override
    public boolean verifyWebhookSignature(String rawBody, String signature) {
        return PaymentWebhookSignatureVerifier.verifyHmacSha256(rawBody, signature, webhookSecret);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must be configured when payments are enabled");
        return value.strip();
    }
}
