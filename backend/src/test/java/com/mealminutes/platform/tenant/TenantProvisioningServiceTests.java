package com.mealminutes.platform.tenant;

import com.mealminutes.platform.tenant.domain.DomainVerificationStatus;
import com.mealminutes.platform.tenant.domain.TenantDomain;
import com.mealminutes.platform.tenant.domain.TenantLifecycleStatus;
import com.mealminutes.platform.tenant.domain.TenantRegistryEntry;
import com.mealminutes.platform.tenant.persistence.TenantDomainRepository;
import com.mealminutes.platform.tenant.persistence.TenantRegistryRepository;
import com.mealminutes.platform.tenant.service.TenantDatabaseProvisioner;
import com.mealminutes.platform.tenant.service.TenantProvisioningService;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TenantProvisioningServiceTests {
    @Test
    void provisionsOnlyAfterVerifiedDomainAndPersistsActiveState() {
        TenantRegistryRepository tenants = mock(TenantRegistryRepository.class);
        TenantDomainRepository domains = mock(TenantDomainRepository.class);
        AtomicReference<String> provisionedSlug = new AtomicReference<>();
        TenantDatabaseProvisioner provisioner = slug -> {
            provisionedSlug.set(slug);
            return new TenantDatabaseProvisioner.ProvisionedDatabase("tenant_abc_food", "secret://abc", 1);
        };
        TenantRegistryEntry tenant = approvedTenant();
        when(tenants.findById("tenant-id")).thenReturn(Optional.of(tenant));
        when(domains.findFirstByTenant_TenantIdAndVerificationStatus(
                "tenant-id", DomainVerificationStatus.VERIFIED))
                .thenReturn(Optional.of(new TenantDomain(tenant, "abc.example.com") {{
                    markVerified();
                }}));
        when(tenants.saveAndFlush(any(TenantRegistryEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TenantRegistryEntry result = new TenantProvisioningService(tenants, domains, provisioner)
                .provision("tenant-id");

        assertThat(result.getLifecycleStatus()).isEqualTo(TenantLifecycleStatus.ACTIVE);
        assertThat(result.getTenantDatabaseName()).isEqualTo("tenant_abc_food");
        assertThat(result.getDatabaseSecretReference()).isEqualTo("secret://abc");
        assertThat(provisionedSlug.get()).isEqualTo("abc-food");
        verify(tenants, times(2)).saveAndFlush(any(TenantRegistryEntry.class));
    }

    @Test
    void rejectsProvisioningWithoutVerifiedDomain() {
        TenantRegistryRepository tenants = mock(TenantRegistryRepository.class);
        TenantDomainRepository domains = mock(TenantDomainRepository.class);
        TenantRegistryEntry tenant = approvedTenant();
        when(tenants.findById("tenant-id")).thenReturn(Optional.of(tenant));
        when(domains.findFirstByTenant_TenantIdAndVerificationStatus(
                "tenant-id", DomainVerificationStatus.VERIFIED)).thenReturn(Optional.empty());

        TenantProvisioningService service = new TenantProvisioningService(tenants, domains,
                slug -> new TenantDatabaseProvisioner.ProvisionedDatabase("tenant_abc_food", "secret://abc", 1));

        assertThatThrownBy(() -> service.provision("tenant-id"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("verified domain");
        verify(tenants, never()).saveAndFlush(any());
    }

    @Test
    void recordsFailureAndAllowsSafeRetryState() {
        TenantRegistryRepository tenants = mock(TenantRegistryRepository.class);
        TenantDomainRepository domains = mock(TenantDomainRepository.class);
        TenantRegistryEntry tenant = approvedTenant();
        when(tenants.findById("tenant-id")).thenReturn(Optional.of(tenant));
        when(domains.findFirstByTenant_TenantIdAndVerificationStatus(
                "tenant-id", DomainVerificationStatus.VERIFIED))
                .thenReturn(Optional.of(new TenantDomain(tenant, "abc.example.com")));
        when(tenants.saveAndFlush(any(TenantRegistryEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TenantProvisioningService service = new TenantProvisioningService(tenants, domains,
                slug -> { throw new IllegalStateException("database unavailable"); });

        assertThatThrownBy(() -> service.provision("tenant-id"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
        assertThat(tenant.getLifecycleStatus()).isEqualTo(TenantLifecycleStatus.FAILED);
        assertThat(tenant.getProvisioningStatus().name()).isEqualTo("FAILED");
        verify(tenants, times(2)).saveAndFlush(any(TenantRegistryEntry.class));
    }

    private TenantRegistryEntry approvedTenant() {
        TenantRegistryEntry tenant = new TenantRegistryEntry("abc-food");
        tenant.transitionTo(TenantLifecycleStatus.PENDING_VERIFICATION);
        tenant.transitionTo(TenantLifecycleStatus.APPROVED);
        return tenant;
    }
}