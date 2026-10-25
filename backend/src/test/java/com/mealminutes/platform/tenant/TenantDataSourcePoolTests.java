package com.mealminutes.platform.tenant;

import com.mealminutes.platform.tenant.domain.TenantContext;
import com.mealminutes.platform.tenant.persistence.TenantDataSourcePool;
import com.mealminutes.platform.tenant.persistence.TenantDataSourcePoolProperties;
import com.mealminutes.platform.tenant.persistence.TenantDatabaseCredentialsProvider;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TenantDataSourcePoolTests {
    @Test
    void reusesPoolForSameTenantAndEvictsLeastRecentlyUsedPoolAtLimit() {
        TenantDataSourcePoolProperties properties = new TenantDataSourcePoolProperties();
        properties.setMaxPoolCount(2);
        List<HikariDataSource> created = new ArrayList<>();
        TenantDataSourcePool pool = new TenantDataSourcePool(properties,
                secret -> new TenantDatabaseCredentialsProvider.DatabaseCredentials("tenant_user", "test-password"),
                (url, name, credentials, ignored) -> {
                    HikariDataSource dataSource = mock(HikariDataSource.class);
                    created.add(dataSource);
                    return dataSource;
                });
        try {
            var a = descriptor("tenant-a", "tenant_a", "secret://a");
            var b = descriptor("tenant-b", "tenant_b", "secret://b");
            var c = descriptor("tenant-c", "tenant_c", "secret://c");
            HikariDataSource first = pool.getOrCreate(a);
            assertThat(pool.getOrCreate(a)).isSameAs(first);
            pool.getOrCreate(b);
            pool.getOrCreate(c);
            assertThat(pool.poolCount()).isEqualTo(2);
            verify(first).close();
        } finally {
            pool.close();
        }
        assertThat(pool.poolCount()).isZero();
    }

    @Test
    void replacesPoolWhenSecretReferenceChanges() {
        List<HikariDataSource> created = new ArrayList<>();
        TenantDataSourcePool pool = new TenantDataSourcePool(new TenantDataSourcePoolProperties(),
                secret -> new TenantDatabaseCredentialsProvider.DatabaseCredentials("tenant_user", "test-password"),
                (url, name, credentials, ignored) -> {
                    HikariDataSource dataSource = mock(HikariDataSource.class);
                    created.add(dataSource);
                    return dataSource;
                });
        try {
            HikariDataSource old = pool.getOrCreate(descriptor("tenant-a", "tenant_a", "secret://old"));
            HikariDataSource current = pool.getOrCreate(descriptor("tenant-a", "tenant_a", "secret://new"));
            assertThat(current).isNotSameAs(old);
            verify(old).close();
            assertThat(pool.poolCount()).isEqualTo(1);
        } finally {
            pool.close();
        }
    }

    @Test
    void rejectsMissingSecretAndUnsafeDatabaseName() {
        TenantDataSourcePool pool = new TenantDataSourcePool(new TenantDataSourcePoolProperties(),
                secret -> new TenantDatabaseCredentialsProvider.DatabaseCredentials("tenant_user", "test-password"),
                (url, name, credentials, ignored) -> mock(HikariDataSource.class));
        try {
            assertThatThrownBy(() -> pool.getOrCreate(
                    new TenantContext.TenantDescriptor("tenant-a", "a", "tenant_a", null)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> pool.getOrCreate(
                    descriptor("tenant-a", "tenant_a;drop", "secret://a")))
                    .isInstanceOf(IllegalArgumentException.class);
        } finally {
            pool.close();
        }
    }

    @Test
    void validatesUrlTemplateAndPoolBounds() {
        TenantDataSourcePoolProperties properties = new TenantDataSourcePoolProperties();
        properties.setJdbcUrlTemplate("jdbc:mysql://db:3306/fixed");
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalArgumentException.class);
        properties.setJdbcUrlTemplate("jdbc:mysql://db:3306/{database}");
        properties.setMinIdlePerTenant(6);
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalArgumentException.class);
    }

    private TenantContext.TenantDescriptor descriptor(String id, String database, String secret) {
        return new TenantContext.TenantDescriptor(id, id, database, secret);
    }
}