package com.mealminutes.platform.tenant;

import com.mealminutes.platform.tenant.domain.DomainVerificationStatus;
import com.mealminutes.platform.tenant.domain.TenantContext;
import com.mealminutes.platform.tenant.domain.TenantDomain;
import com.mealminutes.platform.tenant.domain.TenantLifecycleStatus;
import com.mealminutes.platform.tenant.domain.TenantRegistryEntry;
import com.mealminutes.platform.tenant.domain.TenantResolutionException;
import com.mealminutes.platform.tenant.persistence.TenantDomainRepository;
import com.mealminutes.platform.tenant.service.TenantResolverService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantResolverServiceTests {
    @Mock
    private TenantDomainRepository domainRepository;

    @Test
    void resolvesOnlyVerifiedDomainForActiveProvisionedTenant() {
        TenantDomain domain = verifiedDomain(activeTenant());
        when(domainRepository.findByHostnameAndVerificationStatus(
                "abcfood.example.com", DomainVerificationStatus.VERIFIED)).thenReturn(Optional.of(domain));

        TenantContext.TenantDescriptor resolved =
                new TenantResolverService(domainRepository).resolve(" ABCFOOD.Example.com. ");

        assertThat(resolved.tenantId()).isEqualTo(domain.getTenant().getTenantId());
        assertThat(resolved.slug()).isEqualTo("abc-food");
        assertThat(resolved.databaseName()).isEqualTo("tenant_abc_food");
    }

    @Test
    void rejectsUnknownAndMalformedHosts() {
        when(domainRepository.findByHostnameAndVerificationStatus(
                "unknown.example.com", DomainVerificationStatus.VERIFIED)).thenReturn(Optional.empty());
        TenantResolverService resolver = new TenantResolverService(domainRepository);

        assertThatThrownBy(() -> resolver.resolve("unknown.example.com"))
                .isInstanceOf(TenantResolutionException.class);
        assertThatThrownBy(() -> resolver.resolve("abc.example.com:8080"))
                .isInstanceOf(TenantResolutionException.class);
        verify(domainRepository, never()).findByHostnameAndVerificationStatus(
                "abc.example.com:8080", DomainVerificationStatus.VERIFIED);
    }

    @Test
    void rejectsSuspendedTenantEvenWhenDomainIsVerified() {
        TenantRegistryEntry tenant = activeTenant();
        tenant.transitionTo(TenantLifecycleStatus.SUSPENDED);
        TenantDomain domain = verifiedDomain(tenant);
        when(domainRepository.findByHostnameAndVerificationStatus(
                "abcfood.example.com", DomainVerificationStatus.VERIFIED)).thenReturn(Optional.of(domain));

        assertThatThrownBy(() -> new TenantResolverService(domainRepository).resolve("abcfood.example.com"))
                .isInstanceOf(TenantResolutionException.class)
                .hasMessage("Tenant is unavailable");
    }

    @Test
    void rejectsUnprovisionedTenantEvenWhenDomainIsVerified() {
        TenantRegistryEntry tenant = new TenantRegistryEntry("abc-food");
        TenantDomain domain = verifiedDomain(tenant);
        when(domainRepository.findByHostnameAndVerificationStatus(
                "abcfood.example.com", DomainVerificationStatus.VERIFIED)).thenReturn(Optional.of(domain));

        assertThatThrownBy(() -> new TenantResolverService(domainRepository).resolve("abcfood.example.com"))
                .isInstanceOf(TenantResolutionException.class);
    }

    private TenantDomain verifiedDomain(TenantRegistryEntry tenant) {
        TenantDomain domain = new TenantDomain(tenant, "abcfood.example.com");
        domain.markVerified();
        return domain;
    }

    private TenantRegistryEntry activeTenant() {
        TenantRegistryEntry tenant = new TenantRegistryEntry("abc-food");
        tenant.transitionTo(TenantLifecycleStatus.PENDING_VERIFICATION);
        tenant.transitionTo(TenantLifecycleStatus.APPROVED);
        tenant.beginProvisioning();
        tenant.completeProvisioning("tenant_abc_food", "secret://tenants/abc-food/db", 1);
        tenant.transitionTo(TenantLifecycleStatus.ACTIVE);
        return tenant;
    }
}