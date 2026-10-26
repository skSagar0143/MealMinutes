package com.mealminutes.platform.identity.persistence;

import com.mealminutes.platform.identity.domain.MembershipStatus;
import com.mealminutes.platform.identity.domain.TenantMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantMembershipRepository extends JpaRepository<TenantMembership, String> {
    Optional<TenantMembership> findByTenant_TenantIdAndUser_UserIdAndMembershipStatus(
            String tenantId, String userId, MembershipStatus membershipStatus);

    boolean existsByTenant_TenantIdAndUser_UserIdAndMembershipStatus(
            String tenantId, String userId, MembershipStatus membershipStatus);
}