package com.mealminutes.platform.identity.service;

import com.mealminutes.platform.identity.domain.AccountStatus;
import com.mealminutes.platform.identity.domain.PlatformUserAccount;
import com.mealminutes.platform.identity.domain.RefreshSession;
import com.mealminutes.platform.identity.persistence.PlatformUserAccountRepository;
import com.mealminutes.platform.identity.persistence.RefreshSessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "mealminutes.security", name = "enabled", havingValue = "true")
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;
    private final PlatformUserAccountRepository users;
    private final RefreshSessionRepository refreshSessions;
    private final String issuer;
    private final long accessTtlSeconds;
    private final long refreshTtlSeconds;

    public AuthService(AuthenticationManager authenticationManager, JwtEncoder jwtEncoder,
                       PlatformUserAccountRepository users, RefreshSessionRepository refreshSessions,
                       @Value("${mealminutes.security.issuer}") String issuer,
                       @Value("${mealminutes.security.access-token-ttl-seconds:900}") long accessTtlSeconds,
                       @Value("${mealminutes.security.refresh-token-ttl-seconds:2592000}") long refreshTtlSeconds) {
        if (accessTtlSeconds < 60 || accessTtlSeconds > 3600) throw new IllegalArgumentException("Access token TTL must be 60-3600 seconds");
        if (refreshTtlSeconds < 3600 || refreshTtlSeconds > 7776000) throw new IllegalArgumentException("Refresh token TTL must be 1 hour to 90 days");
        this.authenticationManager = authenticationManager; this.jwtEncoder = jwtEncoder;
        this.users = users; this.refreshSessions = refreshSessions; this.issuer = issuer;
        this.accessTtlSeconds = accessTtlSeconds; this.refreshTtlSeconds = refreshTtlSeconds;
    }

    @Transactional
    public IssuedTokens login(String email, String password) {
        if (email == null || password == null || password.isEmpty()) throw new BadCredentialsException("Invalid credentials");
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(normalized, password));
        PlatformUserAccount user = users.findByEmail(normalized)
                .filter(account -> account.getAccountStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        return issueTokens(user, Instant.now());
    }

    @Transactional
    public IssuedTokens refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) throw new BadCredentialsException("Refresh token is required");
        Instant now = Instant.now();
        RefreshSession current = refreshSessions.findByTokenHashForUpdate(hashRefreshToken(rawRefreshToken))
                .filter(session -> session.getRevokedAt() == null && session.getExpiresAt().isAfter(now))
                .orElseThrow(() -> new BadCredentialsException("Refresh token is invalid or expired"));
        PlatformUserAccount user = current.getUser();
        if (user.getAccountStatus() != AccountStatus.ACTIVE) throw new BadCredentialsException("Account is not active");
        String nextRaw = newRefreshToken();
        RefreshSession next = new RefreshSession(user, hashRefreshToken(nextRaw), now.plusSeconds(refreshTtlSeconds));
        current.revoke(next.getSessionId());
        refreshSessions.save(current);
        refreshSessions.save(next);
        return new IssuedTokens(createAccessToken(user, now), nextRaw, accessTtlSeconds, refreshTtlSeconds);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) return;
        refreshSessions.findByTokenHashForUpdate(hashRefreshToken(rawRefreshToken))
                .filter(session -> session.getRevokedAt() == null)
                .ifPresent(session -> { session.revoke(null); refreshSessions.save(session); });
    }

    private IssuedTokens issueTokens(PlatformUserAccount user, Instant now) {
        String raw = newRefreshToken();
        refreshSessions.save(new RefreshSession(user, hashRefreshToken(raw), now.plusSeconds(refreshTtlSeconds)));
        return new IssuedTokens(createAccessToken(user, now), raw, accessTtlSeconds, refreshTtlSeconds);
    }

    private String createAccessToken(PlatformUserAccount user, Instant now) {
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).issuedAt(now)
                .expiresAt(now.plusSeconds(accessTtlSeconds)).subject(user.getUserId())
                .id(UUID.randomUUID().toString()).claim("email", user.getEmail())
                .claim("roles", List.of(user.getPlatformRole().name())).claim("token_use", "access").build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId("mealminutes-rs256-v1").build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static String newRefreshToken() {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    static String hashRefreshToken(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }
    public record IssuedTokens(String accessToken, String refreshToken, long accessExpiresInSeconds, long refreshExpiresInSeconds) { }
}