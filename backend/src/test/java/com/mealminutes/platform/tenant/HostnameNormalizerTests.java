package com.mealminutes.platform.tenant;

import com.mealminutes.platform.tenant.domain.HostnameNormalizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HostnameNormalizerTests {
    @Test
    void lowercasesAndTrimsTrailingDot() {
        assertThat(HostnameNormalizer.normalize("  ABCFOOD.Example.COM. "))
                .isEqualTo("abcfood.example.com");
    }

    @Test
    void convertsInternationalHostnameToAscii() {
        assertThat(HostnameNormalizer.normalize("bücher.example"))
                .isEqualTo("xn--bcher-kva.example");
    }

    @Test
    void rejectsMissingOrMalformedHostnames() {
        assertThatThrownBy(() -> HostnameNormalizer.normalize(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HostnameNormalizer.normalize(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HostnameNormalizer.normalize("abc.example:8080"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HostnameNormalizer.normalize("abc.example/path"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HostnameNormalizer.normalize("-bad.example"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HostnameNormalizer.normalize("a..example"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}