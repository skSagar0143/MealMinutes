package com.mealminutes.platform.tenant.service;

import com.mealminutes.platform.tenant.domain.DomainVerificationStatus;
import com.mealminutes.platform.tenant.domain.TenantDomain;
import com.mealminutes.platform.tenant.domain.TenantLifecycleStatus;
import com.mealminutes.platform.tenant.domain.TenantRegistryEntry;
import com.mealminutes.platform.tenant.persistence.TenantDomainRepository;
import com.mealminutes.platform.tenant.persistence.TenantRegistryRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnBean(TenantDatabaseProvisioner.class)
public class TenantProvisioningService {
    private final TenantRegistryRepository tenantRepository;
    private final TenantDomainRepository domainRepository;
    private final TenantDatabaseProvisioner databaseProvisioner;

    public TenantProvisioningService(TenantRegistryRepository tenantRepository,
                                     TenantDomainRepository domainRepository,
                                     TenantDatabaseProvisioner databaseProvisioner) {
        this.tenantRepository = tenantRepository;
        this.domainRepository = domainRepository;
        this.databaseProvisioner = databaseProvisioner;
    }

    @Transactional
    public TenantRegistryEntry register(String slug, String hostname) {
        if (tenantRepository.existsBySlug(slug)) {
            throw new IllegalArgumentException("Tenant slug is already registered");
        }
        String normalizedHostname = com.mealminutes.platform.tenant.domain.HostnameNormalizer.normalize(hostname);
        if (domainRepository.existsByHostname(normalizedHostname)) {
            throw new IllegalArgumentException("Hostname is already registered");
        }

        TenantRegistryEntry tenant = new TenantRegistryEntry(slug);
        tenant.transitionTo(TenantLifecycleStatus.PENDING_VERIFICATION);
        tenant = tenantRepository.saveAndFlush(tenant);
        TenantDomain domain = new TenantDomain(tenant, normalizedHostname);
        domainRepository.saveAndFlush(domain);
        return tenant;
    }

    @Transactional
    public TenantRegistryEntry approve(String tenantId) {
        TenantRegistryEntry tenant = requireTenant(tenantId);
        tenant.transitionTo(TenantLifecycleStatus.APPROVED);
        return tenantRepository.saveAndFlush(tenant);
    }

    /**
     * Each state change is persisted before/after the external provisioner call.
     * A failed attempt remains visible and may be retried safely by the port.
     */
    public TenantRegistryEntry provision(String tenantId) {
        TenantRegistryEntry tenant = requireTenant(tenantId);
        if (tenant.getLifecycleStatus() != TenantLifecycleStatus.APPROVED
                && tenant.getLifecycleStatus() != TenantLifecycleStatus.FAILED) {
            throw new IllegalStateException("Tenant must be approved before provisioning");
        }
        boolean hasVerifiedDomain = domainRepository
                .findFirstByTenant_TenantIdAndVerificationStatus(
                        tenantId, DomainVerificationStatus.VERIFIED)
                .isPresent();
        if (!hasVerifiedDomain) {
            throw new IllegalStateException("Tenant must have a verified domain before provisioning");
        }

        tenant.beginProvisioning();
        tenantRepository.saveAndFlush(tenant);

        try {
            TenantDatabaseProvisioner.ProvisionedDatabase provisioned =
                    databaseProvisioner.provision(tenant.getSlug());
            TenantRegistryEntry latest = requireTenant(tenantId);
            latest.completeProvisioning(provisioned.databaseName(),
                    provisioned.secretReference(), provisioned.schemaVersion());
            latest.transitionTo(TenantLifecycleStatus.ACTIVE);
            return tenantRepository.saveAndFlush(latest);
        } catch (RuntimeException exception) {
            TenantRegistryEntry latest = requireTenant(tenantId);
            if (latest.getLifecycleStatus() == TenantLifecycleStatus.PROVISIONING) {
                latest.failProvisioning();
                tenantRepository.saveAndFlush(latest);
            }
            throw exception;
        }
    }

    @Transactional
    public TenantRegistryEntry suspend(String tenantId) {
        TenantRegistryEntry tenant = requireTenant(tenantId);
        tenant.transitionTo(TenantLifecycleStatus.SUSPENDED);
        return tenantRepository.saveAndFlush(tenant);
    }

    @Transactional
    public TenantRegistryEntry reactivate(String tenantId) {
        TenantRegistryEntry tenant = requireTenant(tenantId);
        tenant.transitionTo(TenantLifecycleStatus.ACTIVE);
        return tenantRepository.saveAndFlush(tenant);
    }

    private TenantRegistryEntry requireTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant ID is required");
        }
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant does not exist"));
    }
}