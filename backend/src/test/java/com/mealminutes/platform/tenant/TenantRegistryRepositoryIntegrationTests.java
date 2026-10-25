package com.mealminutes.platform.tenant;

import com.mealminutes.platform.tenant.domain.ProvisioningStatus;
import com.mealminutes.platform.tenant.domain.TenantLifecycleStatus;
import com.mealminutes.platform.tenant.domain.TenantRegistryEntry;
import com.mealminutes.platform.tenant.persistence.TenantRegistryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class TenantRegistryRepositoryIntegrationTests {

    @Autowired
    private TenantRegistryRepository tenantRegistryRepository;

    @Test
    void persistsTenantWithSafeInitialLifecycleState() {
        String slug = "test-" + UUID.randomUUID().toString().substring(0, 8);
        TenantRegistryEntry saved = tenantRegistryRepository.saveAndFlush(new TenantRegistryEntry(slug));

        TenantRegistryEntry loaded = tenantRegistryRepository.findBySlug(slug).orElseThrow();

        assertThat(loaded.getTenantId()).isEqualTo(saved.getTenantId());
        assertThat(loaded.getLifecycleStatus()).isEqualTo(TenantLifecycleStatus.REGISTERED);
        assertThat(loaded.getProvisioningStatus()).isEqualTo(ProvisioningStatus.NOT_STARTED);
        assertThat(loaded.getDatabaseSecretReference()).isNull();
    }
}
