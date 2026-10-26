package com.mealminutes.business.order.api;

import com.mealminutes.business.order.service.OrderCheckoutService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customer/orders")
@ConditionalOnExpression("${mealminutes.tenant-provisioning.enabled:false} and ${mealminutes.order-pricing.enabled:false}")
@PreAuthorize("hasRole('CUSTOMER')")
public class OrderController {
    private final OrderCheckoutService orders;
    public OrderController(OrderCheckoutService orders) { this.orders = orders; }

    @PostMapping
    public OrderResponse create(@AuthenticationPrincipal Jwt jwt,
                                @RequestHeader("Idempotency-Key") String idempotencyKey,
                                @Valid @RequestBody OrderCheckoutRequest request) {
        return orders.createOrder(jwt.getSubject(), idempotencyKey, request);
    }

    @GetMapping("/{orderId}")
    public OrderResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable String orderId) {
        return orders.getOrder(jwt.getSubject(), orderId);
    }

    @GetMapping
    public List<OrderResponse> list(@AuthenticationPrincipal Jwt jwt,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return orders.listOrders(jwt.getSubject(), page, size);
    }
}
