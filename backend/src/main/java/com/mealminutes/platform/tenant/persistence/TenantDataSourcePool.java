package com.mealminutes.platform.tenant.persistence;

import com.mealminutes.platform.tenant.domain.TenantContext;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Bounded, access-ordered tenant connection-pool registry. It is intentionally
 * not wired into the platform JPA datasource; tenant persistence must use a
 * separate tenant-aware EntityManagerFactory before business APIs are exposed.
 */
public final class TenantDataSourcePool implements AutoCloseable {
    private final TenantDataSourcePoolProperties properties;
    private final TenantDatabaseCredentialsProvider credentialsProvider;
    private final TenantDataSourceFactory dataSourceFactory;
    private final LinkedHashMap<String, PoolEntry> pools = new LinkedHashMap<>(16, 0.75f, true);
    private boolean closed;

    public TenantDataSourcePool(TenantDataSourcePoolProperties properties,
                                TenantDatabaseCredentialsProvider credentialsProvider) {
        this(properties, credentialsProvider, TenantDataSourcePool::createHikariDataSource);
    }

    public TenantDataSourcePool(TenantDataSourcePoolProperties properties,
                                TenantDatabaseCredentialsProvider credentialsProvider,
                                TenantDataSourceFactory dataSourceFactory) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.credentialsProvider = Objects.requireNonNull(credentialsProvider, "credentialsProvider");
        this.dataSourceFactory = Objects.requireNonNull(dataSourceFactory, "dataSourceFactory");
        this.properties.validate();
    }

    public synchronized HikariDataSource getOrCreate(TenantContext.TenantDescriptor tenant) {
        if (closed) throw new IllegalStateException("Tenant datasource pool is closed");
        Objects.requireNonNull(tenant, "tenant");
        if (tenant.databaseName() == null || tenant.databaseSecretReference() == null
                || tenant.databaseSecretReference().isBlank()) {
            throw new IllegalArgumentException("Tenant database name and secret reference are required");
        }
        String jdbcUrl = properties.jdbcUrlFor(tenant.databaseName());
        PoolEntry existing = pools.get(tenant.tenantId());
        if (existing != null) {
            if (existing.databaseName.equals(tenant.databaseName())
                    && existing.secretReference.equals(tenant.databaseSecretReference())
                    && !existing.dataSource.isClosed()) {
                return existing.dataSource;
            }
            pools.remove(tenant.tenantId());
            existing.dataSource.close();
        }

        while (pools.size() >= properties.getMaxPoolCount()) {
            Iterator<Map.Entry<String, PoolEntry>> iterator = pools.entrySet().iterator();
            Map.Entry<String, PoolEntry> eldest = iterator.next();
            iterator.remove();
            eldest.getValue().dataSource.close();
        }

        TenantDatabaseCredentialsProvider.DatabaseCredentials credentials =
                credentialsProvider.resolve(tenant.databaseSecretReference());
        HikariDataSource dataSource = dataSourceFactory.create(
                jdbcUrl, "tenant-" + poolNameSuffix(tenant.tenantId()),
                credentials, properties);
        pools.put(tenant.tenantId(), new PoolEntry(
                tenant.databaseName(), tenant.databaseSecretReference(), dataSource));
        return dataSource;
    }

    public synchronized void evict(String tenantId) {
        PoolEntry removed = pools.remove(tenantId);
        if (removed != null) removed.dataSource.close();
    }

    public synchronized int poolCount() {
        return pools.size();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        pools.values().forEach(entry -> entry.dataSource.close());
        pools.clear();
    }

    private static String poolNameSuffix(String tenantId) {
        String normalized = tenantId.replaceAll("[^A-Za-z0-9]", "");
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Tenant ID cannot produce a pool name");
        }
        return normalized.substring(0, Math.min(8, normalized.length()));
    }

    private static HikariDataSource createHikariDataSource(
            String jdbcUrl, String poolName,
            TenantDatabaseCredentialsProvider.DatabaseCredentials credentials,
            TenantDataSourcePoolProperties properties) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(credentials.username());
        config.setPassword(credentials.password());
        config.setPoolName(poolName);
        config.setMaximumPoolSize(properties.getMaxPoolSizePerTenant());
        config.setMinimumIdle(properties.getMinIdlePerTenant());
        config.setConnectionTimeout(properties.getConnectionTimeoutMs());
        config.setIdleTimeout(properties.getIdleTimeoutMs());
        return new HikariDataSource(config);
    }

    @FunctionalInterface
    public interface TenantDataSourceFactory {
        HikariDataSource create(String jdbcUrl, String poolName,
                                TenantDatabaseCredentialsProvider.DatabaseCredentials credentials,
                                TenantDataSourcePoolProperties properties);
    }

    private record PoolEntry(String databaseName, String secretReference, HikariDataSource dataSource) { }
}