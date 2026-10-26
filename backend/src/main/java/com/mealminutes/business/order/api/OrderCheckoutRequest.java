package com.mealminutes.business.order.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OrderCheckoutRequest(
        @NotBlank @Size(max = 36) String cartId,
        @NotBlank @Size(max = 36) String branchId,
        @NotNull FulfillmentType fulfillmentType,
        @Size(max = 36) String deliveryAddressId,
        @Size(max = 1000) String customerNote) {
    public enum FulfillmentType { PICKUP, DELIVERY }
}
