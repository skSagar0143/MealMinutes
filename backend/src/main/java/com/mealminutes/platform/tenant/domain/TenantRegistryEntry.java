package com.mealminutes.platform.tenant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "platform_tenant_registry")
public class TenantRegistryEntry {
    private static final Pattern SLUG = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");
    private static final Pattern DATABASE_NAME = Pattern.compile("[a-z][a-z0-9_]{0,63}");

    @Id
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 36)
    private String tenantId;

    @Column(name = "slug", nullable = false, unique = true, length = 63)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 32)
    private TenantLifecycleStatus lifecycleStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "provisioning_status", nullable = false, length = 24)
    private ProvisioningStatus provisioningStatus;

    @Column(name = "tenant_database_name", length = 64)
    private String tenantDatabaseName;

    @Column(name = "database_secret_reference", length = 255)
    private String databaseSecretReference;

    @Column(name = "schema_version")
    private Integer schemaVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TenantRegistryEntry() { }

    public TenantRegistryEntry(String slug) {
        if (slug == null || !SLUG.matcher(slug).matches()) {
            throw new IllegalArgumentException("Tenant slug must be lowercase alphanumeric with internal hyphens");
        }
        this.tenantId = UUID.randomUUID().toString();
        this.slug = slug;
        this.lifecycleStatus = TenantLifecycleStatus.REGISTERED;
        this.provisioningStatus = ProvisioningStatus.NOT_STARTED;
    }

    public void transitionTo(TenantLifecycleStatus target) {
        TenantLifecyclePolicy.requireAllowed(lifecycleStatus, target);
        if (target == TenantLifecycleStatus.ACTIVE
                && (provisioningStatus != ProvisioningStatus.SUCCEEDED
                || tenantDatabaseName == null || databaseSecretReference == null)) {
            throw new IllegalStateException("Tenant cannot become active before provisioning succeeds");
        }
        lifecycleStatus = target;
    }

    public void beginProvisioning() {
        if (lifecycleStatus != TenantLifecycleStatus.APPROVED
                && lifecycleStatus != TenantLifecycleStatus.FAILED) {
            throw new IllegalStateException("Only approved or failed tenants can be provisioned");
        }
        lifecycleStatus = TenantLifecycleStatus.PROVISIONING;
        provisioningStatus = ProvisioningStatus.IN_PROGRESS;
    }

    public void completeProvisioning(String databaseName, String secretReference, int migratedSchemaVersion) {
        if (lifecycleStatus != TenantLifecycleStatus.PROVISIONING
                || provisioningStatus != ProvisioningStatus.IN_PROGRESS) {
            throw new IllegalStateException("Provisioning has not been started");
        }
        if (databaseName == null || !DATABASE_NAME.matcher(databaseName).matches()) {
            throw new IllegalArgumentException("Invalid tenant database name");
        }
        if (secretReference == null || secretReference.isBlank() || secretReference.length() > 255) {
            throw new IllegalArgumentException("A secret-manager reference is required; never store a password here");
        }
        if (migratedSchemaVersion < 1) {
            throw new IllegalArgumentException("Schema version must be positive");
        }
        tenantDatabaseName = databaseName;
        databaseSecretReference = secretReference;
        schemaVersion = migratedSchemaVersion;
        provisioningStatus = ProvisioningStatus.SUCCEEDED;
    }

    public void failProvisioning() {
        if (lifecycleStatus != TenantLifecycleStatus.PROVISIONING
                || provisioningStatus != ProvisioningStatus.IN_PROGRESS) {
            throw new IllegalStateException("Provisioning has not been started");
        }
        provisioningStatus = ProvisioningStatus.FAILED;
        lifecycleStatus = TenantLifecycleStatus.FAILED;
    }

    @PrePersist
    void beforeInsert() {
        Instant now = Instant.now();
        if (tenantId == null) tenantId = UUID.randomUUID().toString();
        if (lifecycleStatus == null) lifecycleStatus = TenantLifecycleStatus.REGISTERED;
        if (provisioningStatus == null) provisioningStatus = ProvisioningStatus.NOT_STARTED;
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void beforeUpdate() { updatedAt = Instant.now(); }

    public String getTenantId() { return tenantId; }
    public String getSlug() { return slug; }
    public TenantLifecycleStatus getLifecycleStatus() { return lifecycleStatus; }
    public ProvisioningStatus getProvisioningStatus() { return provisioningStatus; }
    public String getTenantDatabaseName() { return tenantDatabaseName; }
    public String getDatabaseSecretReference() { return databaseSecretReference; }
    public Integer getSchemaVersion() { return schemaVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}