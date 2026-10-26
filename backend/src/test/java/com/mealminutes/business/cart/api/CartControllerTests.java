package com.mealminutes.business.cart.api;

import com.mealminutes.business.cart.service.CartService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CartControllerTests {
    private final CartService service = mock(CartService.class);
    private final CartController controller = new CartController(service);
    private final Jwt jwt = mock(Jwt.class);

    CartControllerTests() { when(jwt.getSubject()).thenReturn("customer-1"); }

    @Test void readsActiveCartForAuthenticatedSubject() {
        var expected = new CartResponse(null, "branch-1", "ACTIVE", List.of(), BigDecimal.ZERO);
        when(service.getActiveCart("customer-1", "branch-1")).thenReturn(expected);
        assertThat(controller.get(jwt, "branch-1")).isEqualTo(expected);
    }

    @Test void addsItemForAuthenticatedSubject() {
        var request = new AddCartItemRequest("branch-1", "item-1", 2, null);
        var expected = new CartResponse("cart-1", "branch-1", "ACTIVE", List.of(), BigDecimal.ZERO);
        when(service.addItem("customer-1", request)).thenReturn(expected);
        assertThat(controller.add(jwt, request)).isEqualTo(expected);
    }

    @Test void updatesQuantityForAuthenticatedSubject() {
        var expected = new CartResponse("cart-1", "branch-1", "ACTIVE", List.of(), BigDecimal.ZERO);
        when(service.updateQuantity("customer-1", "cart-1", "item-1", 3)).thenReturn(expected);
        assertThat(controller.update(jwt, "cart-1", "item-1", 3)).isEqualTo(expected);
    }

    @Test void removesItemForAuthenticatedSubject() {
        var expected = new CartResponse("cart-1", "branch-1", "ACTIVE", List.of(), BigDecimal.ZERO);
        when(service.removeItem("customer-1", "cart-1", "item-1")).thenReturn(expected);
        assertThat(controller.remove(jwt, "cart-1", "item-1")).isEqualTo(expected);
    }
}