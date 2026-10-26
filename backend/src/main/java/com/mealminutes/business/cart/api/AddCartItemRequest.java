package com.mealminutes.business.cart.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddCartItemRequest(
        @NotBlank @Size(max = 36) String branchId,
        @NotBlank @Size(max = 36) String itemId,
        @Min(1) @Max(99) int quantity,
        @Size(max = 500) String notes) { }
