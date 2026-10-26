package com.mealminutes.platform.tenant.service;

import java.text.Normalizer;
import java.util.Locale;

public final class TenantSlugNormalizer {
    private TenantSlugNormalizer() { }

    public static String fromName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Name is required");
        String slug = Normalizer.normalize(name.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (slug.isBlank() || slug.length() > 120)
            throw new IllegalArgumentException("Name cannot be converted to a valid slug");
        return slug;
    }

    public static String requireSlug(String slug) {
        if (slug == null || !slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*") || slug.length() > 120)
            throw new IllegalArgumentException("Slug must contain lowercase letters, numbers and single hyphen separators");
        return slug;
    }
}