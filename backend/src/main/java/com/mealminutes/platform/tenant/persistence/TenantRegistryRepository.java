package com.mealminutes.platform.tenant.persistence;

import com.mealminutes.platform.tenant.domain.TenantRegistryEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantRegistryRepository extends JpaRepository<TenantRegistryEntry, String> {
    Optional<TenantRegistryEntry> findBySlug(String slug);
    boolean existsBySlug(String slug);
}
