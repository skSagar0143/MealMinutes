package com.mealminutes.platform.identity.web;

import com.mealminutes.platform.identity.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpHeaders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AuthControllerTests {
    @Test void loginReturnsAccessTokenAndHttpOnlyRefreshCookie() {
        AuthService service = mock(AuthService.class);
        when(service.login("user@example.com", "long-password"))
                .thenReturn(new AuthService.IssuedTokens("access-token", "refresh-token-value", 900, 2592000));
        AuthController controller = new AuthController(service, true, "https://app.example.com");

        var response = controller.login(new AuthController.LoginRequest("user@example.com", "long-password"),
                "https://app.example.com");

        assertThat(response.getBody().accessToken()).isEqualTo("access-token");
        String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(cookie).contains("HttpOnly").contains("Secure").contains("SameSite=Strict")
                .contains("Path=/api/v1/auth");
    }

    @Test void refreshRejectsUntrustedOrMissingOriginBeforeCallingService() {
        AuthService service = mock(AuthService.class);
        AuthController controller = new AuthController(service, true, "https://app.example.com");

        assertThatThrownBy(() -> controller.refresh("refresh-token", "https://attacker.example"))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(service);
    }

    @Test void logoutClearsCookieAfterRevocation() {
        AuthService service = mock(AuthService.class);
        AuthController controller = new AuthController(service, true, "https://app.example.com");

        var response = controller.logout("refresh-token", "https://app.example.com");

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
        verify(service).logout("refresh-token");
    }
}