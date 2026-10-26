package com.mealminutes.business.payment.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RazorpayPaymentGatewayTests {
    @Test void createsOrderWithBasicAuthenticationAndValidatesProviderResponse() throws Exception {
        AtomicReference<String> authorization=new AtomicReference<>();
        AtomicReference<String> requestBody=new AtomicReference<>();
        HttpServer server=server(200,"{\"id\":\"order_rzp_123\",\"amount\":1100,\"currency\":\"INR\"}",authorization,requestBody);
        try {
            RazorpayPaymentGateway gateway=gateway(server);
            var order=gateway.createOrder("payment-123",1100,"INR");
            assertThat(order.orderId()).isEqualTo("order_rzp_123");
            assertThat(order.amountPaise()).isEqualTo(1100);
            assertThat(order.currency()).isEqualTo("INR");
            assertThat(authorization.get()).startsWith("Basic ");
            assertThat(requestBody.get()).contains("\"amount\":1100","\"receipt\":\"payment-123\"");
            assertThat(gateway.providerName()).isEqualTo("RAZORPAY");
            assertThat(gateway.publicKeyId()).isEqualTo("test-key");
        } finally { server.stop(0); }
    }

    @Test void rejectsProviderErrorsAndMismatchedResponse() throws Exception {
        HttpServer failed=server(500,"{}",new AtomicReference<>(),new AtomicReference<>());
        try {
            assertThatThrownBy(()->gateway(failed).createOrder("payment-123",1100,"INR"))
                    .isInstanceOf(PaymentGateway.PaymentGatewayException.class);
        } finally { failed.stop(0); }
        HttpServer mismatch=server(200,"{\"id\":\"order_rzp_123\",\"amount\":999,\"currency\":\"INR\"}",
                new AtomicReference<>(),new AtomicReference<>());
        try {
            assertThatThrownBy(()->gateway(mismatch).createOrder("payment-123",1100,"INR"))
                    .isInstanceOf(PaymentGateway.PaymentGatewayException.class);
        } finally { mismatch.stop(0); }
    }

    @Test void validatesRequestAndWebhookSignature() throws Exception {
        HttpServer server=server(200,"{\"id\":\"order_rzp_123\",\"amount\":1100,\"currency\":\"INR\"}",
                new AtomicReference<>(),new AtomicReference<>());
        try {
            RazorpayPaymentGateway gateway=gateway(server);
            assertThatThrownBy(()->gateway.createOrder(" ",1100,"INR")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(()->gateway.createOrder("payment-123",0,"INR")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(()->gateway.createOrder("payment-123",1100,"USD")).isInstanceOf(IllegalArgumentException.class);
            assertThat(gateway.verifyWebhookSignature("{\"event\":\"x\"}", "bad-signature")).isFalse();
        } finally { server.stop(0); }
    }

    private RazorpayPaymentGateway gateway(HttpServer server) {
        return new RazorpayPaymentGateway(new ObjectMapper(),"test-key","test-secret","webhook-secret",
                "http://127.0.0.1:"+server.getAddress().getPort()+"/v1");
    }

    private HttpServer server(int status,String body,AtomicReference<String> authorization,
                              AtomicReference<String> requestBody) throws IOException {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/orders",exchange->{
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(status,bytes.length);
            try(var output=exchange.getResponseBody()){output.write(bytes);}
        });
        server.start();
        return server;
    }
}