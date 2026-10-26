package com.mealminutes.business.profile.api;

import com.mealminutes.business.menu.api.RestaurantProfileResponse;
import com.mealminutes.business.profile.service.RestaurantProfileService;
import com.mealminutes.platform.identity.service.TenantMembershipAuthorizationService;
import com.mealminutes.platform.tenant.domain.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RestaurantProfileControllerTests {
    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void returnsProfileForAuthorizedTenantMember() {
        RestaurantProfileService profiles = mock(RestaurantProfileService.class);
        TenantMembershipAuthorizationService memberships = mock(TenantMembershipAuthorizationService.class);
        var expected = new RestaurantProfileResponse("profile-1", "Local Kitchen", null, null,
                null, null, "INR", "Asia/Kolkata");
        when(profiles.getProfile()).thenReturn(Optional.of(expected));
        var controller = new RestaurantProfileController(profiles, memberships);

        try (TenantContext.Scope ignored = TenantContext.bind(tenant())) {
            assertThat(controller.get(jwt())).isEqualTo(expected);
        }

        verify(memberships).requireAnyRole(eq("tenant-1"), eq("user-1"), anySet());
    }

    @Test
    void savesProfileUsingAuthenticatedActor() {
        RestaurantProfileService profiles = mock(RestaurantProfileService.class);
        TenantMembershipAuthorizationService memberships = mock(TenantMembershipAuthorizationService.class);
        var expected = new RestaurantProfileResponse("profile-1", "Local Kitchen", null, null,
                null, null, "INR", "Asia/Kolkata");
        when(profiles.saveProfile(eq("user-1"), any())).thenReturn(expected);
        var controller = new RestaurantProfileController(profiles, memberships);
        var request = new RestaurantProfileController.ProfileRequest("Local Kitchen", null, null,
                null, null, "INR", "Asia/Kolkata");

        try (TenantContext.Scope ignored = TenantContext.bind(tenant())) {
            assertThat(controller.save(request, jwt())).isEqualTo(expected);
        }

        verify(profiles).saveProfile(eq("user-1"), any());
    }

    @Test
    void rejectsProfileWriteWithoutTenantMembership() {
        RestaurantProfileService profiles = mock(RestaurantProfileService.class);
        TenantMembershipAuthorizationService memberships = mock(TenantMembershipAuthorizationService.class);
        doThrow(new AccessDeniedException("no membership"))
                .when(memberships).requireAnyRole(anyString(), anyString(), anySet());
        var controller = new RestaurantProfileController(profiles, memberships);
        var request = new RestaurantProfileController.ProfileRequest("Local Kitchen", null, null,
                null, null, "INR", "Asia/Kolkata");

        try (TenantContext.Scope ignored = TenantContext.bind(tenant())) {
            assertThatThrownBy(() -> controller.save(request, jwt()))
                    .isInstanceOf(AccessDeniedException.class);
        }
        verifyNoInteractions(profiles);
    }

    private static Jwt jwt() {
        return Jwt.withTokenValue("token").header("alg", "RS256")
                .subject("user-1").claim("roles", List.of("OWNER"))
                .issuedAt(java.time.Instant.now()).expiresAt(java.time.Instant.now().plusSeconds(300)).build();
    }

    private static TenantContext.TenantDescriptor tenant() {
        return new TenantContext.TenantDescriptor("tenant-1", "abc-food", "tenant_abc_food", "secret://db");
    }
}
