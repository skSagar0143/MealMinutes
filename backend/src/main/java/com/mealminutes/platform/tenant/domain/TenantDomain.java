package com.mealminutes.platform.tenant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "platform_tenant_domain")
public class TenantDomain {
    @Id
    @Column(name = "domain_id", nullable = false, updatable = false, length = 36)
    private String domainId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private TenantRegistryEntry tenant;

    @Column(name = "hostname", nullable = false, unique = true, length = 253)
    private String hostname;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 16)
    private DomainVerificationStatus verificationStatus;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TenantDomain() { }

    public TenantDomain(TenantRegistryEntry tenant, String hostname) {
        if (tenant == null) {
            throw new IllegalArgumentException("Tenant is required");
        }
        this.tenant = tenant;
        this.hostname = HostnameNormalizer.normalize(hostname);
        this.verificationStatus = DomainVerificationStatus.PENDING;
    }

    public void markVerified() {
        if (verificationStatus == DomainVerificationStatus.REVOKED) {
            throw new IllegalStateException("A revoked domain cannot be verified again; register it through the verification workflow");
        }
        verificationStatus = DomainVerificationStatus.VERIFIED;
    }

    public void revoke() {
        verificationStatus = DomainVerificationStatus.REVOKED;
    }

    public void markPrimary() {
        primary = true;
    }

    @PrePersist
    void beforeInsert() {
        if (domainId == null) domainId = UUID.randomUUID().toString();
        if (verificationStatus == null) verificationStatus = DomainVerificationStatus.PENDING;
        if (createdAt == null) createdAt = Instant.now();
    }

    public String getDomainId() { return domainId; }
    public TenantRegistryEntry getTenant() { return tenant; }
    public String getHostname() { return hostname; }
    public DomainVerificationStatus getVerificationStatus() { return verificationStatus; }
    public boolean isPrimary() { return primary; }
    public Instant getCreatedAt() { return createdAt; }
}