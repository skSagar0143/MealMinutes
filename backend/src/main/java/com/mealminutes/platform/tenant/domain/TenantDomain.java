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

    @Column(name = "verification_challenge_hash", length = 64)
    private String verificationChallengeHash;

    @Column(name = "verification_expires_at")
    private Instant verificationExpiresAt;

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

    public void setVerificationChallenge(String challenge, Instant expiresAt) {
        if (challenge == null || challenge.isBlank() || expiresAt == null || !expiresAt.isAfter(Instant.now())) {
            throw new IllegalArgumentException("A non-empty challenge and future expiry are required");
        }
        this.verificationChallengeHash = hashChallenge(challenge);
        this.verificationExpiresAt = expiresAt;
    }

    public boolean matchesVerificationChallenge(String challenge, Instant now) {
        if (challenge == null || challenge.isBlank() || verificationChallengeHash == null
                || verificationExpiresAt == null || now == null || !verificationExpiresAt.isAfter(now)) {
            return false;
        }
        return java.security.MessageDigest.isEqual(
                verificationChallengeHash.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                hashChallenge(challenge).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private static String hashChallenge(String challenge) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(challenge.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public void markVerified() {
        if (verificationStatus == DomainVerificationStatus.REVOKED) {
            throw new IllegalStateException("A revoked domain cannot be verified again; register it through the verification workflow");
        }
        verificationStatus = DomainVerificationStatus.VERIFIED;
        verificationChallengeHash = null;
        verificationExpiresAt = null;
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
    public Instant getVerificationExpiresAt() { return verificationExpiresAt; }
    public Instant getCreatedAt() { return createdAt; }
}