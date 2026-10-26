package com.mealminutes.business.order.api;

import com.mealminutes.business.order.service.OrderLifecycleService;
import com.mealminutes.platform.identity.domain.TenantMembershipRole;
import com.mealminutes.platform.identity.service.TenantMembershipAuthorizationService;
import com.mealminutes.platform.tenant.domain.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/customer/orders")
@ConditionalOnExpression("${mealminutes.tenant-provisioning.enabled:false} and ${mealminutes.order-pricing.enabled:false}")
public class CustomerOrderCancellationController {
    private final OrderLifecycleService orders;
    private final TenantMembershipAuthorizationService memberships;

    public CustomerOrderCancellationController(OrderLifecycleService orders,
                                               TenantMembershipAuthorizationService memberships) {
        this.orders = orders;
        this.memberships = memberships;
    }

    @PostMapping("/{orderId}/cancel")
    public OrderResponse cancel(@PathVariable String orderId,
                                @Valid @RequestBody(required = false) CancelOrderRequest request,
                                @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new org.springframework.security.access.AccessDeniedException("Authentication is required");
        }
        memberships.requireAnyRole(TenantContext.requireCurrent().tenantId(), jwt.getSubject(),
                Set.of(TenantMembershipRole.CUSTOMER));
        String note = request == null ? null : request.reason();
        return orders.cancelUnpaidCustomerOrder(jwt.getSubject(), orderId, note);
    }

    public record CancelOrderRequest(@Size(max = 500) String reason) { }
}
