package com.mealminutes.platform.tenant;

import com.mealminutes.platform.tenant.domain.TenantContext;
import com.mealminutes.platform.tenant.persistence.TenantAwareDataSource;
import com.mealminutes.platform.tenant.persistence.TenantDataSourcePool;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TenantAwareDataSourceTests {
    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void requiresTenantContextBeforeOpeningTenantConnection() throws Exception {
        TenantAwareDataSource dataSource = new TenantAwareDataSource(mock(TenantDataSourcePool.class));
        assertThatThrownBy(dataSource::getConnection)
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("Tenant context is required");
    }

    @Test
    void selectsPoolUsingCurrentTenantDescriptor() throws Exception {
        TenantDataSourcePool pool = mock(TenantDataSourcePool.class);
        HikariDataSource delegate = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        var tenant = new TenantContext.TenantDescriptor("tenant-1", "abc", "tenant_abc", "secret://abc");
        when(pool.getOrCreate(tenant)).thenReturn(delegate);
        when(delegate.getConnection()).thenReturn(connection);
        TenantAwareDataSource dataSource = new TenantAwareDataSource(pool);

        try (TenantContext.Scope ignored = TenantContext.bind(tenant)) {
            dataSource.getConnection();
        }

        verify(pool).getOrCreate(tenant);
        verify(delegate).getConnection();
    }

    @Test
    void neverAcceptsCallerSuppliedTenantCredentials() {
        TenantAwareDataSource dataSource = new TenantAwareDataSource(mock(TenantDataSourcePool.class));
        assertThatThrownBy(() -> dataSource.getConnection("caller", "password"))
                .isInstanceOf(SQLFeatureNotSupportedException.class);
    }
}