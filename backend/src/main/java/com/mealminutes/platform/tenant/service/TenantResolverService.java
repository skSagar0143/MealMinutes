package com.mealminutes.platform.tenant.service;

import com.mealminutes.platform.tenant.domain.DomainVerificationStatus;
import com.mealminutes.platform.tenant.domain.HostnameNormalizer;
import com.mealminutes.platform.tenant.domain.ProvisioningStatus;
import com.mealminutes.platform.tenant.domain.TenantContext;
import com.mealminutes.platform.tenant.domain.TenantDomain;
import com.mealminutes.platform.tenant.domain.TenantLifecycleStatus;
import com.mealminutes.platform.tenant.domain.TenantResolutionException;
import com.mealminutes.platform.tenant.persistence.TenantDomainRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantResolverService {
    private final TenantDomainRepository domainRepository;

    public TenantResolverService(TenantDomainRepository domainRepository) {
        this.domainRepository = domainRepository;
    }

    @Transactional(readOnly = true)
    public TenantContext.TenantDescriptor resolve(String suppliedHostname) {
        final String hostname;
        try {
            hostname = HostnameNormalizer.normalize(suppliedHostname);
        } catch (IllegalArgumentException ex) {
            throw new TenantResolutionException("Tenant could not be resolved", ex);
        }

        TenantDomain domain = domainRepository
                .findByHostnameAndVerificationStatus(hostname, DomainVerificationStatus.VERIFIED)
                .orElseThrow(() -> new TenantResolutionException("Tenant could not be resolved"));

        var tenant = domain.getTenant();
        if (tenant.getLifecycleStatus() != TenantLifecycleStatus.ACTIVE
                || tenant.getProvisioningStatus() != ProvisioningStatus.SUCCEEDED
                || tenant.getTenantDatabaseName() == null
                || tenant.getTenantDatabaseName().isBlank()) {
            throw new TenantResolutionException("Tenant is unavailable");
        }
        return new TenantContext.TenantDescriptor(
                tenant.getTenantId(), tenant.getSlug(), tenant.getTenantDatabaseName());
    }
}