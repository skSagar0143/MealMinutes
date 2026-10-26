package com.mealminutes.platform.identity.service;

import com.mealminutes.platform.identity.domain.MembershipStatus;
import com.mealminutes.platform.identity.persistence.TenantMembershipRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantMembershipAuthorizationService {
    private final TenantMembershipRepository memberships;
    public TenantMembershipAuthorizationService(TenantMembershipRepository memberships) { this.memberships = memberships; }
    @Transactional(readOnly = true)
    public boolean hasActiveMembership(String tenantId, String userId) {
        if (tenantId == null || userId == null) return false;
        return memberships.existsByTenant_TenantIdAndUser_UserIdAndMembershipStatus(
                tenantId, userId, MembershipStatus.ACTIVE);
    }
    public void requireActiveMembership(String tenantId, String userId) {
        if (!hasActiveMembership(tenantId, userId)) throw new AccessDeniedException("Active tenant membership is required");
    }
}