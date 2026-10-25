package com.mealminutes.platform.tenant.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class TenantLifecyclePolicy {
    private static final Map<TenantLifecycleStatus, Set<TenantLifecycleStatus>> ALLOWED =
            new EnumMap<>(TenantLifecycleStatus.class);

    static {
        ALLOWED.put(TenantLifecycleStatus.REGISTERED,
                EnumSet.of(TenantLifecycleStatus.PENDING_VERIFICATION, TenantLifecycleStatus.FAILED));
        ALLOWED.put(TenantLifecycleStatus.PENDING_VERIFICATION,
                EnumSet.of(TenantLifecycleStatus.APPROVED, TenantLifecycleStatus.FAILED));
        ALLOWED.put(TenantLifecycleStatus.APPROVED,
                EnumSet.of(TenantLifecycleStatus.PROVISIONING, TenantLifecycleStatus.SUSPENDED));
        ALLOWED.put(TenantLifecycleStatus.PROVISIONING,
                EnumSet.of(TenantLifecycleStatus.ACTIVE, TenantLifecycleStatus.FAILED));
        ALLOWED.put(TenantLifecycleStatus.ACTIVE,
                EnumSet.of(TenantLifecycleStatus.SUSPENDED, TenantLifecycleStatus.DEPROVISIONING));
        ALLOWED.put(TenantLifecycleStatus.FAILED,
                EnumSet.of(TenantLifecycleStatus.PROVISIONING, TenantLifecycleStatus.DEPROVISIONING));
        ALLOWED.put(TenantLifecycleStatus.SUSPENDED,
                EnumSet.of(TenantLifecycleStatus.ACTIVE, TenantLifecycleStatus.DEPROVISIONING));
        ALLOWED.put(TenantLifecycleStatus.DEPROVISIONING, EnumSet.noneOf(TenantLifecycleStatus.class));
    }

    private TenantLifecyclePolicy() { }

    public static boolean isAllowed(TenantLifecycleStatus from, TenantLifecycleStatus to) {
        return from != null && to != null && ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static void requireAllowed(TenantLifecycleStatus from, TenantLifecycleStatus to) {
        if (!isAllowed(from, to)) {
            throw new IllegalStateException("Tenant lifecycle transition is not allowed: " + from + " -> " + to);
        }
    }
}