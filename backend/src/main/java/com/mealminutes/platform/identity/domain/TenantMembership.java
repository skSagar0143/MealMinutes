package com.mealminutes.platform.identity.domain;

import com.mealminutes.platform.tenant.domain.TenantRegistryEntry;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "platform_tenant_membership",
       uniqueConstraints = @UniqueConstraint(name = "uk_platform_tenant_membership_user", columnNames = {"tenant_id", "user_id"}))
public class TenantMembership {
    @Id @Column(name = "membership_id", nullable = false, updatable = false, length = 36)
    private String membershipId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "tenant_id", nullable = false)
    private TenantRegistryEntry tenant;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false)
    private PlatformUserAccount user;
    @Enumerated(EnumType.STRING) @Column(name = "tenant_role", nullable = false, length = 32)
    private TenantMembershipRole tenantRole;
    @Enumerated(EnumType.STRING) @Column(name = "membership_status", nullable = false, length = 16)
    private MembershipStatus membershipStatus;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TenantMembership() { }
    public TenantMembership(TenantRegistryEntry tenant, PlatformUserAccount user, TenantMembershipRole role) {
        if (tenant == null || user == null || role == null) throw new IllegalArgumentException("Tenant, user and role are required");
        this.membershipId = UUID.randomUUID().toString();
        this.tenant = tenant; this.user = user; this.tenantRole = role; this.membershipStatus = MembershipStatus.ACTIVE;
    }
    @PrePersist void beforeInsert() {
        if (membershipId == null) membershipId = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
        if (membershipStatus == null) membershipStatus = MembershipStatus.ACTIVE;
    }
    public String getMembershipId() { return membershipId; }
    public TenantRegistryEntry getTenant() { return tenant; }
    public PlatformUserAccount getUser() { return user; }
    public TenantMembershipRole getTenantRole() { return tenantRole; }
    public MembershipStatus getMembershipStatus() { return membershipStatus; }
    public void suspend() { membershipStatus = MembershipStatus.SUSPENDED; }
    public void reactivate() { membershipStatus = MembershipStatus.ACTIVE; }
}