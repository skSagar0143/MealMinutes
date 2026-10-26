package com.mealminutes.platform.tenant.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.regex.Pattern;

/** Uses dedicated provisioning credentials; never expose this admin component to request input. */
public final class JdbcTenantDatabaseAdmin implements TenantDatabaseAdmin {
    private static final Pattern DATABASE = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    private static final Pattern USERNAME = Pattern.compile("[a-zA-Z0-9_]{1,32}");
    private static final Pattern PASSWORD = Pattern.compile("[A-Za-z0-9_-]{32,128}");
    private final AdminConnectionProvider connectionProvider;

    public JdbcTenantDatabaseAdmin(String adminJdbcUrl, String adminUsername, String adminPassword) {
        this(adminJdbcUrl, adminUsername, adminPassword,
                () -> DriverManager.getConnection(adminJdbcUrl, adminUsername, adminPassword));
    }

    JdbcTenantDatabaseAdmin(String adminJdbcUrl, String adminUsername, String adminPassword,
                            AdminConnectionProvider connectionProvider) {
        if (adminJdbcUrl == null || !adminJdbcUrl.startsWith("jdbc:mysql://")
                || adminUsername == null || adminUsername.isBlank()
                || adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalArgumentException("Trusted MySQL provisioning URL and credentials are required");
        }
        this.connectionProvider = Objects.requireNonNull(connectionProvider);
    }

    @Override
    public void ensureDatabase(String databaseName) {
        requireDatabase(databaseName);
        execute("CREATE DATABASE IF NOT EXISTS " + databaseName
                + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
    }

    @Override
    public void ensureUserAndGrant(String databaseName, String username, String password) {
        requireDatabase(databaseName);
        if (username == null || !USERNAME.matcher(username).matches())
            throw new IllegalArgumentException("Invalid tenant DB username");
        if (password == null || !PASSWORD.matcher(password).matches())
            throw new IllegalArgumentException("Invalid generated tenant DB password");
        execute("CREATE USER IF NOT EXISTS '" + username + "'@'%' IDENTIFIED BY '" + password + "'");
        execute("ALTER USER '" + username + "'@'%' IDENTIFIED BY '" + password + "'");
        execute("GRANT SELECT, INSERT, UPDATE, DELETE ON " + databaseName + ".* TO '" + username + "'@'%'");
    }

    private void execute(String sql) {
        try (Connection connection = connectionProvider.open(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException exception) {
            throw new IllegalStateException("Tenant database administration operation failed", exception);
        }
    }

    private static void requireDatabase(String name) {
        if (name == null || !DATABASE.matcher(name).matches())
            throw new IllegalArgumentException("Invalid tenant database name");
    }

    @FunctionalInterface
    interface AdminConnectionProvider { Connection open() throws SQLException; }
}