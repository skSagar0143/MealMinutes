package com.mealminutes.platform.identity.service;

import com.mealminutes.platform.identity.domain.AccountStatus;
import com.mealminutes.platform.identity.domain.PlatformRole;
import com.mealminutes.platform.identity.domain.PlatformUserAccount;
import com.mealminutes.platform.identity.persistence.PlatformUserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PlatformUserDetailsServiceTests {
    @Test void loadsNormalizedEmailAndRole() {
        PlatformUserAccountRepository repo = mock(PlatformUserAccountRepository.class);
        var account = new PlatformUserAccount("admin@example.com", "$2a$12$hash", PlatformRole.PLATFORM_ADMIN);
        when(repo.findByEmail("admin@example.com")).thenReturn(Optional.of(account));
        var details = new PlatformUserDetailsService(repo).loadUserByUsername(" Admin@Example.com ");
        assertThat(details.getUsername()).isEqualTo("admin@example.com");
        assertThat(details.getAuthorities()).extracting("authority").contains("ROLE_PLATFORM_ADMIN");
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.isAccountNonLocked()).isTrue();
    }
    @Test void reflectsLockedAndDisabledAccountStates() {
        PlatformUserAccountRepository repo = mock(PlatformUserAccountRepository.class);
        var locked = new PlatformUserAccount("locked@example.com", "$2a$12$hash", PlatformRole.CUSTOMER);
        locked.lock();
        when(repo.findByEmail("locked@example.com")).thenReturn(Optional.of(locked));
        assertThat(new PlatformUserDetailsService(repo).loadUserByUsername("locked@example.com").isAccountNonLocked()).isFalse();
        var disabled = new PlatformUserAccount("disabled@example.com", "$2a$12$hash", PlatformRole.CUSTOMER);
        disabled.disable();
        when(repo.findByEmail("disabled@example.com")).thenReturn(Optional.of(disabled));
        assertThat(new PlatformUserDetailsService(repo).loadUserByUsername("disabled@example.com").isEnabled()).isFalse();
    }
    @Test void rejectsUnknownEmail() {
        PlatformUserAccountRepository repo = mock(PlatformUserAccountRepository.class);
        when(repo.findByEmail("")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> new PlatformUserDetailsService(repo).loadUserByUsername(null))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}