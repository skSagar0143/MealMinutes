package com.mealminutes.platform.identity.config;

import com.mealminutes.platform.identity.domain.PlatformRole;
import com.mealminutes.platform.identity.domain.PlatformUserAccount;
import com.mealminutes.platform.identity.persistence.PlatformUserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "BOOTSTRAP_ADMIN_EMAIL")
public class PlatformAdminBootstrapConfiguration {
    @Bean
    ApplicationRunner bootstrapPlatformAdmin(@Value("${BOOTSTRAP_ADMIN_EMAIL}") String email,
                                              @Value("${BOOTSTRAP_ADMIN_PASSWORD}") String password,
                                              PlatformUserAccountRepository users,
                                              PasswordEncoder passwordEncoder) {
        return args -> {
            if (password == null || password.length() < 16 || password.length() > 72)
                throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD must be 16-72 characters");
            String normalized = email.trim().toLowerCase(java.util.Locale.ROOT);
            if (!users.existsByEmail(normalized)) {
                users.saveAndFlush(new PlatformUserAccount(normalized, passwordEncoder.encode(password), PlatformRole.PLATFORM_ADMIN));
            }
        };
    }
}