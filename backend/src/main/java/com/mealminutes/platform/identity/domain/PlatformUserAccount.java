package com.mealminutes.platform.identity.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "platform_user_account")
public class PlatformUserAccount {
    @Id
    @Column(name = "user_id", nullable = false, updatable = false, length = 36)
    private String userId;
    @Column(name = "email", nullable = false, unique = true, length = 254)
    private String email;
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;
    @Enumerated(EnumType.STRING)
    @Column(name = "platform_role", nullable = false, length = 32)
    private PlatformRole platformRole;
    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 16)
    private AccountStatus accountStatus;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PlatformUserAccount() { }

    public PlatformUserAccount(String email, String passwordHash, PlatformRole platformRole) {
        if (email == null || !email.trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || email.length() > 254) {
            throw new IllegalArgumentException("A valid email address is required");
        }
        if (passwordHash == null || passwordHash.isBlank() || passwordHash.length() > 100) {
            throw new IllegalArgumentException("A password hash is required");
        }
        if (platformRole == null) throw new IllegalArgumentException("Platform role is required");
        this.userId = UUID.randomUUID().toString();
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.passwordHash = passwordHash;
        this.platformRole = platformRole;
        this.accountStatus = AccountStatus.ACTIVE;
    }

    @PrePersist
    void beforeInsert() {
        if (userId == null) userId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (accountStatus == null) accountStatus = AccountStatus.ACTIVE;
    }
    @PreUpdate
    void beforeUpdate() { updatedAt = Instant.now(); }
    public String getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public PlatformRole getPlatformRole() { return platformRole; }
    public AccountStatus getAccountStatus() { return accountStatus; }
    public void lock() { accountStatus = AccountStatus.LOCKED; }
    public void disable() { accountStatus = AccountStatus.DISABLED; }
    public void activate() { accountStatus = AccountStatus.ACTIVE; }
}