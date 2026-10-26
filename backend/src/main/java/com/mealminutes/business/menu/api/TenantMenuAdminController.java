package com.mealminutes.business.menu.api;

import com.mealminutes.business.menu.service.TenantMenuManagementService;
import com.mealminutes.platform.identity.domain.TenantMembershipRole;
import com.mealminutes.platform.identity.service.TenantMembershipAuthorizationService;
import com.mealminutes.platform.tenant.domain.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/restaurant/menu")
public class TenantMenuAdminController {
    private static final Set<TenantMembershipRole> READ_ROLES =
            Set.of(TenantMembershipRole.OWNER, TenantMembershipRole.MANAGER, TenantMembershipRole.STAFF);
    private static final Set<TenantMembershipRole> WRITE_ROLES =
            Set.of(TenantMembershipRole.OWNER, TenantMembershipRole.MANAGER);
    private final TenantMenuManagementService menu;
    private final TenantMembershipAuthorizationService memberships;

    public TenantMenuAdminController(TenantMenuManagementService menu,
                                     TenantMembershipAuthorizationService memberships) {
        this.menu = menu;
        this.memberships = memberships;
    }

    @GetMapping("/categories")
    public List<MenuCategoryResponse> categories(@RequestParam(defaultValue = "false") boolean includeInactive,
                                                  @AuthenticationPrincipal Jwt jwt) {
        authorize(jwt, READ_ROLES);
        return menu.listCategories(includeInactive);
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public MenuCategoryResponse createCategory(@Valid @RequestBody CreateCategoryRequest request,
                                                @AuthenticationPrincipal Jwt jwt) {
        authorize(jwt, WRITE_ROLES);
        return menu.createCategory(new TenantMenuManagementService.CreateCategoryCommand(
                request.name(), request.slug(), request.description(), request.sortOrder()), jwt.getSubject());
    }

    @PatchMapping("/categories/{categoryId}/active")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setCategoryActive(@PathVariable String categoryId, @Valid @RequestBody ActiveRequest request,
                                  @AuthenticationPrincipal Jwt jwt) {
        authorize(jwt, WRITE_ROLES);
        menu.setCategoryActive(categoryId, request.active(), jwt.getSubject());
    }

    @GetMapping("/items")
    public List<MenuItemResponse> items(@RequestParam(required = false) String categoryId,
                                        @RequestParam(defaultValue = "false") boolean includeUnavailable,
                                        @RequestParam(defaultValue = "false") boolean includeInactiveCategories,
                                        @AuthenticationPrincipal Jwt jwt) {
        authorize(jwt, READ_ROLES);
        return menu.listItems(categoryId, includeUnavailable, includeInactiveCategories);
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public MenuItemResponse createItem(@Valid @RequestBody CreateMenuItemRequest request,
                                       @AuthenticationPrincipal Jwt jwt) {
        authorize(jwt, WRITE_ROLES);
        return menu.createItem(new TenantMenuManagementService.CreateMenuItemCommand(
                request.categoryId(), request.name(), request.slug(), request.description(), request.price(),
                request.currency(), request.imageUrl(), request.vegetarian(), request.vegan(),
                request.allergens(), request.displayOrder()), jwt.getSubject());
    }

    @PatchMapping("/items/{itemId}/availability")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setItemAvailability(@PathVariable String itemId, @Valid @RequestBody AvailabilityRequest request,
                                    @AuthenticationPrincipal Jwt jwt) {
        authorize(jwt, WRITE_ROLES);
        menu.setItemAvailability(itemId, request.available(), jwt.getSubject());
    }

    private void authorize(Jwt jwt, Set<TenantMembershipRole> roles) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new org.springframework.security.access.AccessDeniedException("Authentication is required");
        }
        memberships.requireAnyRole(TenantContext.requireCurrent().tenantId(), jwt.getSubject(), roles);
    }

    public record CreateCategoryRequest(@NotBlank @Size(max = 120) String name,
                                        @Size(max = 120) String slug,
                                        @Size(max = 500) String description,
                                        @Min(0) int sortOrder) { }
    public record ActiveRequest(@NotNull Boolean active) { }
    public record AvailabilityRequest(@NotNull Boolean available) { }
    public record CreateMenuItemRequest(
            @NotBlank @Size(max = 36) String categoryId,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 160) String slug,
            @Size(max = 2000) String description,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @Pattern(regexp = "[A-Za-z]{3}") String currency,
            @Size(max = 2048) String imageUrl,
            boolean vegetarian,
            boolean vegan,
            @Size(max = 1000) String allergens,
            @Min(0) int displayOrder) { }
}