package com.mealminutes.platform.tenant.persistence;

import com.mealminutes.platform.tenant.domain.DomainVerificationStatus;
import com.mealminutes.platform.tenant.domain.TenantDomain;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantDomainRepository extends JpaRepository<TenantDomain, String> {
    Optional<TenantDomain> findByHostnameAndVerificationStatus(String hostname, DomainVerificationStatus verificationStatus);
    boolean existsByHostname(String hostname);
}
