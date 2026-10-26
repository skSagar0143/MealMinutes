package com.mealminutes.platform.tenant.service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

public final class JdbcTenantDatabaseProvisioner implements TenantDatabaseProvisioner {
    private static final Pattern SLUG = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");
    private static final SecureRandom RANDOM = new SecureRandom();
    private final TenantDatabaseAdmin admin;
    private final TenantCredentialSecretStore secretStore;
    private final TenantSchemaMigrator migrator;

    public JdbcTenantDatabaseProvisioner(TenantDatabaseAdmin admin,
                                         TenantCredentialSecretStore secretStore,
                                         TenantSchemaMigrator migrator) {
        this.admin = admin;
        this.secretStore = secretStore;
        this.migrator = migrator;
    }

    @Override
    public ProvisionedDatabase provision(String tenantSlug) {
        if (tenantSlug == null || !SLUG.matcher(tenantSlug).matches())
            throw new IllegalArgumentException("Invalid tenant slug");
        String suffix = hashSuffix(tenantSlug);
        String safeSlug = tenantSlug.replace('-', '_');
        String databaseName = "tenant_" + safeSlug.substring(0, Math.min(48, safeSlug.length())) + "_" + suffix;
        String username = "mm_" + safeSlug.substring(0, Math.min(20, safeSlug.length())) + "_" + suffix.substring(0, 6);
        String secretReference = "secret://mealminutes/tenants/" + tenantSlug + "/database";
        var credentials = secretStore.createOrGet(secretReference, username, randomPassword());
        if (!username.equals(credentials.username()))
            throw new IllegalStateException("Stored tenant DB username does not match tenant identity");
        admin.ensureDatabase(databaseName);
        admin.ensureUserAndGrant(databaseName, credentials.username(), credentials.password());
        int version = migrator.migrate(databaseName);
        return new ProvisionedDatabase(databaseName, secretReference, version);
    }

    private static String hashSuffix(String slug) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(slug.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest).substring(0, 8);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
    private static String randomPassword() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}