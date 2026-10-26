package com.mealminutes.platform.identity.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "platform_refresh_session")
public class RefreshSession {
    @Id
    @Column(name = "session_id", nullable = false, updatable = false, length = 36)
    private String sessionId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private PlatformUserAccount user;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;
    @Column(name = "replaced_by_session_id", length = 36)
    private String replacedBySessionId;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RefreshSession() { }

    public RefreshSession(PlatformUserAccount user, String tokenHash, Instant expiresAt) {
        if (user == null || tokenHash == null || !tokenHash.matches("[0-9a-f]{64}") || expiresAt == null) {
            throw new IllegalArgumentException("Valid user, token hash and expiry are required");
        }
        this.sessionId = UUID.randomUUID().toString();
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }
    public void revoke(String replacementSessionId) {
        if (revokedAt == null) revokedAt = Instant.now();
        replacedBySessionId = replacementSessionId;
    }
    @PrePersist
    void beforeInsert() {
        if (sessionId == null) sessionId = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }
    public String getSessionId() { return sessionId; }
    public PlatformUserAccount getUser() { return user; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public String getReplacedBySessionId() { return replacedBySessionId; }
}