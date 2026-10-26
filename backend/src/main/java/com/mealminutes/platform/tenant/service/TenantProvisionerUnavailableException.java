package com.mealminutes.platform.tenant.service;

public class TenantProvisionerUnavailableException extends RuntimeException {
    public TenantProvisionerUnavailableException() {
        super("Tenant database provisioning is not configured");
    }
}