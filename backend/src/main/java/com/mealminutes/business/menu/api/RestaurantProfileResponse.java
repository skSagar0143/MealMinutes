package com.mealminutes.business.menu.api;

public record RestaurantProfileResponse(
        String id, String name, String description, String logoUrl,
        String supportEmail, String supportPhone, String currency, String timezone) { }
