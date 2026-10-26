package com.mealminutes.platform.tenant.persistence;

import org.junit.jupiter.api.Test;
import javax.sql.DataSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TenantDataSourceConfigurationTests {
    @Test void createsValidatedPoolAndTenantDatasourceAdapter() {
        TenantDataSourceConfiguration configuration = new TenantDataSourceConfiguration();
        TenantDataSourcePoolProperties properties = configuration.tenantDataSourcePoolProperties();
        assertThat(properties.getMaxPoolCount()).isEqualTo(20);
        TenantDatabaseCredentialsProvider credentials = secret ->
                new TenantDatabaseCredentialsProvider.DatabaseCredentials("tenant_user", "test-password");
        TenantDataSourcePool pool = configuration.tenantDataSourcePool(properties, credentials);
        try {
            DataSource dataSource = configuration.tenantBusinessDataSource(pool);
            assertThat(dataSource).isInstanceOf(TenantAwareDataSource.class);
            assertThat(pool.poolCount()).isZero();
        } finally {
            pool.close();
        }
    }
}