package com.mealminutes.business.profile.api;

import com.mealminutes.business.menu.api.RestaurantProfileResponse;
import com.mealminutes.business.profile.service.RestaurantProfileService;
import com.mealminutes.platform.identity.domain.TenantMembershipRole;
import com.mealminutes.platform.identity.service.TenantMembershipAuthorizationService;
import com.mealminutes.platform.tenant.domain.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/restaurant/profile")
@ConditionalOnProperty(prefix = "mealminutes.tenant-provisioning", name = "enabled", havingValue = "true")
public class RestaurantProfileController {
    private static final Set<TenantMembershipRole> READ_ROLES =
            Set.of(TenantMembershipRole.OWNER, TenantMembershipRole.MANAGER, TenantMembershipRole.STAFF);
    private static final Set<TenantMembershipRole> WRITE_ROLES =
            Set.of(TenantMembershipRole.OWNER, TenantMembershipRole.MANAGER);
    private final RestaurantProfileService profiles;
    private final TenantMembershipAuthorizationService memberships;

    public RestaurantProfileController(RestaurantProfileService profiles,
                                       TenantMembershipAuthorizationService memberships) {
        this.profiles = profiles;
        this.memberships = memberships;
    }

    @GetMapping
    public RestaurantProfileResponse get(@AuthenticationPrincipal Jwt jwt) {
        authorize(jwt, READ_ROLES);
        return profiles.getProfile().orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant profile not found"));
    }

    @PutMapping
    public RestaurantProfileResponse save(@Valid @RequestBody ProfileRequest request,
                                          @AuthenticationPrincipal Jwt jwt) {
        authorize(jwt, WRITE_ROLES);
        return profiles.saveProfile(jwt.getSubject(), request.toCommand());
    }

    private void authorize(Jwt jwt, Set<TenantMembershipRole> roles) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new org.springframework.security.access.AccessDeniedException("Authentication is required");
        }
        memberships.requireAnyRole(TenantContext.requireCurrent().tenantId(), jwt.getSubject(), roles);
    }

    public record ProfileRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 2000) String description,
            @Size(max = 2048) String logoUrl,
            @Email @Size(max = 254) String supportEmail,
            @Size(max = 32) String supportPhone,
            @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency,
            @NotBlank @Size(max = 64) String timezone) {
        RestaurantProfileService.ProfileCommand toCommand() {
            return new RestaurantProfileService.ProfileCommand(name, description, logoUrl, supportEmail,
                    supportPhone, currency, timezone);
        }
    }
}
