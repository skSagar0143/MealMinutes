package com.mealminutes.platform.tenant;

import com.mealminutes.platform.tenant.domain.DomainVerificationStatus;
import com.mealminutes.platform.tenant.domain.TenantDomain;
import com.mealminutes.platform.tenant.domain.TenantRegistryEntry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantDomainTests {
    @Test
    void normalizesHostnameAndStartsPending() {
        TenantDomain domain = new TenantDomain(new TenantRegistryEntry("abc-food"), " ABCFOOD.Example.com. ");
        assertThat(domain.getHostname()).isEqualTo("abcfood.example.com");
        assertThat(domain.getVerificationStatus()).isEqualTo(DomainVerificationStatus.PENDING);
        assertThat(domain.isPrimary()).isFalse();
    }

    @Test
    void verificationCanBeRevokedButRevokedDomainCannotBeReverified() {
        TenantDomain domain = new TenantDomain(new TenantRegistryEntry("abc-food"), "abcfood.example.com");
        domain.markVerified();
        assertThat(domain.getVerificationStatus()).isEqualTo(DomainVerificationStatus.VERIFIED);
        domain.revoke();
        assertThat(domain.getVerificationStatus()).isEqualTo(DomainVerificationStatus.REVOKED);
        assertThatThrownBy(domain::markVerified).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void requiresTenantAndValidHostname() {
        assertThatThrownBy(() -> new TenantDomain(null, "abcfood.example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TenantDomain(new TenantRegistryEntry("abc-food"), "abcfood.example.com/path"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void canMarkPrimaryDomain() {
        TenantDomain domain = new TenantDomain(new TenantRegistryEntry("abc-food"), "abcfood.example.com");
        domain.markPrimary();
        assertThat(domain.isPrimary()).isTrue();
    }
}