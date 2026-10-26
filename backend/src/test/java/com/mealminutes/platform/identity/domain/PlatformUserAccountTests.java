package com.mealminutes.platform.identity.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformUserAccountTests {
    @Test void normalizesEmailAndSupportsStatusTransitions() {
        PlatformUserAccount account = new PlatformUserAccount(" Admin@Example.com ", "$2a$12$test", PlatformRole.PLATFORM_ADMIN);
        assertThat(account.getEmail()).isEqualTo("admin@example.com");
        assertThat(account.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        account.lock(); assertThat(account.getAccountStatus()).isEqualTo(AccountStatus.LOCKED);
        account.activate(); assertThat(account.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        account.disable(); assertThat(account.getAccountStatus()).isEqualTo(AccountStatus.DISABLED);
    }
    @Test void rejectsInvalidAccountInput() {
        assertThatThrownBy(() -> new PlatformUserAccount("not-an-email", "$2a$12$test", PlatformRole.CUSTOMER)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlatformUserAccount("a@example.com", "", PlatformRole.CUSTOMER)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlatformUserAccount("a@example.com", "$2a$12$test", null)).isInstanceOf(IllegalArgumentException.class);
    }
}