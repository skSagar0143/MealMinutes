package com.mealminutes.platform.identity.service;

import com.mealminutes.platform.identity.persistence.PlatformUserAccountRepository;
import com.mealminutes.platform.identity.persistence.RefreshSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AuthServiceTests {
    @Test void refreshTokenHashIsDeterministicAndNotRaw() {
        String token = "random-refresh-token";
        String first = AuthService.hashRefreshToken(token);
        assertThat(first).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(AuthService.hashRefreshToken(token)).isEqualTo(first);
        assertThat(first).isNotEqualTo(token);
        assertThat(AuthService.hashRefreshToken(token + "x")).isNotEqualTo(first);
    }
    @Test void loginRejectsMissingCredentialsBeforeAuthentication() {
        AuthenticationManager manager = mock(AuthenticationManager.class);
        AuthService service = new AuthService(manager, mock(JwtEncoder.class),
                mock(PlatformUserAccountRepository.class), mock(RefreshSessionRepository.class),
                "https://mealminutes.local", 900, 2592000);
        assertThatThrownBy(() -> service.login("", "password")).isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(manager);
    }
}