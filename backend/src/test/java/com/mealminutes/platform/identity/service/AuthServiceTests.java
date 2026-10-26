package com.mealminutes.platform.identity.service;

import com.mealminutes.platform.identity.domain.PlatformUserAccount;
import com.mealminutes.platform.identity.domain.PlatformRole;
import com.mealminutes.platform.identity.domain.RefreshSession;
import com.mealminutes.platform.identity.persistence.PlatformUserAccountRepository;
import com.mealminutes.platform.identity.persistence.RefreshSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
        AuthService service = service(manager, mock(JwtEncoder.class),
                mock(PlatformUserAccountRepository.class), mock(RefreshSessionRepository.class));
        assertThatThrownBy(() -> service.login(" ", "password")).isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(manager);
    }

    @Test void loginCreatesHashedRefreshSessionAndReturnsAccessToken() {
        AuthenticationManager manager = mock(AuthenticationManager.class);
        JwtEncoder encoder = encoder();
        PlatformUserAccountRepository users = mock(PlatformUserAccountRepository.class);
        RefreshSessionRepository sessions = mock(RefreshSessionRepository.class);
        PlatformUserAccount user = user();
        when(manager.authenticate(any(Authentication.class))).thenReturn(mock(Authentication.class));
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(user));
        when(sessions.save(any(RefreshSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var tokens = service(manager, encoder, users, sessions).login(" Admin@Example.com ", "Strong-password!");

        assertThat(tokens.accessToken()).isEqualTo("signed-access-token");
        assertThat(tokens.refreshToken()).hasSizeGreaterThan(30);
        var captor = org.mockito.ArgumentCaptor.forClass(RefreshSession.class);
        verify(sessions).save(captor.capture());
        assertThat(captor.getValue().getTokenHash()).hasSize(64).isNotEqualTo(tokens.refreshToken());
    }

    @Test void refreshRotatesSessionAndRevokesOldToken() {
        AuthenticationManager manager = mock(AuthenticationManager.class);
        JwtEncoder encoder = encoder();
        PlatformUserAccount user = user();
        RefreshSessionRepository sessions = mock(RefreshSessionRepository.class);
        String raw = "existing-refresh-token";
        RefreshSession current = new RefreshSession(user, AuthService.hashRefreshToken(raw), Instant.now().plusSeconds(3600));
        when(sessions.findByTokenHashForUpdate(AuthService.hashRefreshToken(raw))).thenReturn(Optional.of(current));
        when(sessions.save(any(RefreshSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var tokens = service(manager, encoder, mock(PlatformUserAccountRepository.class), sessions).refresh(raw);

        assertThat(tokens.accessToken()).isEqualTo("signed-access-token");
        assertThat(tokens.refreshToken()).isNotEqualTo(raw);
        assertThat(current.getRevokedAt()).isNotNull();
        assertThat(current.getReplacedBySessionId()).isNotBlank();
        verify(sessions, times(2)).save(any(RefreshSession.class));
    }

    @Test void refreshRejectsUnknownAndExpiredTokens() {
        RefreshSessionRepository sessions = mock(RefreshSessionRepository.class);
        when(sessions.findByTokenHashForUpdate(any())).thenReturn(Optional.empty());
        AuthService service = service(mock(AuthenticationManager.class), encoder(),
                mock(PlatformUserAccountRepository.class), sessions);
        assertThatThrownBy(() -> service.refresh("unknown")).isInstanceOf(BadCredentialsException.class);

        RefreshSession expired = new RefreshSession(user(), AuthService.hashRefreshToken("expired"),
                Instant.now().minusSeconds(1));
        when(sessions.findByTokenHashForUpdate(AuthService.hashRefreshToken("expired"))).thenReturn(Optional.of(expired));
        assertThatThrownBy(() -> service.refresh("expired")).isInstanceOf(BadCredentialsException.class);
    }

    @Test void logoutRevokesKnownSessionAndIgnoresMissingToken() {
        RefreshSessionRepository sessions = mock(RefreshSessionRepository.class);
        String raw = "logout-refresh-token";
        RefreshSession session = new RefreshSession(user(), AuthService.hashRefreshToken(raw), Instant.now().plusSeconds(3600));
        when(sessions.findByTokenHashForUpdate(AuthService.hashRefreshToken(raw))).thenReturn(Optional.of(session));
        when(sessions.save(any(RefreshSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AuthService service = service(mock(AuthenticationManager.class), encoder(),
                mock(PlatformUserAccountRepository.class), sessions);

        service.logout(raw);
        service.logout(null);

        assertThat(session.getRevokedAt()).isNotNull();
        verify(sessions).save(session);
    }

    private AuthService service(AuthenticationManager manager, JwtEncoder encoder,
                                PlatformUserAccountRepository users, RefreshSessionRepository sessions) {
        return new AuthService(manager, encoder, users, sessions, "https://mealminutes.local", 900, 2592000);
    }
    private JwtEncoder encoder() {
        JwtEncoder encoder = mock(JwtEncoder.class);
        Jwt jwt = mock(Jwt.class);
        when(jwt.getTokenValue()).thenReturn("signed-access-token");
        when(encoder.encode(any(JwtEncoderParameters.class))).thenReturn(jwt);
        return encoder;
    }
    private PlatformUserAccount user() {
        return new PlatformUserAccount("admin@example.com", "$2a$12$fake-hash", PlatformRole.PLATFORM_ADMIN);
    }
}