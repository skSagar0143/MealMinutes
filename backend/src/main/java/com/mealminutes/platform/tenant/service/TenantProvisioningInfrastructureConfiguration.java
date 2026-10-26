package com.mealminutes.platform.tenant.service;

import com.mealminutes.platform.tenant.persistence.TenantDatabaseCredentialsProvider;
import com.mealminutes.platform.tenant.persistence.TenantDataSourcePool;
import com.mealminutes.platform.tenant.persistence.TenantDataSourcePoolProperties;
import com.mealminutes.platform.tenant.persistence.TenantAwareDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "mealminutes.tenant-provisioning", name = "enabled", havingValue = "true")
public class TenantProvisioningInfrastructureConfiguration {
    @Bean
    @ConfigurationProperties("mealminutes.tenant-datasource")
    TenantDataSourcePoolProperties tenantDataSourcePoolProperties() {
        return new TenantDataSourcePoolProperties();
    }

    @Bean(destroyMethod = "close")
    TenantDataSourcePool tenantDataSourcePool(TenantDataSourcePoolProperties properties,
                                               TenantDatabaseCredentialsProvider credentialsProvider) {
        return new TenantDataSourcePool(properties, credentialsProvider);
    }

    @Bean(name = "tenantBusinessDataSource")
    DataSource tenantBusinessDataSource(TenantDataSourcePool pool) {
        return new TenantAwareDataSource(pool);
    }

    @Bean(name = "tenantBusinessJdbcTemplate")
    JdbcTemplate tenantBusinessJdbcTemplate(@Qualifier("tenantBusinessDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "tenantTransactionManager")
    PlatformTransactionManager tenantTransactionManager(@Qualifier("tenantBusinessDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    TenantDatabaseAdmin tenantDatabaseAdmin(
            @Value("${mealminutes.tenant-provisioning.admin-jdbc-url}") String adminJdbcUrl,
            @Value("${mealminutes.tenant-provisioning.admin-username}") String adminUsername,
            @Value("${mealminutes.tenant-provisioning.admin-password}") String adminPassword) {
        return new JdbcTenantDatabaseAdmin(adminJdbcUrl, adminUsername, adminPassword);
    }

    @Bean
    TenantSchemaMigrator tenantSchemaMigrator(
            @Value("${mealminutes.tenant-datasource.jdbc-url-template}") String jdbcUrlTemplate,
            @Value("${mealminutes.tenant-provisioning.admin-username}") String adminUsername,
            @Value("${mealminutes.tenant-provisioning.admin-password}") String adminPassword,
            @Value("${mealminutes.tenant-provisioning.migration-location}") String migrationLocation) {
        return new FlywayTenantSchemaMigrator(jdbcUrlTemplate, adminUsername, adminPassword, migrationLocation);
    }

    @Bean
    TenantDatabaseProvisioner tenantDatabaseProvisioner(TenantDatabaseAdmin admin,
                                                        TenantCredentialSecretStore secretStore,
                                                        TenantSchemaMigrator migrator) {
        return new JdbcTenantDatabaseProvisioner(admin, secretStore, migrator);
    }

    @Bean
    TenantDatabaseCredentialsProvider tenantDatabaseCredentialsProvider(TenantCredentialSecretStore secretStore) {
        return secretReference -> {
            var credentials = secretStore.get(secretReference);
            return new TenantDatabaseCredentialsProvider.DatabaseCredentials(
                    credentials.username(), credentials.password());
        };
    }
}