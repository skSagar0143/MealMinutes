package com.mealminutes.platform.tenant.service;

import com.mealminutes.platform.tenant.domain.DomainVerificationStatus;
import com.mealminutes.platform.tenant.domain.TenantDomain;
import com.mealminutes.platform.tenant.domain.TenantLifecycleStatus;
import com.mealminutes.platform.tenant.domain.TenantRegistryEntry;
import com.mealminutes.platform.tenant.persistence.TenantDomainRepository;
import com.mealminutes.platform.tenant.persistence.TenantRegistryRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantProvisioningService {
    private final TenantRegistryRepository tenantRepository;
    private final TenantDomainRepository domainRepository;
    private final TenantDatabaseProvisioner databaseProvisioner;

    @Autowired
    public TenantProvisioningService(TenantRegistryRepository tenantRepository,
                                     TenantDomainRepository domainRepository,
                                     ObjectProvider<TenantDatabaseProvisioner> provisioners) {
        this(tenantRepository, domainRepository, provisioners.getIfAvailable());
    }

    public TenantProvisioningService(TenantRegistryRepository tenantRepository,
                                     TenantDomainRepository domainRepository,
                                     TenantDatabaseProvisioner databaseProvisioner) {
        this.tenantRepository = tenantRepository;
        this.domainRepository = domainRepository;
        this.databaseProvisioner = databaseProvisioner;
    }

    @Transactional
    public TenantRegistrationResult register(String slug, String hostname) {
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
        String challenge = createVerificationChallenge();
        domain.setVerificationChallenge(challenge, java.time.Instant.now().plus(java.time.Duration.ofHours(24)));
        domainRepository.saveAndFlush(domain);
        return new TenantRegistrationResult(tenant.getTenantId(), tenant.getSlug(),
                domain.getHostname(), tenant.getLifecycleStatus().name(),
                "_mealminutes-verification." + domain.getHostname(),
                "mealminutes-verification=" + challenge);
    }

    @Transactional(readOnly = true)
    public Page<TenantRegistryEntry> list(Pageable pageable) {
        return tenantRepository.findAll(pageable);
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
        if (databaseProvisioner == null) throw new TenantProvisionerUnavailableException();
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

    private static String createVerificationChallenge() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record TenantRegistrationResult(String tenantId, String slug, String hostname,
                                           String lifecycleStatus, String dnsTxtRecordName,
                                           String dnsTxtRecordValue) { }

    private TenantRegistryEntry requireTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant ID is required");
        }
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant does not exist"));
    }
}