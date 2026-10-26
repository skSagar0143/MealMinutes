package com.mealminutes.platform.tenant.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantSlugNormalizerTests {
    @Test void createsAsciiSlugFromName() {
        assertThat(TenantSlugNormalizer.fromName("  Café & Tea  ")).isEqualTo("cafe-tea");
        assertThat(TenantSlugNormalizer.fromName("HOT   DRINKS")).isEqualTo("hot-drinks");
        assertThat(TenantSlugNormalizer.fromName("A")).isEqualTo("a");
    }
    @Test void rejectsEmptyNamesAndInvalidSlugs() {
        assertThatThrownBy(() -> TenantSlugNormalizer.fromName("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TenantSlugNormalizer.requireSlug(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TenantSlugNormalizer.requireSlug("../admin")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TenantSlugNormalizer.requireSlug("two words")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TenantSlugNormalizer.requireSlug("x".repeat(161))).isInstanceOf(IllegalArgumentException.class);
    }
}
// Direct tests exercise normalization independently of service calls.