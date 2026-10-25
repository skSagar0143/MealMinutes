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

@Entity
@Table(name = "platform_tenant_registry")
public class TenantRegistryEntry {

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

    protected TenantRegistryEntry() {
    }

    public TenantRegistryEntry(String slug) {
        this.slug = slug;
        this.lifecycleStatus = TenantLifecycleStatus.REGISTERED;
        this.provisioningStatus = ProvisioningStatus.NOT_STARTED;
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
    void beforeUpdate() {
        updatedAt = Instant.now();
    }

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
