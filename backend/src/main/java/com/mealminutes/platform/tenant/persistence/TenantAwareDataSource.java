package com.mealminutes.platform.tenant.persistence;

import com.mealminutes.platform.tenant.domain.TenantContext;
import org.springframework.jdbc.datasource.AbstractDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

/**
 * Datasource adapter for tenant business persistence. Configure it on a
 * dedicated tenant EntityManagerFactory; do not mark it as the platform
 * datasource or primary datasource.
 */
public final class TenantAwareDataSource extends AbstractDataSource {
    private final TenantDataSourcePool pool;

    public TenantAwareDataSource(TenantDataSourcePool pool) {
        this.pool = pool;
    }

    @Override
    public Connection getConnection() throws SQLException {
        TenantContext.TenantDescriptor tenant = TenantContext.currentOrNull();
        if (tenant == null) {
            throw new SQLException("Tenant context is required for tenant database access");
        }
        try {
            DataSource delegate = pool.getOrCreate(tenant);
            return delegate.getConnection();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new SQLException("Tenant database is unavailable", ex);
        }
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        throw new SQLFeatureNotSupportedException(
                "Tenant credentials are resolved from the configured secret provider");
    }
}