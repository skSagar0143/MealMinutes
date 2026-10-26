package com.mealminutes.platform.tenant.service;

import com.mealminutes.platform.tenant.domain.TenantContext;
import com.mealminutes.platform.tenant.persistence.TenantAwareDataSource;
import com.mealminutes.platform.tenant.persistence.TenantDataSourcePool;
import com.mealminutes.platform.tenant.persistence.TenantDataSourcePoolProperties;
import com.mealminutes.platform.tenant.persistence.TenantDatabaseCredentialsProvider;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that tenant context selects a physically separate MySQL database.
 * Use only a disposable test MySQL instance: this test creates uniquely named
 * databases that are removed automatically when the CI service container exits.
 */
class TenantDatabaseIsolationIntegrationTests {
    @Test
    void tenantContextSelectsOnlyItsConfiguredDatabase() throws Exception {
        String adminUrl = System.getenv("TENANT_TEST_ADMIN_JDBC_URL");
        String tenantUrlTemplate = System.getenv("TENANT_TEST_JDBC_URL_TEMPLATE");
        String username = System.getenv("TENANT_TEST_ADMIN_USERNAME");
        String password = System.getenv("TENANT_TEST_ADMIN_PASSWORD");
        Assumptions.assumeTrue(adminUrl != null && tenantUrlTemplate != null
                        && username != null && password != null,
                "Set isolated TENANT_TEST_* variables to run the tenant database isolation test");

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String databaseA = "mm_iso_a_" + suffix;
        String databaseB = "mm_iso_b_" + suffix;
        createDatabase(adminUrl, username, password, databaseA);
        createDatabase(adminUrl, username, password, databaseB);

        TenantDataSourcePoolProperties properties = new TenantDataSourcePoolProperties();
        properties.setJdbcUrlTemplate(tenantUrlTemplate);
        try (TenantDataSourcePool pool = new TenantDataSourcePool(properties, reference ->
                new TenantDatabaseCredentialsProvider.DatabaseCredentials(username, password))) {
            TenantAwareDataSource tenantDataSource = new TenantAwareDataSource(pool);
            TenantContext.TenantDescriptor tenantA =
                    new TenantContext.TenantDescriptor("tenant-a", "tenant-a", databaseA, "secret://tenant-a");
            TenantContext.TenantDescriptor tenantB =
                    new TenantContext.TenantDescriptor("tenant-b", "tenant-b", databaseB, "secret://tenant-b");

            insertMarker(tenantDataSource, tenantA, "tenant-a-only");
            insertMarker(tenantDataSource, tenantB, "tenant-b-only");
            assertMarker(tenantDataSource, tenantA, "tenant-a-only");
            assertMarker(tenantDataSource, tenantB, "tenant-b-only");
            assertThat(pool.poolCount()).isEqualTo(2);
        }
    }

    private static void insertMarker(TenantAwareDataSource dataSource,
                                     TenantContext.TenantDescriptor tenant, String marker) throws Exception {
        try (TenantContext.Scope ignored = TenantContext.bind(tenant);
             Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE tenant_isolation_probe (marker VARCHAR(64) NOT NULL)");
            statement.executeUpdate("INSERT INTO tenant_isolation_probe(marker) VALUES ('" + marker + "')");
        }
    }

    private static void assertMarker(TenantAwareDataSource dataSource,
                                     TenantContext.TenantDescriptor tenant, String expected) throws Exception {
        try (TenantContext.Scope ignored = TenantContext.bind(tenant);
             Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT marker FROM tenant_isolation_probe")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo(expected);
            assertThat(result.next()).isFalse();
        }
    }

    private static void createDatabase(String adminUrl, String username, String password,
                                       String databaseName) throws Exception {
        try (Connection connection = DriverManager.getConnection(adminUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + databaseName
                    + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
    }
}
