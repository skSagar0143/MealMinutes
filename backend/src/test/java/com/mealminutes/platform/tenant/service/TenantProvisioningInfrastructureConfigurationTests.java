package com.mealminutes.platform.tenant.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TenantProvisioningInfrastructureConfigurationTests {
    @Test void createsTenantDataSourceJdbcTemplateAndTransactionManager() {
        var configuration = new TenantProvisioningInfrastructureConfiguration();
        var properties = configuration.tenantDataSourcePoolProperties();
        var provider = (TenantDatabaseCredentialsProvider) secretReference ->
                new com.mealminutes.platform.tenant.persistence.TenantDatabaseCredentialsProvider.DatabaseCredentials(
                        "mm_tenant_a", "A".repeat(43));
        var pool = configuration.tenantDataSourcePool(properties, provider);
        try {
            var dataSource = configuration.tenantBusinessDataSource(pool);
            assertThat(dataSource).isInstanceOf(com.mealminutes.platform.tenant.persistence.TenantAwareDataSource.class);
            assertThat(configuration.tenantBusinessJdbcTemplate(dataSource)).isNotNull();
            assertThat(configuration.tenantTransactionManager(dataSource)).isInstanceOf(
                    org.springframework.jdbc.datasource.DataSourceTransactionManager.class);
        } finally {
            pool.close();
        }
    }

    @Test void wiresProvisionerAndSecretBackedCredentialProvider() {
        TenantProvisioningInfrastructureConfiguration configuration = new TenantProvisioningInfrastructureConfiguration();
        TenantDatabaseAdmin admin = mock(TenantDatabaseAdmin.class);
        TenantSchemaMigrator migrator = mock(TenantSchemaMigrator.class);
        TenantCredentialSecretStore store = mock(TenantCredentialSecretStore.class);
        when(store.get("secret://tenant/a")).thenReturn(
                new TenantCredentialSecretStore.TenantCredentials("mm_tenant_a", "A".repeat(43)));

        var provisioner = configuration.tenantDatabaseProvisioner(admin, store, migrator);
        var provider = configuration.tenantDatabaseCredentialsProvider(store);

        assertThat(provisioner).isInstanceOf(JdbcTenantDatabaseProvisioner.class);
        assertThat(provider.resolve("secret://tenant/a").username()).isEqualTo("mm_tenant_a");
        assertThat(provider.resolve("secret://tenant/a").password()).isEqualTo("A".repeat(43));
    }
}