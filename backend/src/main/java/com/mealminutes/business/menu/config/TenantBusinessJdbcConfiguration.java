package com.mealminutes.business.menu.config;

import com.mealminutes.platform.tenant.persistence.TenantDataSourcePool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/** Exposes tenant JDBC only when a credential-backed tenant pool is configured. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "mealminutes.tenant-datasource", name = "standalone-enabled", havingValue = "true")
@ConditionalOnBean(TenantDataSourcePool.class)
public class TenantBusinessJdbcConfiguration {
    @Bean(name = "tenantBusinessJdbcTemplate")
    JdbcTemplate tenantBusinessJdbcTemplate(@Qualifier("tenantBusinessDataSource") DataSource tenantBusinessDataSource) {
        return new JdbcTemplate(tenantBusinessDataSource);
    }
}
