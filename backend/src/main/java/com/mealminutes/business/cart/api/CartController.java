package com.mealminutes.business.cart.api;

import com.mealminutes.business.cart.service.CartService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customer/cart")
@ConditionalOnProperty(prefix = "mealminutes.tenant-provisioning", name = "enabled", havingValue = "true")
@PreAuthorize("hasRole('CUSTOMER')")
public class CartController {
    private final CartService carts;
    public CartController(CartService carts) { this.carts = carts; }

    @GetMapping
    public CartResponse get(@AuthenticationPrincipal Jwt jwt, @RequestParam String branchId) {
        return carts.getActiveCart(jwt.getSubject(), branchId);
    }

    @PostMapping("/items")
    public CartResponse add(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddCartItemRequest request) {
        return carts.addItem(jwt.getSubject(), request);
    }

    @PutMapping("/{cartId}/items/{itemId}")
    public CartResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable String cartId,
                               @PathVariable String itemId, @RequestParam @Min(1) @Max(99) int quantity) {
        return carts.updateQuantity(jwt.getSubject(), cartId, itemId, quantity);
    }

    @DeleteMapping("/{cartId}/items/{itemId}")
    public CartResponse remove(@AuthenticationPrincipal Jwt jwt, @PathVariable String cartId,
                               @PathVariable String itemId) {
        return carts.removeItem(jwt.getSubject(), cartId, itemId);
    }
}
