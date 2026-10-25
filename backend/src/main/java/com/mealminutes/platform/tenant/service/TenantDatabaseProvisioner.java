package com.mealminutes.platform.tenant.service;

/**
 * Infrastructure port for privileged tenant database creation, secret storage,
 * and tenant Flyway migrations. Implementations must use restricted database
 * administration credentials, validated identifiers, and idempotent DDL.
 */
@FunctionalInterface
public interface TenantDatabaseProvisioner {
    ProvisionedDatabase provision(String tenantSlug);

    record ProvisionedDatabase(String databaseName, String secretReference, int schemaVersion) {
        public ProvisionedDatabase {
            if (databaseName == null || !databaseName.matches("[a-z][a-z0-9_]{0,63}")) {
                throw new IllegalArgumentException("Provisioner returned an invalid database name");
            }
            if (secretReference == null || secretReference.isBlank() || secretReference.length() > 255) {
                throw new IllegalArgumentException("Provisioner must return a secret-manager reference");
            }
            if (schemaVersion < 1) {
                throw new IllegalArgumentException("Provisioner must return a positive schema version");
            }
        }
    }
}