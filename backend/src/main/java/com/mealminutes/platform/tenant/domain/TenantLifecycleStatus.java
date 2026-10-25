package com.mealminutes.platform.tenant.domain;

public enum TenantLifecycleStatus {
    REGISTERED,
    PENDING_VERIFICATION,
    APPROVED,
    PROVISIONING,
    ACTIVE,
    FAILED,
    SUSPENDED,
    DEPROVISIONING
}
