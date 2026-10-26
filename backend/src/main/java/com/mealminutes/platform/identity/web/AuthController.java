package com.mealminutes.platform.identity.web;

import com.mealminutes.platform.identity.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(prefix = "mealminutes.security", name = "enabled", havingValue = "true")
public class AuthController {
    private static final String REFRESH_COOKIE = "mealminutes_refresh";
    private final AuthService authService;
    private final boolean secureCookie;
    private final String allowedOrigin;

    public AuthController(AuthService authService,
                          @Value("${mealminutes.security.refresh-cookie-secure:true}") boolean secureCookie,
                          @Value("${mealminutes.security.allowed-origin:http://localhost:5173}") String allowedOrigin) {
        this.authService = authService; this.secureCookie = secureCookie; this.allowedOrigin = allowedOrigin;
    }

    @PostMapping("/login")
    public ResponseEntity<AccessTokenResponse> login(@Valid @RequestBody LoginRequest request,
                                                      @RequestHeader(name = "Origin", required = false) String origin) {
        requireTrustedOrigin(origin);
        var tokens = authService.login(request.email(), request.password());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie(tokens.refreshToken(), tokens.refreshExpiresInSeconds()).toString())
                .body(new AccessTokenResponse(tokens.accessToken(), "Bearer", tokens.accessExpiresInSeconds()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            @RequestHeader(name = "Origin", required = false) String origin) {
        requireTrustedOrigin(origin);
        var tokens = authService.refresh(refreshToken);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie(tokens.refreshToken(), tokens.refreshExpiresInSeconds()).toString())
                .body(new AccessTokenResponse(tokens.accessToken(), "Bearer", tokens.accessExpiresInSeconds()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            @RequestHeader(name = "Origin", required = false) String origin) {
        requireTrustedOrigin(origin);
        authService.logout(refreshToken);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).header(HttpHeaders.SET_COOKIE, cookie("", 0).toString()).build();
    }

    private void requireTrustedOrigin(String origin) {
        if (origin == null || !origin.equals(allowedOrigin)) {
            throw new org.springframework.security.access.AccessDeniedException("Request origin is not allowed");
        }
    }
    private ResponseCookie cookie(String value, long age) {
        return ResponseCookie.from(REFRESH_COOKIE, value).httpOnly(true).secure(secureCookie)
                .sameSite("Strict").path("/api/v1/auth").maxAge(age).build();
    }
    public record LoginRequest(@NotBlank @Email @Size(max = 254) String email,
                               @NotBlank @Size(max = 200) String password) { }
    public record AccessTokenResponse(String accessToken, String tokenType, long expiresIn) { }
}