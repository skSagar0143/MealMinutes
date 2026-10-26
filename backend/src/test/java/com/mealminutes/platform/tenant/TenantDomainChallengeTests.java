package com.mealminutes.platform.tenant;

import com.mealminutes.platform.tenant.domain.TenantDomain;
import com.mealminutes.platform.tenant.domain.TenantRegistryEntry;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantDomainChallengeTests {
    @Test void challengeIsHashedExpiresAndIsClearedAfterVerification() {
        TenantDomain domain = new TenantDomain(new TenantRegistryEntry("abc-food"), "shop.example.com");
        Instant expiry = Instant.now().plusSeconds(300);
        domain.setVerificationChallenge("secret-challenge", expiry);
        assertThat(domain.matchesVerificationChallenge("secret-challenge", Instant.now())).isTrue();
        assertThat(domain.matchesVerificationChallenge("wrong", Instant.now())).isFalse();
        assertThat(domain.matchesVerificationChallenge("secret-challenge", expiry.plusMillis(1))).isFalse();
        domain.markVerified();
        assertThat(domain.matchesVerificationChallenge("secret-challenge", Instant.now())).isFalse();
    }
    @Test void rejectsBlankChallengeAndPastExpiry() {
        TenantDomain domain = new TenantDomain(new TenantRegistryEntry("abc-food"), "shop.example.com");
        assertThatThrownBy(() -> domain.setVerificationChallenge(" ", Instant.now().plusSeconds(30)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> domain.setVerificationChallenge("token", Instant.now().minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}