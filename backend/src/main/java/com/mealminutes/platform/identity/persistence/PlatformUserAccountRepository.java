package com.mealminutes.platform.identity.persistence;

import com.mealminutes.platform.identity.domain.PlatformUserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PlatformUserAccountRepository extends JpaRepository<PlatformUserAccount, String> {
    Optional<PlatformUserAccount> findByEmail(String email);
    boolean existsByEmail(String email);
}