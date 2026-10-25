package com.mealminutes.platform.tenant.persistence;

/**
 * Resolves credentials from a secret manager using a reference from the
 * platform registry. Implementations must not accept raw credentials from HTTP
 * requests or log passwords.
 */
@FunctionalInterface
public interface TenantDatabaseCredentialsProvider {
    DatabaseCredentials resolve(String secretReference);

    record DatabaseCredentials(String username, String password) {
        public DatabaseCredentials {
            if (username == null || username.isBlank()) {
                throw new IllegalArgumentException("Database username is required");
            }
            if (password == null || password.isBlank()) {
                throw new IllegalArgumentException("Database password is required");
            }
        }
    }
}