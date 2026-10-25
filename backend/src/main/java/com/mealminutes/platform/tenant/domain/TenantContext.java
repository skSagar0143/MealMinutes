package com.mealminutes.platform.tenant.domain;

import java.util.Objects;

/**
 * Request-local tenant identity. This context is routing metadata, not proof
 * that the authenticated principal is authorized for the tenant.
 */
public final class TenantContext {
    private static final ThreadLocal<TenantDescriptor> CURRENT = new ThreadLocal<>();

    private TenantContext() { }

    public static TenantDescriptor requireCurrent() {
        TenantDescriptor descriptor = CURRENT.get();
        if (descriptor == null) {
            throw new IllegalStateException("No tenant is bound to the current request");
        }
        return descriptor;
    }

    public static TenantDescriptor currentOrNull() {
        return CURRENT.get();
    }

    public static Scope bind(TenantDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        if (CURRENT.get() != null) {
            throw new IllegalStateException("A tenant context is already bound to this thread");
        }
        CURRENT.set(descriptor);
        return new Scope();
    }

    public static void clear() {
        CURRENT.remove();
    }

    public record TenantDescriptor(
            String tenantId, String slug, String databaseName, String databaseSecretReference) {
        public TenantDescriptor {
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
            if (slug == null || slug.isBlank()) throw new IllegalArgumentException("slug is required");
        }
    }

    public static final class Scope implements AutoCloseable {
        private boolean closed;
        private Scope() { }
        @Override
        public void close() {
            if (!closed) {
                CURRENT.remove();
                closed = true;
            }
        }
    }
}