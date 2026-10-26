package com.mealminutes.platform.tenant.service;

import com.mealminutes.platform.tenant.domain.HostnameNormalizer;
import com.mealminutes.platform.tenant.domain.TenantDomain;
import com.mealminutes.platform.tenant.persistence.TenantDomainRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class TenantDomainVerificationService {
    private final TenantDomainRepository domains;
    private final DomainOwnershipVerifier ownershipVerifier;

    public TenantDomainVerificationService(TenantDomainRepository domains, DomainOwnershipVerifier ownershipVerifier) {
        this.domains = domains;
        this.ownershipVerifier = ownershipVerifier;
    }

    public TenantDomain verify(String suppliedHostname, String challenge) {
        String hostname = HostnameNormalizer.normalize(suppliedHostname);
        TenantDomain domain = domains.findByHostname(hostname)
                .orElseThrow(() -> new IllegalArgumentException("Domain is not registered"));
        if (!domain.matchesVerificationChallenge(challenge, Instant.now())) {
            throw new IllegalArgumentException("Domain verification challenge is invalid or expired");
        }
        String recordName = "_mealminutes-verification." + hostname;
        String expectedValue = "mealminutes-verification=" + challenge;
        if (!ownershipVerifier.hasTxtRecord(recordName, expectedValue)) {
            throw new IllegalStateException("Required DNS TXT verification record was not found");
        }
        domain.markVerified();
        return domains.saveAndFlush(domain);
    }
}