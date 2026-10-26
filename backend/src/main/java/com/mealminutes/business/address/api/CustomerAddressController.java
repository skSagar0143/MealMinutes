package com.mealminutes.business.address.api;

import com.mealminutes.business.address.service.CustomerAddressService;
import com.mealminutes.platform.identity.domain.TenantMembershipRole;
import com.mealminutes.platform.identity.service.TenantMembershipAuthorizationService;
import com.mealminutes.platform.tenant.domain.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/customer/addresses")
@PreAuthorize("hasRole('CUSTOMER')")
@ConditionalOnProperty(prefix = "mealminutes.tenant-provisioning", name = "enabled", havingValue = "true")
public class CustomerAddressController {
    private final CustomerAddressService addresses;
    private final TenantMembershipAuthorizationService memberships;

    public CustomerAddressController(CustomerAddressService addresses,
                                     TenantMembershipAuthorizationService memberships) {
        this.addresses = addresses;
        this.memberships = memberships;
    }

    @GetMapping
    public List<CustomerAddressService.AddressResponse> list(@AuthenticationPrincipal Jwt jwt) {
        authorize(jwt);
        return addresses.listAddresses(jwt.getSubject());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerAddressService.AddressResponse create(@Valid @RequestBody AddressRequest request,
                                                          @AuthenticationPrincipal Jwt jwt) {
        authorize(jwt);
        return addresses.createAddress(jwt.getSubject(), request.toCommand());
    }

    @PutMapping("/{addressId}")
    public CustomerAddressService.AddressResponse update(@PathVariable String addressId,
                                                         @Valid @RequestBody AddressRequest request,
                                                         @AuthenticationPrincipal Jwt jwt) {
        authorize(jwt);
        return addresses.updateAddress(jwt.getSubject(), addressId, request.toCommand());
    }

    @DeleteMapping("/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String addressId, @AuthenticationPrincipal Jwt jwt) {
        authorize(jwt);
        addresses.deleteAddress(jwt.getSubject(), addressId);
    }

    private void authorize(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new org.springframework.security.access.AccessDeniedException("Authentication is required");
        }
        memberships.requireAnyRole(TenantContext.requireCurrent().tenantId(), jwt.getSubject(),
                Set.of(TenantMembershipRole.CUSTOMER));
    }

    public record AddressRequest(
            @NotBlank @Size(max = 80) String label,
            @NotBlank @Size(max = 160) String recipientName,
            @NotBlank @Pattern(regexp = "[+0-9() .-]{7,32}") String recipientPhone,
            @NotBlank @Size(max = 255) String addressLine1,
            @Size(max = 255) String addressLine2,
            @NotBlank @Size(max = 120) String city,
            @Size(max = 120) String region,
            @Size(max = 20) String postalCode,
            @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String countryCode,
            @Size(max = 500) String deliveryInstructions,
            boolean isDefault) {
        CustomerAddressService.AddressCommand toCommand() {
            return new CustomerAddressService.AddressCommand(label, recipientName, recipientPhone, addressLine1,
                    addressLine2, city, region, postalCode, countryCode, deliveryInstructions, isDefault);
        }
    }
}
