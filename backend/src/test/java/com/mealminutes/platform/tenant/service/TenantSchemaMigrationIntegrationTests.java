package com.mealminutes.platform.tenant.service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The CI workflow supplies a disposable MySQL service and restricted test
 * credentials. Local test runs skip this integration test unless explicitly
 * configured with equivalent isolated test-database credentials.
 */
class TenantSchemaMigrationIntegrationTests {
    @Test
    void appliesTenantBusinessSchemaToAnIsolatedDatabase() throws Exception {
        String adminUrl = System.getenv("TENANT_TEST_ADMIN_JDBC_URL");
        String tenantUrlTemplate = System.getenv("TENANT_TEST_JDBC_URL_TEMPLATE");
        String username = System.getenv("TENANT_TEST_ADMIN_USERNAME");
        String password = System.getenv("TENANT_TEST_ADMIN_PASSWORD");
        Assumptions.assumeTrue(adminUrl != null && tenantUrlTemplate != null
                && username != null && password != null,
                "Set isolated TENANT_TEST_* variables to run the tenant schema integration test");

        String databaseName = "mm_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String tenantUrl = tenantUrlTemplate.replace("{database}", databaseName);
        try {
            try (var connection = DriverManager.getConnection(adminUrl, username, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE " + databaseName + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            }

            Flyway flyway = Flyway.configure().dataSource(tenantUrl, username, password)
                    .locations("classpath:db/tenant-migration").cleanDisabled(true).load();
            flyway.migrate();

            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("10");
            try (var connection = DriverManager.getConnection(tenantUrl, username, password);
                 ResultSet columns = connection.getMetaData().getColumns(databaseName, null, "customer_cart", "active_cart_key")) {
                assertThat(columns.next()).isTrue();
            }
            try (var connection = DriverManager.getConnection(tenantUrl, username, password);
                 ResultSet columns = connection.getMetaData().getColumns(databaseName, null, "customer_order", "request_fingerprint")) {
                assertThat(columns.next()).isTrue();
            }
            try (var connection = DriverManager.getConnection(tenantUrl, username, password);
                 ResultSet columns = connection.getMetaData().getColumns(databaseName, null, "customer_order", "fulfillment_type")) {
                assertThat(columns.next()).isTrue();
            }
            try (var connection = DriverManager.getConnection(tenantUrl, username, password);
                 ResultSet columns = connection.getMetaData().getColumns(databaseName, null, "customer_address", "default_customer_user_id")) {
                assertThat(columns.next()).isTrue();
            }
            try (var connection = DriverManager.getConnection(tenantUrl, username, password);
                 ResultSet tables = connection.getMetaData().getTables(databaseName, null, "restaurant_branch_operating_hours", new String[]{"TABLE"})) {
                assertThat(tables.next()).isTrue();
            }
            try (var connection = DriverManager.getConnection(tenantUrl, username, password);
                 ResultSet columns = connection.getMetaData().getColumns(databaseName, null, "restaurant_profile", "active_profile_key")) {
                assertThat(columns.next()).isTrue();
            }
            try (var connection = DriverManager.getConnection(tenantUrl, username, password);
                 ResultSet columns = connection.getMetaData().getColumns(databaseName, null, "outbox_event", "next_attempt_at")) {
                assertThat(columns.next()).isTrue();
            }
            try (var connection = DriverManager.getConnection(tenantUrl, username, password);
                 ResultSet columns = connection.getMetaData().getColumns(databaseName, null, "outbox_event", "dead_lettered_at")) {
                assertThat(columns.next()).isTrue();
            }
            try (var connection = DriverManager.getConnection(tenantUrl, username, password);
                 ResultSet columns = connection.getMetaData().getColumns(databaseName, null, "outbox_event", "next_attempt_at")) {
                assertThat(columns.next()).isTrue();
            }
            try (var connection = DriverManager.getConnection(tenantUrl, username, password);
                 ResultSet columns = connection.getMetaData().getColumns(databaseName, null, "outbox_event", "dead_lettered_at")) {
                assertThat(columns.next()).isTrue();
            }
            try (var connection = DriverManager.getConnection(tenantUrl, username, password);
                 ResultSet columns = connection.getMetaData().getColumns(databaseName, null, "restaurant_profile", "active_profile_key")) {
                assertThat(columns.next()).isTrue();
            }
            try (var connection = DriverManager.getConnection(tenantUrl, username, password);
                 ResultSet columns = connection.getMetaData().getColumns(databaseName, null, "payment_transaction", "provider_order_id")) {
                assertThat(columns.next()).isTrue();
            }
            try (var connection = DriverManager.getConnection(tenantUrl, username, password);
                 ResultSet tables = connection.getMetaData().getTables(databaseName, null, "payment_webhook_event", new String[]{"TABLE"})) {
                assertThat(tables.next()).isTrue();
            }
            try (var connection = DriverManager.getConnection(tenantUrl, username, password);
                 ResultSet tables = connection.getMetaData().getTables(databaseName, null, "restaurant_profile", new String[]{"TABLE"})) {
                assertThat(tables.next()).isTrue();
            }
            try (var connection = DriverManager.getConnection(tenantUrl, username, password);
                 ResultSet tables = connection.getMetaData().getTables(databaseName, null, "customer_order", new String[]{"TABLE"})) {
                assertThat(tables.next()).isTrue();
            }
            try (var connection = DriverManager.getConnection(tenantUrl, username, password);
                 ResultSet tables = connection.getMetaData().getTables(databaseName, null, "outbox_event", new String[]{"TABLE"})) {
                assertThat(tables.next()).isTrue();
            }
            try (var connection = DriverManager.getConnection(tenantUrl, username, password);
                 ResultSet tables = connection.getMetaData().getTables(databaseName, null, "inbox_event", new String[]{"TABLE"})) {
                assertThat(tables.next()).isTrue();
            }
        } finally {
            try (var connection = DriverManager.getConnection(adminUrl, username, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS " + databaseName);
            }
        }
    }
}