package com.mealminutes.platform.tenant.service;

import org.flywaydb.core.Flyway;
import java.util.regex.Pattern;

public final class FlywayTenantSchemaMigrator implements TenantSchemaMigrator {
    private static final Pattern DATABASE = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    private final String jdbcUrlTemplate;
    private final String adminUsername;
    private final String adminPassword;
    private final String migrationLocation;

    public FlywayTenantSchemaMigrator(String jdbcUrlTemplate, String adminUsername,
                                      String adminPassword, String migrationLocation) {
        if (jdbcUrlTemplate == null || !jdbcUrlTemplate.startsWith("jdbc:mysql://")
                || !jdbcUrlTemplate.contains("{database}")
                || adminUsername == null || adminUsername.isBlank()
                || adminPassword == null || adminPassword.isBlank()
                || migrationLocation == null || migrationLocation.isBlank()) {
            throw new IllegalArgumentException("Valid tenant migration configuration is required");
        }
        this.jdbcUrlTemplate = jdbcUrlTemplate;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.migrationLocation = migrationLocation;
    }

    @Override
    public int migrate(String databaseName) {
        if (databaseName == null || !DATABASE.matcher(databaseName).matches())
            throw new IllegalArgumentException("Invalid tenant database name");
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrlTemplate.replace("{database}", databaseName), adminUsername, adminPassword)
                .locations(migrationLocation).cleanDisabled(true).load();
        flyway.migrate();
        var current = flyway.info().current();
        if (current == null || current.getVersion() == null)
            throw new IllegalStateException("Tenant migrations completed without a versioned schema");
        try {
            return Integer.parseInt(current.getVersion().getVersion());
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Tenant migration version must be an integer", exception);
        }
    }
}