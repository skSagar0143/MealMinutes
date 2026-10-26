package com.mealminutes.business.menu.api;

import java.math.BigDecimal;

public record MenuItemResponse(
        String id, String categoryId, String name, String slug, String description,
        BigDecimal price, String currency, String imageUrl, boolean vegetarian,
        boolean vegan, String allergens, boolean available, int displayOrder) { }
