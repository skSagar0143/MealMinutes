package com.mealminutes.platform.tenant.persistence;

import com.mealminutes.platform.tenant.domain.DomainVerificationStatus;
import com.mealminutes.platform.tenant.domain.TenantDomain;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantDomainRepository extends JpaRepository<TenantDomain, String> {
    @EntityGraph(attributePaths = "tenant")
    Optional<TenantDomain> findByHostnameAndVerificationStatus(
            String hostname, DomainVerificationStatus verificationStatus);

    @EntityGraph(attributePaths = "tenant")
    Optional<TenantDomain> findByHostname(String hostname);

    boolean existsByHostname(String hostname);

    Optional<TenantDomain> findFirstByTenant_TenantIdAndVerificationStatus(
            String tenantId, DomainVerificationStatus verificationStatus);
}