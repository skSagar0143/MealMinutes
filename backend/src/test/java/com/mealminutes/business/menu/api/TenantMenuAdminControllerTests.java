package com.mealminutes.business.menu.api;

import com.mealminutes.business.menu.service.TenantMenuManagementService;
import com.mealminutes.platform.identity.service.TenantMembershipAuthorizationService;
import com.mealminutes.platform.tenant.domain.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TenantMenuAdminControllerTests {
    @AfterEach void clearTenant() { TenantContext.clear(); }

    @Test void createsCategoryForAuthorizedTenantMember() {
        TenantMenuManagementService menu = mock(TenantMenuManagementService.class);
        TenantMembershipAuthorizationService auth = mock(TenantMembershipAuthorizationService.class);
        var response = new MenuCategoryResponse("category-1", "Coffee", "coffee", null, 0);
        when(menu.createCategory(any(), eq("user-1"))).thenReturn(response);
        var controller = new TenantMenuAdminController(menu, auth);
        try (var ignored = TenantContext.bind(tenant())) {
            var result = controller.createCategory(
                    new TenantMenuAdminController.CreateCategoryRequest("Coffee", null, null, 0), jwt());
            assertThat(result.slug()).isEqualTo("coffee");
        }
        verify(auth).requireAnyRole(eq("tenant-1"), eq("user-1"), anySet());
        verify(menu).createCategory(any(), eq("user-1"));
    }

    @Test void rejectsMenuWriteWhenMembershipAuthorizationFails() {
        TenantMenuManagementService menu = mock(TenantMenuManagementService.class);
        TenantMembershipAuthorizationService auth = mock(TenantMembershipAuthorizationService.class);
        doThrow(new AccessDeniedException("no membership")).when(auth).requireAnyRole(anyString(), anyString(), anySet());
        var controller = new TenantMenuAdminController(menu, auth);
        try (var ignored = TenantContext.bind(tenant())) {
            assertThatThrownBy(() -> controller.setItemAvailability("item-1",
                    new TenantMenuAdminController.AvailabilityRequest(true), jwt()))
                    .isInstanceOf(AccessDeniedException.class);
        }
        verifyNoInteractions(menu);
    }

    @Test void listsItemsForAuthenticatedStaff() {
        TenantMenuManagementService menu = mock(TenantMenuManagementService.class);
        TenantMembershipAuthorizationService auth = mock(TenantMembershipAuthorizationService.class);
        when(menu.listItems("category-1", false, false)).thenReturn(List.of());
        var controller = new TenantMenuAdminController(menu, auth);
        try (var ignored = TenantContext.bind(tenant())) {
            assertThat(controller.items("category-1", false, false, jwt())).isEmpty();
        }
        verify(auth).requireAnyRole(eq("tenant-1"), eq("user-1"), anySet());
    }

    private Jwt jwt() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("access-token").header("alg", "RS256").subject("user-1")
                .issuedAt(now).expiresAt(now.plusSeconds(300)).build();
    }
    private TenantContext.TenantDescriptor tenant() {
        return new TenantContext.TenantDescriptor("tenant-1", "abc-food", "tenant_abc_food", "secret://abc");
    }
}