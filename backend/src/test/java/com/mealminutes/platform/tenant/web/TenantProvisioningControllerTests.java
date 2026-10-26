package com.mealminutes.platform.tenant.web;

import com.mealminutes.platform.tenant.domain.TenantRegistryEntry;
import com.mealminutes.platform.tenant.service.TenantProvisioningService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TenantProvisioningControllerTests {
    @Test void registersTenantAndReturnsDnsInstructions() {
        TenantProvisioningService service = mock(TenantProvisioningService.class);
        var result = new TenantProvisioningService.TenantRegistrationResult(
                "tenant-1", "abc-food", "shop.example.com", "PENDING_VERIFICATION",
                "_mealminutes-verification.shop.example.com", "mealminutes-verification=token");
        when(service.register("abc-food", "shop.example.com")).thenReturn(result);
        var response = new TenantProvisioningController(service).register(
                new TenantProvisioningController.RegisterTenantRequest("abc-food", "shop.example.com"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().dnsTxtRecordValue()).contains("token");
    }

    @Test void listsTenantSummariesWithoutExposingDatabaseCredentials() {
        TenantProvisioningService service = mock(TenantProvisioningService.class);
        TenantRegistryEntry tenant = new TenantRegistryEntry("abc-food");
        when(service.list(any())).thenReturn(new PageImpl<>(List.of(tenant), PageRequest.of(0, 20), 1));
        var page = new TenantProvisioningController(service).list(PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().getFirst().slug()).isEqualTo("abc-food");
    }
}