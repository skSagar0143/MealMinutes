package com.mealminutes.platform.tenant.service;

import org.junit.jupiter.api.Test;
import javax.naming.NamingException;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class DnsTxtRecordVerifierTests {
    @Test void matchesExpectedTxtRecordAfterRemovingDnsQuotes() {
        DnsTxtRecordVerifier verifier = new DnsTxtRecordVerifier(name -> {
            assertThat(name).isEqualTo("_mealminutes-verification.shop.example.com");
            return List.of("\"mealminutes-verification=token-123\"");
        });
        assertThat(verifier.hasTxtRecord("_mealminutes-verification.shop.example.com",
                "mealminutes-verification=token-123")).isTrue();
    }
    @Test void rejectsMissingOrMismatchedTxtRecords() {
        DnsTxtRecordVerifier empty = new DnsTxtRecordVerifier(name -> List.of());
        assertThat(empty.hasTxtRecord("shop.example.com", "expected")).isFalse();
        DnsTxtRecordVerifier wrong = new DnsTxtRecordVerifier(name -> List.of("other=value"));
        assertThat(wrong.hasTxtRecord("shop.example.com", "expected")).isFalse();
    }
    @Test void safelyTreatsDnsFailuresAndInvalidArgumentsAsUnverified() {
        DnsTxtRecordVerifier broken = new DnsTxtRecordVerifier(name -> { throw new NamingException("timeout"); });
        assertThat(broken.hasTxtRecord("shop.example.com", "expected")).isFalse();
        assertThat(broken.hasTxtRecord(" ", "expected")).isFalse();
        assertThat(broken.hasTxtRecord("shop.example.com", " ")).isFalse();
    }
}