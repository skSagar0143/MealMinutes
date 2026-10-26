package com.mealminutes.business.branch.api;

import com.mealminutes.business.branch.service.RestaurantBranchService;
import com.mealminutes.platform.identity.domain.TenantMembershipRole;
import com.mealminutes.platform.identity.service.TenantMembershipAuthorizationService;
import com.mealminutes.platform.tenant.domain.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/restaurant/branches")
@ConditionalOnProperty(prefix = "mealminutes.tenant-provisioning", name = "enabled", havingValue = "true")
public class RestaurantBranchController {
    private static final Set<TenantMembershipRole> READ_ROLES =
            Set.of(TenantMembershipRole.OWNER, TenantMembershipRole.MANAGER, TenantMembershipRole.STAFF);
    private static final Set<TenantMembershipRole> WRITE_ROLES =
            Set.of(TenantMembershipRole.OWNER, TenantMembershipRole.MANAGER);
    private final RestaurantBranchService branches;
    private final TenantMembershipAuthorizationService memberships;

    public RestaurantBranchController(RestaurantBranchService branches,
                                      TenantMembershipAuthorizationService memberships) {
        this.branches = branches;
        this.memberships = memberships;
    }

    @GetMapping
    public List<RestaurantBranchService.BranchResponse> list(
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @AuthenticationPrincipal Jwt jwt) {
        authorize(jwt, READ_ROLES);
        return branches.listBranches(includeInactive);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RestaurantBranchService.BranchResponse create(@Valid @RequestBody BranchRequest request,
                                                         @AuthenticationPrincipal Jwt jwt) {
        authorize(jwt, WRITE_ROLES);
        return branches.createBranch(request.toCommand());
    }

    @PutMapping("/{branchId}")
    public RestaurantBranchService.BranchResponse update(@PathVariable String branchId,
                                                         @Valid @RequestBody BranchRequest request,
                                                         @AuthenticationPrincipal Jwt jwt) {
        authorize(jwt, WRITE_ROLES);
        return branches.updateBranch(branchId, request.toCommand());
    }

    @PatchMapping("/{branchId}/active")
    public RestaurantBranchService.BranchResponse setActive(@PathVariable String branchId,
                                                            @Valid @RequestBody ActiveRequest request,
                                                            @AuthenticationPrincipal Jwt jwt) {
        authorize(jwt, WRITE_ROLES);
        return branches.setBranchActive(branchId, request.active());
    }

    @GetMapping("/{branchId}/operating-hours")
    public List<RestaurantBranchService.OperatingHoursResponse> operatingHours(
            @PathVariable String branchId, @AuthenticationPrincipal Jwt jwt) {
        authorize(jwt, READ_ROLES);
        return branches.listOperatingHours(branchId, false);
    }

    @PutMapping("/{branchId}/operating-hours")
    public List<RestaurantBranchService.OperatingHoursResponse> replaceOperatingHours(
            @PathVariable String branchId, @Valid @RequestBody OperatingHoursRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        authorize(jwt, WRITE_ROLES);
        return branches.replaceOperatingHours(branchId, request.hours().stream()
                .map(OperatingHoursEntry::toCommand).toList());
    }

    private void authorize(Jwt jwt, Set<TenantMembershipRole> roles) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new org.springframework.security.access.AccessDeniedException("Authentication is required");
        }
        memberships.requireAnyRole(TenantContext.requireCurrent().tenantId(), jwt.getSubject(), roles);
    }

    public record BranchRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 80) String slug,
            @NotBlank @Size(max = 255) String addressLine1,
            @Size(max = 255) String addressLine2,
            @NotBlank @Size(max = 120) String city,
            @Size(max = 120) String region,
            @Size(max = 20) String postalCode,
            @NotBlank @Size(min = 2, max = 2) String countryCode,
            @Size(max = 32) String phone,
            BigDecimal latitude,
            BigDecimal longitude,
            boolean pickupEnabled,
            boolean deliveryEnabled) {
        RestaurantBranchService.BranchCommand toCommand() {
            return new RestaurantBranchService.BranchCommand(name, slug, addressLine1, addressLine2,
                    city, region, postalCode, countryCode, phone, latitude, longitude,
                    pickupEnabled, deliveryEnabled);
        }
    }

    public record ActiveRequest(@NotNull Boolean active) { }
    public record OperatingHoursRequest(@NotNull @Size(min = 7, max = 7) List<@Valid OperatingHoursEntry> hours) { }
    public record OperatingHoursEntry(int dayOfWeek, @Size(max = 16) String opensAt,
                                      @Size(max = 16) String closesAt, boolean closed) {
        RestaurantBranchService.OperatingHoursCommand toCommand() {
            return new RestaurantBranchService.OperatingHoursCommand(dayOfWeek, opensAt, closesAt, closed);
        }
    }
}
