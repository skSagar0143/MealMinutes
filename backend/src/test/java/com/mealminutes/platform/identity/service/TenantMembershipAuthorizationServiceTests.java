package com.mealminutes.platform.identity.service;

import com.mealminutes.platform.identity.domain.MembershipStatus;
import com.mealminutes.platform.identity.persistence.TenantMembershipRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TenantMembershipAuthorizationServiceTests {
    @Test void allowsOnlyActiveMembership() {
        TenantMembershipRepository repo = mock(TenantMembershipRepository.class);
        when(repo.existsByTenant_TenantIdAndUser_UserIdAndMembershipStatus("t1", "u1", MembershipStatus.ACTIVE))
                .thenReturn(true);
        var service = new TenantMembershipAuthorizationService(repo);
        assertThat(service.hasActiveMembership("t1", "u1")).isTrue();
        service.requireActiveMembership("t1", "u1");
        verify(repo, times(2)).existsByTenant_TenantIdAndUser_UserIdAndMembershipStatus("t1", "u1", MembershipStatus.ACTIVE);
    }
    @Test void deniesMissingOrInactiveMembership() {
        TenantMembershipRepository repo = mock(TenantMembershipRepository.class);
        when(repo.existsByTenant_TenantIdAndUser_UserIdAndMembershipStatus("t1", "u2", MembershipStatus.ACTIVE))
                .thenReturn(false);
        var service = new TenantMembershipAuthorizationService(repo);
        assertThat(service.hasActiveMembership(null, "u2")).isFalse();
        assertThatThrownBy(() -> service.requireActiveMembership("t1", "u2")).isInstanceOf(AccessDeniedException.class);
        verify(repo).existsByTenant_TenantIdAndUser_UserIdAndMembershipStatus("t1", "u2", MembershipStatus.ACTIVE);
    }
}