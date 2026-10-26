package com.mealminutes.business.menu.api;

import com.mealminutes.business.menu.service.TenantMenuQueryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/storefront/menu")
@ConditionalOnBean(TenantMenuQueryService.class)
public class StorefrontMenuController {
    private final TenantMenuQueryService menu;

    public StorefrontMenuController(TenantMenuQueryService menu) {
        this.menu = menu;
    }

    @GetMapping("/restaurant")
    public RestaurantProfileResponse restaurant() {
        return menu.getRestaurantProfile().orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant profile not found"));
    }

    @GetMapping("/categories")
    public List<MenuCategoryResponse> categories() {
        return menu.listCategories();
    }

    @GetMapping("/items")
    public List<MenuItemResponse> items(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return menu.listItems(category, search, page, size);
    }
}
