package com.mealminutes.business.order.api;

import com.mealminutes.business.order.service.OrderCheckoutService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OrderControllerTests {
    private final OrderCheckoutService service=mock(OrderCheckoutService.class);
    private final OrderController controller=new OrderController(service);
    private final Jwt jwt=mock(Jwt.class);
    OrderControllerTests(){when(jwt.getSubject()).thenReturn("customer-1");}

    @Test void createsOrderUsingSignedSubjectAndIdempotencyHeader(){
        var request=new OrderCheckoutRequest("cart-1","branch-1",OrderCheckoutRequest.FulfillmentType.PICKUP,null,null);
        var expected=sampleOrder();
        when(service.createOrder("customer-1","order-key-123",request)).thenReturn(expected);
        assertThat(controller.create(jwt,"order-key-123",request)).isEqualTo(expected);
    }

    @Test void readsSingleOrderAndPagedHistory(){
        when(service.getOrder("customer-1","order-1")).thenReturn(sampleOrder());
        when(service.listOrders("customer-1",1,10)).thenReturn(List.of(sampleOrder()));
        assertThat(controller.get(jwt,"order-1")).isEqualTo(sampleOrder());
        assertThat(controller.list(jwt,1,10)).containsExactly(sampleOrder());
    }

    @Test void forwardsDefaultPageParameters(){
        when(service.listOrders("customer-1",0,20)).thenReturn(List.of());
        assertThat(controller.list(jwt,0,20)).isEmpty();
        verify(service).listOrders("customer-1",0,20);
    }

    private OrderResponse sampleOrder(){
        return new OrderResponse("order-1","MM202610261234567890123456","PAYMENT_PENDING","PENDING","PICKUP",
                "INR",new BigDecimal("10.00"),new BigDecimal("1.00"),BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("11.00"));
    }
}