package com.mealminutes.platform.tenant.web;

import com.mealminutes.platform.tenant.domain.TenantDomain;
import com.mealminutes.platform.tenant.domain.TenantRegistryEntry;
import com.mealminutes.platform.tenant.service.TenantDomainVerificationService;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TenantDomainVerificationControllerTests {
    @Test void returnsVerifiedStatusAfterServiceCompletesDnsProof() {
        TenantDomainVerificationService service = mock(TenantDomainVerificationService.class);
        TenantDomain domain = new TenantDomain(new TenantRegistryEntry("abc-food"), "shop.example.com");
        domain.markVerified();
        when(service.verify("shop.example.com", "token")).thenReturn(domain);
        var response = new TenantDomainVerificationController(service).verify(
                new TenantDomainVerificationController.VerifyDomainRequest("shop.example.com", "token"));
        assertThat(response.verified()).isTrue();
        assertThat(response.verificationStatus()).isEqualTo("VERIFIED");
    }
}