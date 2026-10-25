package com.mealminutes.platform.tenant.persistence;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Tenant datasource beans are enabled only when deployment supplies a real
 * secret-manager-backed TenantDatabaseCredentialsProvider. This keeps the
 * application fail-closed instead of inventing or hardcoding tenant passwords.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(TenantDatabaseCredentialsProvider.class)
public class TenantDataSourceConfiguration {
    @Bean
    @ConfigurationProperties("mealminutes.tenant-datasource")
    TenantDataSourcePoolProperties tenantDataSourcePoolProperties() {
        return new TenantDataSourcePoolProperties();
    }

    @Bean(destroyMethod = "close")
    TenantDataSourcePool tenantDataSourcePool(
            TenantDataSourcePoolProperties properties,
            TenantDatabaseCredentialsProvider credentialsProvider) {
        return new TenantDataSourcePool(properties, credentialsProvider);
    }

    @Bean(name = "tenantBusinessDataSource")
    DataSource tenantBusinessDataSource(TenantDataSourcePool pool) {
        return new TenantAwareDataSource(pool);
    }
}