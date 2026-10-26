package com.mealminutes.business.cart.api;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(String cartId, String branchId, String status,
                           List<CartItemResponse> items, BigDecimal subtotal) {
    public CartResponse { items = List.copyOf(items); }
    public record CartItemResponse(String cartItemId, String itemId, String name, int quantity,
                                   BigDecimal unitPrice, String currency, BigDecimal lineTotal,
                                   String notes) { }
}
