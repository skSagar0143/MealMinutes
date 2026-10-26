package com.mealminutes.platform.tenant.web;

import com.mealminutes.platform.tenant.domain.TenantRegistryEntry;
import com.mealminutes.platform.tenant.service.TenantProvisioningService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/platform/tenants")
public class TenantProvisioningController {
    private final TenantProvisioningService provisioning;
    public TenantProvisioningController(TenantProvisioningService provisioning) { this.provisioning = provisioning; }

    @PostMapping
    public ResponseEntity<TenantProvisioningService.TenantRegistrationResult> register(
            @Valid @RequestBody RegisterTenantRequest request) {
        var result = provisioning.register(request.slug(), request.hostname());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping
    public Page<TenantSummary> list(@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return Page.empty(pageable);
    }

    @PostMapping("/{tenantId}/approve")
    public TenantSummary approve(@PathVariable String tenantId) {
        return summary(provisioning.approve(tenantId));
    }

    @PostMapping("/{tenantId}/provision")
    public TenantSummary provision(@PathVariable String tenantId) {
        return summary(provisioning.provision(tenantId));
    }

    @PostMapping("/{tenantId}/suspend")
    public TenantSummary suspend(@PathVariable String tenantId) {
        return summary(provisioning.suspend(tenantId));
    }

    @PostMapping("/{tenantId}/reactivate")
    public TenantSummary reactivate(@PathVariable String tenantId) {
        return summary(provisioning.reactivate(tenantId));
    }

    private TenantSummary summary(TenantRegistryEntry tenant) {
        return new TenantSummary(tenant.getTenantId(), tenant.getSlug(), tenant.getLifecycleStatus().name(),
                tenant.getProvisioningStatus().name(), tenant.getCreatedAt());
    }

    public record RegisterTenantRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?") String slug,
            @NotBlank @Size(max = 260) String hostname) { }

    public record TenantSummary(String tenantId, String slug, String lifecycleStatus,
                                String provisioningStatus, Instant createdAt) { }
}