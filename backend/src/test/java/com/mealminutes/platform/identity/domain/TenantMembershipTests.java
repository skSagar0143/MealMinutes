package com.mealminutes.platform.identity.domain;

import com.mealminutes.platform.tenant.domain.TenantRegistryEntry;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantMembershipTests {
    @Test void startsActiveAndCanBeSuspendedAndReactivated() {
        var tenant = new TenantRegistryEntry("abc-food");
        var user = new PlatformUserAccount("owner@example.com", "$2a$12$hash", PlatformRole.CUSTOMER);
        var membership = new TenantMembership(tenant, user, TenantMembershipRole.OWNER);
        assertThat(membership.getMembershipStatus()).isEqualTo(MembershipStatus.ACTIVE);
        membership.suspend();
        assertThat(membership.getMembershipStatus()).isEqualTo(MembershipStatus.SUSPENDED);
        membership.reactivate();
        assertThat(membership.getMembershipStatus()).isEqualTo(MembershipStatus.ACTIVE);
    }
    @Test void requiresTenantUserAndRole() {
        var tenant = new TenantRegistryEntry("abc-food");
        var user = new PlatformUserAccount("owner@example.com", "$2a$12$hash", PlatformRole.CUSTOMER);
        assertThatThrownBy(() -> new TenantMembership(null, user, TenantMembershipRole.OWNER)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TenantMembership(tenant, null, TenantMembershipRole.OWNER)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TenantMembership(tenant, user, null)).isInstanceOf(IllegalArgumentException.class);
    }
}