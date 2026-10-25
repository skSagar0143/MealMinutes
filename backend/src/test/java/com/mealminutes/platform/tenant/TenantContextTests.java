package com.mealminutes.platform.tenant;

import com.mealminutes.platform.tenant.domain.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextTests {
    @AfterEach
    void cleanContext() {
        TenantContext.clear();
    }

    @Test
    void bindsAndRemovesTenantWhenScopeCloses() {
        var descriptor = new TenantContext.TenantDescriptor("tenant-1", "abc-food", "tenant_abc_food");
        try (TenantContext.Scope ignored = TenantContext.bind(descriptor)) {
            assertThat(TenantContext.requireCurrent()).isEqualTo(descriptor);
        }
        assertThat(TenantContext.currentOrNull()).isNull();
    }

    @Test
    void rejectsMissingOrNestedContexts() {
        assertThatThrownBy(TenantContext::requireCurrent).isInstanceOf(IllegalStateException.class);
        try (TenantContext.Scope ignored = TenantContext.bind(
                new TenantContext.TenantDescriptor("tenant-1", "abc-food", null))) {
            assertThatThrownBy(() -> TenantContext.bind(
                    new TenantContext.TenantDescriptor("tenant-2", "xyz-food", null)))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void descriptorRequiresIdentityAndSlug() {
        assertThatThrownBy(() -> new TenantContext.TenantDescriptor("", "abc", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TenantContext.TenantDescriptor("tenant", " ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}