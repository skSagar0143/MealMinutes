package com.mealminutes.platform.tenant.web;

import com.mealminutes.platform.tenant.service.TenantDomainVerificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenant-domains")
public class TenantDomainVerificationController {
    private final TenantDomainVerificationService verification;
    public TenantDomainVerificationController(TenantDomainVerificationService verification) {
        this.verification = verification;
    }

    @PostMapping("/verify")
    public VerificationResponse verify(@Valid @RequestBody VerifyDomainRequest request) {
        var domain = verification.verify(request.hostname(), request.challenge());
        return new VerificationResponse(domain.getHostname(), domain.getVerificationStatus().name(), true);
    }

    public record VerifyDomainRequest(@NotBlank @Size(max = 260) String hostname,
                                      @NotBlank @Size(max = 128) String challenge) { }
    public record VerificationResponse(String hostname, String verificationStatus, boolean verified) { }
}