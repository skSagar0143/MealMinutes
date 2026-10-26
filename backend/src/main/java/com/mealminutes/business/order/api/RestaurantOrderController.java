package com.mealminutes.business.order.api;

import com.mealminutes.business.order.service.OrderLifecycleService;
import com.mealminutes.platform.identity.domain.TenantMembershipRole;
import com.mealminutes.platform.identity.service.TenantMembershipAuthorizationService;
import com.mealminutes.platform.tenant.domain.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/restaurant/orders")
@ConditionalOnExpression("${mealminutes.tenant-provisioning.enabled:false} and ${mealminutes.order-pricing.enabled:false}")
public class RestaurantOrderController {
    private static final Set<TenantMembershipRole> ORDER_ROLES =
            Set.of(TenantMembershipRole.OWNER, TenantMembershipRole.MANAGER, TenantMembershipRole.STAFF);
    private final OrderLifecycleService orders;
    private final TenantMembershipAuthorizationService memberships;

    public RestaurantOrderController(OrderLifecycleService orders,
                                     TenantMembershipAuthorizationService memberships) {
        this.orders = orders;
        this.memberships = memberships;
    }

    @GetMapping
    public List<OrderResponse> list(@RequestParam String branchId,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @AuthenticationPrincipal Jwt jwt) {
        authorize(jwt);
        return orders.listBranchOrders(branchId, page, size);
    }

    @PatchMapping("/{orderId}/status")
    public OrderResponse transition(@PathVariable String orderId,
                                    @Valid @RequestBody TransitionOrderRequest request,
                                    @AuthenticationPrincipal Jwt jwt) {
        authorize(jwt);
        return orders.transitionRestaurantOrder(jwt.getSubject(), orderId, request.status(), request.note());
    }

    private void authorize(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new org.springframework.security.access.AccessDeniedException("Authentication is required");
        }
        memberships.requireAnyRole(TenantContext.requireCurrent().tenantId(), jwt.getSubject(), ORDER_ROLES);
    }

    public record TransitionOrderRequest(@NotBlank @Size(max = 32) String status,
                                          @Size(max = 500) String note) { }
}
