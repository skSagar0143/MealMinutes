package com.mealminutes.platform.identity.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshSessionTests {
    @Test void validatesInputsAndRevokesSession() {
        var user = new PlatformUserAccount("user@example.com", "$2a$12$hash", PlatformRole.CUSTOMER);
        var session = new RefreshSession(user, "a".repeat(64), Instant.now().plusSeconds(3600));
        assertThat(session.getRevokedAt()).isNull();
        session.revoke("replacement-id");
        assertThat(session.getRevokedAt()).isNotNull();
        assertThat(session.getReplacedBySessionId()).isEqualTo("replacement-id");
        session.revoke(null);
        assertThat(session.getReplacedBySessionId()).isNull();
    }
    @Test void rejectsInvalidRefreshSessionInput() {
        var user = new PlatformUserAccount("user@example.com", "$2a$12$hash", PlatformRole.CUSTOMER);
        assertThatThrownBy(() -> new RefreshSession(null, "a".repeat(64), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RefreshSession(user, "not-a-hash", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RefreshSession(user, "a".repeat(64), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}