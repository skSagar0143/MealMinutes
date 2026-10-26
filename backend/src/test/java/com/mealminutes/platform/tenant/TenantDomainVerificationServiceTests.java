package com.mealminutes.platform.tenant;

import com.mealminutes.platform.tenant.domain.TenantDomain;
import com.mealminutes.platform.tenant.domain.TenantRegistryEntry;
import com.mealminutes.platform.tenant.domain.DomainVerificationStatus;
import com.mealminutes.platform.tenant.persistence.TenantDomainRepository;
import com.mealminutes.platform.tenant.service.DomainOwnershipVerifier;
import com.mealminutes.platform.tenant.service.TenantDomainVerificationService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TenantDomainVerificationServiceTests {
    @Test void verifiesOnlyWhenChallengeAndDnsRecordMatch() {
        TenantDomainRepository domains = mock(TenantDomainRepository.class);
        DomainOwnershipVerifier dns = mock(DomainOwnershipVerifier.class);
        TenantDomain domain = domainWithChallenge();
        when(domains.findByHostname("shop.example.com")).thenReturn(Optional.of(domain));
        when(dns.hasTxtRecord("_mealminutes-verification.shop.example.com", "mealminutes-verification=challenge-123"))
                .thenReturn(true);
        when(domains.saveAndFlush(domain)).thenReturn(domain);

        TenantDomain result = new TenantDomainVerificationService(domains, dns)
                .verify(" SHOP.EXAMPLE.COM. ", "challenge-123");

        assertThat(result.getVerificationStatus()).isEqualTo(DomainVerificationStatus.VERIFIED);
        assertThat(result.getVerificationExpiresAt()).isNull();
        verify(domains).saveAndFlush(domain);
    }

    @Test void rejectsInvalidChallengeBeforeDnsLookup() {
        TenantDomainRepository domains = mock(TenantDomainRepository.class);
        DomainOwnershipVerifier dns = mock(DomainOwnershipVerifier.class);
        when(domains.findByHostname("shop.example.com")).thenReturn(Optional.of(domainWithChallenge()));

        assertThatThrownBy(() -> new TenantDomainVerificationService(domains, dns)
                .verify("shop.example.com", "wrong-token")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(dns);
    }

    @Test void leavesDomainPendingWhenDnsProofIsMissing() {
        TenantDomainRepository domains = mock(TenantDomainRepository.class);
        DomainOwnershipVerifier dns = mock(DomainOwnershipVerifier.class);
        TenantDomain domain = domainWithChallenge();
        when(domains.findByHostname("shop.example.com")).thenReturn(Optional.of(domain));
        when(dns.hasTxtRecord(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> new TenantDomainVerificationService(domains, dns)
                .verify("shop.example.com", "challenge-123")).isInstanceOf(IllegalStateException.class);
        assertThat(domain.getVerificationStatus()).isEqualTo(DomainVerificationStatus.PENDING);
        verify(domains, never()).saveAndFlush(any());
    }

    private TenantDomain domainWithChallenge() {
        TenantDomain domain = new TenantDomain(new TenantRegistryEntry("abc-food"), "shop.example.com");
        domain.setVerificationChallenge("challenge-123", Instant.now().plusSeconds(3600));
        return domain;
    }
}