package com.mealminutes.platform.tenant.service;

/** Production implementations should use a managed secret manager. */
public interface TenantCredentialSecretStore {
    TenantCredentials createOrGet(String secretReference, String proposedUsername, String proposedPassword);
    TenantCredentials get(String secretReference);

    record TenantCredentials(String username, String password) {
        public TenantCredentials {
            if (username == null || !username.matches("[a-zA-Z0-9_]{1,32}"))
                throw new IllegalArgumentException("Invalid tenant database username");
            if (password == null || !password.matches("[A-Za-z0-9_-]{32,128}"))
                throw new IllegalArgumentException("Invalid generated tenant database password");
        }
    }
}