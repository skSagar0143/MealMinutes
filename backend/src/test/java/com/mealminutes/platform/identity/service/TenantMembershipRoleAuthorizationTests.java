package com.mealminutes.platform.identity.service;

import com.mealminutes.platform.identity.domain.*;
import com.mealminutes.platform.identity.persistence.TenantMembershipRepository;
import com.mealminutes.platform.tenant.domain.TenantRegistryEntry;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TenantMembershipRoleAuthorizationTests {
    @Test void permitsOnlyAllowedActiveTenantRoles() {
        TenantMembershipRepository repo = mock(TenantMembershipRepository.class);
        var user = new PlatformUserAccount("owner@example.com", "$2a$12$hash", PlatformRole.CUSTOMER);
        var membership = new TenantMembership(new TenantRegistryEntry("abc-food"), user, TenantMembershipRole.OWNER);
        when(repo.findByTenant_TenantIdAndUser_UserIdAndMembershipStatus("tenant-1", "user-1", MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(membership));
        new TenantMembershipAuthorizationService(repo).requireAnyRole(
                "tenant-1", "user-1", Set.of(TenantMembershipRole.OWNER, TenantMembershipRole.MANAGER));
    }

    @Test void rejectsStaffEscalationAndMissingIdentity() {
        TenantMembershipRepository repo = mock(TenantMembershipRepository.class);
        var user = new PlatformUserAccount("staff@example.com", "$2a$12$hash", PlatformRole.CUSTOMER);
        var membership = new TenantMembership(new TenantRegistryEntry("abc-food"), user, TenantMembershipRole.STAFF);
        when(repo.findByTenant_TenantIdAndUser_UserIdAndMembershipStatus("tenant-1", "user-1", MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(membership));
        var service = new TenantMembershipAuthorizationService(repo);
        assertThatThrownBy(() -> service.requireAnyRole("tenant-1", "user-1", Set.of(TenantMembershipRole.OWNER)))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> service.requireAnyRole(null, "user-1", Set.of(TenantMembershipRole.OWNER)))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
}