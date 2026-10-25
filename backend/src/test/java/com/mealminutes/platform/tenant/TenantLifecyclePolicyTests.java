package com.mealminutes.platform.tenant;

import com.mealminutes.platform.tenant.domain.ProvisioningStatus;
import com.mealminutes.platform.tenant.domain.TenantLifecyclePolicy;
import com.mealminutes.platform.tenant.domain.TenantLifecycleStatus;
import com.mealminutes.platform.tenant.domain.TenantRegistryEntry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantLifecyclePolicyTests {
    @Test
    void allowsExpectedRegistrationAndProvisioningPath() {
        assertThat(TenantLifecyclePolicy.isAllowed(TenantLifecycleStatus.REGISTERED,
                TenantLifecycleStatus.PENDING_VERIFICATION)).isTrue();
        assertThat(TenantLifecyclePolicy.isAllowed(TenantLifecycleStatus.PENDING_VERIFICATION,
                TenantLifecycleStatus.APPROVED)).isTrue();
        assertThat(TenantLifecyclePolicy.isAllowed(TenantLifecycleStatus.APPROVED,
                TenantLifecycleStatus.PROVISIONING)).isTrue();
        assertThat(TenantLifecyclePolicy.isAllowed(TenantLifecycleStatus.PROVISIONING,
                TenantLifecycleStatus.ACTIVE)).isTrue();
    }

    @Test
    void rejectsUnsafeOrTerminalTransitions() {
        assertThat(TenantLifecyclePolicy.isAllowed(TenantLifecycleStatus.REGISTERED,
                TenantLifecycleStatus.ACTIVE)).isFalse();
        assertThat(TenantLifecyclePolicy.isAllowed(TenantLifecycleStatus.DEPROVISIONING,
                TenantLifecycleStatus.ACTIVE)).isFalse();
        assertThatThrownBy(() -> TenantLifecyclePolicy.requireAllowed(
                TenantLifecycleStatus.REGISTERED, TenantLifecycleStatus.ACTIVE))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void provisioningMustCompleteBeforeActivation() {
        TenantRegistryEntry tenant = approvedTenant();
        tenant.beginProvisioning();
        assertThat(tenant.getProvisioningStatus()).isEqualTo(ProvisioningStatus.IN_PROGRESS);
        assertThatThrownBy(() -> tenant.transitionTo(TenantLifecycleStatus.ACTIVE))
                .isInstanceOf(IllegalStateException.class);
        tenant.completeProvisioning("tenant_abc_food", "secret://tenants/abc-food/db", 1);
        assertThat(tenant.getProvisioningStatus()).isEqualTo(ProvisioningStatus.SUCCEEDED);
        tenant.transitionTo(TenantLifecycleStatus.ACTIVE);
        assertThat(tenant.getLifecycleStatus()).isEqualTo(TenantLifecycleStatus.ACTIVE);
    }

    @Test
    void rejectsUnsafeProvisioningInputsAndAllowsFailureRetryPath() {
        TenantRegistryEntry tenant = approvedTenant();
        tenant.beginProvisioning();
        assertThatThrownBy(() -> tenant.completeProvisioning("bad-name;drop", "secret://tenant/db", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tenant.completeProvisioning("tenant_abc", "plain-password", 0))
                .isInstanceOf(IllegalArgumentException.class);
        tenant.failProvisioning();
        assertThat(tenant.getLifecycleStatus()).isEqualTo(TenantLifecycleStatus.FAILED);
        tenant.beginProvisioning();
        assertThat(tenant.getProvisioningStatus()).isEqualTo(ProvisioningStatus.IN_PROGRESS);
    }

    @Test
    void rejectsInvalidTenantSlugs() {
        assertThatThrownBy(() -> new TenantRegistryEntry("ABC Food"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TenantRegistryEntry("-bad"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new TenantRegistryEntry("valid-slug").getSlug()).isEqualTo("valid-slug");
    }

    private TenantRegistryEntry approvedTenant() {
        TenantRegistryEntry tenant = new TenantRegistryEntry("abc-food");
        tenant.transitionTo(TenantLifecycleStatus.PENDING_VERIFICATION);
        tenant.transitionTo(TenantLifecycleStatus.APPROVED);
        return tenant;
    }
}