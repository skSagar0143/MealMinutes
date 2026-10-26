package com.mealminutes.platform.identity.config;

import com.mealminutes.platform.identity.domain.PlatformRole;
import com.mealminutes.platform.identity.domain.PlatformUserAccount;
import com.mealminutes.platform.identity.persistence.PlatformUserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlatformAdminBootstrapConfigurationTests {
    @Test void createsAdminOnceWithEncodedPassword() throws Exception {
        PlatformUserAccountRepository users = mock(PlatformUserAccountRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(users.existsByEmail("admin@example.com")).thenReturn(false);
        when(encoder.encode("strong-password-12345")).thenReturn("encoded-hash");
        when(users.saveAndFlush(any(PlatformUserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ApplicationRunner runner = new PlatformAdminBootstrapConfiguration().bootstrapPlatformAdmin(
                "Admin@Example.com", "strong-password-12345", users, encoder);

        runner.run(new DefaultApplicationArguments(new String[0]));

        var captor = org.mockito.ArgumentCaptor.forClass(PlatformUserAccount.class);
        verify(users).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("admin@example.com");
        assertThat(captor.getValue().getPlatformRole()).isEqualTo(PlatformRole.PLATFORM_ADMIN);
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("encoded-hash");
    }

    @Test void doesNotReplaceExistingAdminAndRejectsWeakBootstrapPassword() throws Exception {
        PlatformUserAccountRepository users = mock(PlatformUserAccountRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(users.existsByEmail("admin@example.com")).thenReturn(true);
        ApplicationRunner existing = new PlatformAdminBootstrapConfiguration().bootstrapPlatformAdmin(
                "admin@example.com", "strong-password-12345", users, encoder);
        existing.run(new DefaultApplicationArguments(new String[0]));
        verify(users, never()).saveAndFlush(any());

        ApplicationRunner weak = new PlatformAdminBootstrapConfiguration().bootstrapPlatformAdmin(
                "admin@example.com", "weak", users, encoder);
        assertThatThrownBy(() -> weak.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class);
    }
}