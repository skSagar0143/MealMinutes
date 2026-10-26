package com.mealminutes.business.order.service;

import com.mealminutes.business.order.api.OrderCheckoutRequest;
import com.mealminutes.business.order.api.OrderResponse;
import com.mealminutes.platform.tenant.domain.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"unchecked","rawtypes"})
class OrderCheckoutServiceTests {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final OrderCheckoutService service = new OrderCheckoutService(
            jdbc, new ObjectMapper(), new BigDecimal("0.10"), new BigDecimal("5.00"));

    @AfterEach void clearTenant() { TenantContext.clear(); }

    @Test void createsPickupOrderWithServerCalculatedTotalsAndOutboxEvent() {
        stubNoExisting();
        stubBranchCartAndLines(new OrderCheckoutService.OrderLine("item-1",2,null,"Tea","Fresh tea",
                new BigDecimal("10.00"),"INR",true));
        when(jdbc.update(anyString(),any(Object[].class))).thenReturn(1);
        OrderResponse response;
        try (var ignored = TenantContext.bind(tenant())) {
            response = service.createOrder("customer-1","order-key-123",pickupRequest());
        }
        assertThat(response.status()).isEqualTo("PAYMENT_PENDING");
        assertThat(response.paymentStatus()).isEqualTo("PENDING");
        assertThat(response.subtotal()).isEqualByComparingTo("20.00");
        assertThat(response.taxTotal()).isEqualByComparingTo("2.00");
        assertThat(response.deliveryFee()).isEqualByComparingTo("0.00");
        assertThat(response.grandTotal()).isEqualByComparingTo("22.00");
        verify(jdbc).update(contains("INSERT INTO customer_order(order_id"),any(Object[].class));
        verify(jdbc).update(contains("INSERT INTO customer_order_item"),any(Object[].class));
        verify(jdbc).update(contains("UPDATE customer_cart"),any(Object[].class));
        verify(jdbc).update(contains("INSERT INTO outbox_event"),any(Object[].class));
    }

    @Test void createsDeliveryOrderWithOwnedAddressSnapshotAndDeliveryFee() {
        stubNoExisting();
        stubBranchCartAndLines(new OrderCheckoutService.OrderLine("item-1",1,null,"Tea",null,
                new BigDecimal("10.00"),"INR",true));
        when(jdbc.query(contains("FROM customer_address"),any(RowMapper.class),eq("address-1"),eq("customer-1")))
                .thenReturn(List.of(new OrderCheckoutService.AddressSnapshot("Home","Customer","9999999999",
                        "12 Main Road",null,"Delhi","Delhi","110001","IN","Ring bell")));
        when(jdbc.update(anyString(),any(Object[].class))).thenReturn(1);
        var request = new OrderCheckoutRequest("cart-1","branch-1",
                OrderCheckoutRequest.FulfillmentType.DELIVERY,"address-1","Ring bell");
        try (var ignored = TenantContext.bind(tenant())) {
            var response = service.createOrder("customer-1","order-key-123",request);
            assertThat(response.deliveryFee()).isEqualByComparingTo("5.00");
            assertThat(response.grandTotal()).isEqualByComparingTo("16.00");
        }
    }

    @Test void replaysSameIdempotencyKeyButRejectsDifferentRequestFingerprint() {
        var request = pickupRequest();
        var response = sampleOrder();
        when(jdbc.query(contains("FROM customer_order WHERE customer_user_id = ? AND idempotency_key = ?"),
                any(RowMapper.class),eq("customer-1"),eq("order-key-123")))
                .thenReturn(List.of(new OrderCheckoutService.StoredOrder(
                        OrderCheckoutService.fingerprint("customer-1",request),response)));
        assertThat(service.createOrder("customer-1","order-key-123",request)).isEqualTo(response);
        reset(jdbc);
        when(jdbc.query(contains("FROM customer_order WHERE customer_user_id = ? AND idempotency_key = ?"),
                any(RowMapper.class),eq("customer-1"),eq("order-key-123")))
                .thenReturn(List.of(new OrderCheckoutService.StoredOrder("different",response)));
        assertThatThrownBy(()->service.createOrder("customer-1","order-key-123",request))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test void rejectsInvalidIdempotencyKeyAndPricingConfiguration() {
        assertThatThrownBy(()->service.createOrder("customer-1","bad",pickupRequest()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new OrderCheckoutService(jdbc,new ObjectMapper(),new BigDecimal("1.1"),BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new OrderCheckoutService(jdbc,new ObjectMapper(),BigDecimal.ZERO,new BigDecimal("-1.00")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbc);
    }

    @Test void rejectsMissingBranchCartAndEmptyCart() {
        stubNoExisting();
        when(jdbc.query(contains("FROM restaurant_branch"),any(RowMapper.class),eq("branch-1"))).thenReturn(List.of());
        assertThatThrownBy(()->service.createOrder("customer-1","order-key-123",pickupRequest()))
                .isInstanceOf(ResponseStatusException.class);
        reset(jdbc);
        stubNoExisting();
        when(jdbc.query(contains("FROM restaurant_branch"),any(RowMapper.class),eq("branch-1")))
                .thenReturn(List.of(new OrderCheckoutService.BranchCapabilities(true,true)));
        when(jdbc.query(contains("FROM customer_cart"),any(RowMapper.class),eq("cart-1"),eq("customer-1"),eq("branch-1")))
                .thenReturn(List.of());
        assertThatThrownBy(()->service.createOrder("customer-1","order-key-123",pickupRequest()))
                .isInstanceOf(ResponseStatusException.class);
        reset(jdbc);
        stubNoExisting();
        when(jdbc.query(contains("FROM restaurant_branch"),any(RowMapper.class),eq("branch-1")))
                .thenReturn(List.of(new OrderCheckoutService.BranchCapabilities(true,true)));
        when(jdbc.query(contains("FROM customer_cart"),any(RowMapper.class),eq("cart-1"),eq("customer-1"),eq("branch-1")))
                .thenReturn(List.of("cart-1"));
        when(jdbc.query(contains("FROM customer_cart_item ci"),any(RowMapper.class),eq("cart-1"))).thenReturn(List.of());
        assertThatThrownBy(()->service.createOrder("customer-1","order-key-123",pickupRequest()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test void rejectsUnavailableAndMixedCurrencyCartItems() {
        stubNoExisting();
        stubBranchCartAndLines(new OrderCheckoutService.OrderLine("item-1",1,null,"Tea",null,
                new BigDecimal("10.00"),"INR",false));
        assertThatThrownBy(()->service.createOrder("customer-1","order-key-123",pickupRequest()))
                .isInstanceOf(ResponseStatusException.class);
        reset(jdbc);
        stubNoExisting();
        when(jdbc.query(contains("FROM restaurant_branch"),any(RowMapper.class),eq("branch-1")))
                .thenReturn(List.of(new OrderCheckoutService.BranchCapabilities(true,true)));
        when(jdbc.query(contains("FROM customer_cart"),any(RowMapper.class),eq("cart-1"),eq("customer-1"),eq("branch-1")))
                .thenReturn(List.of("cart-1"));
        when(jdbc.query(contains("FROM customer_cart_item ci"),any(RowMapper.class),eq("cart-1")))
                .thenReturn(List.of(
                        new OrderCheckoutService.OrderLine("item-1",1,null,"Tea",null,new BigDecimal("10.00"),"INR",true),
                        new OrderCheckoutService.OrderLine("item-2",1,null,"Coffee",null,new BigDecimal("20.00"),"USD",true)));
        assertThatThrownBy(()->service.createOrder("customer-1","order-key-123",pickupRequest()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test void getsOrdersAndValidatesPagination() {
        when(jdbc.query(contains("WHERE customer_user_id = ? AND order_id = ?"),any(RowMapper.class),eq("customer-1"),eq("order-1")))
                .thenReturn(List.of(sampleOrder()));
        assertThat(service.getOrder("customer-1","order-1").orderId()).isEqualTo("order-1");
        when(jdbc.query(contains("ORDER BY created_at DESC"),any(RowMapper.class),eq("customer-1"),eq(20),eq(0)))
                .thenReturn(List.of(sampleOrder()));
        assertThat(service.listOrders("customer-1",0,20)).hasSize(1);
        assertThatThrownBy(()->service.getOrder("customer-1","missing")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.listOrders("customer-1",-1,20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.listOrders("customer-1",0,101)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.listOrders("customer-1",Integer.MAX_VALUE,100)).isInstanceOf(IllegalArgumentException.class);
    }

    private void stubNoExisting() {
        when(jdbc.query(contains("FROM customer_order WHERE customer_user_id = ? AND idempotency_key = ?"),
                any(RowMapper.class),eq("customer-1"),eq("order-key-123"))).thenReturn(List.of());
    }
    private void stubBranchCartAndLines(OrderCheckoutService.OrderLine line) {
        when(jdbc.query(contains("FROM restaurant_branch"),any(RowMapper.class),eq("branch-1")))
                .thenReturn(List.of(new OrderCheckoutService.BranchCapabilities(true,true)));
        when(jdbc.query(contains("FROM customer_cart"),any(RowMapper.class),eq("cart-1"),eq("customer-1"),eq("branch-1")))
                .thenReturn(List.of("cart-1"));
        when(jdbc.query(contains("FROM customer_cart_item ci"),any(RowMapper.class),eq("cart-1"))).thenReturn(List.of(line));
    }
    private OrderCheckoutRequest pickupRequest() {
        return new OrderCheckoutRequest("cart-1","branch-1",OrderCheckoutRequest.FulfillmentType.PICKUP,null," note ");
    }
    private OrderResponse sampleOrder() {
        return new OrderResponse("order-1","MM202610261234567890123456","PAYMENT_PENDING","PENDING","PICKUP",
                "INR",new BigDecimal("10.00"),new BigDecimal("1.00"),BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("11.00"));
    }
    private TenantContext.TenantDescriptor tenant() {
        return new TenantContext.TenantDescriptor("tenant-1","abc-food","tenant_abc_food","secret://db");
    }
}
