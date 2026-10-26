package com.mealminutes.platform.tenant.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Shared safe slug normalization for tenant-owned menu categories and items. */
public final class TenantSlugNormalizer {
    private static final Pattern VALID = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,158}[a-z0-9])?");
    private TenantSlugNormalizer() { }

    public static String fromName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Name is required to create a slug");
        String ascii = Normalizer.normalize(name.strip(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return requireSlug(ascii);
    }

    public static String requireSlug(String slug) {
        if (slug == null || slug.isBlank()) throw new IllegalArgumentException("Slug is required");
        String candidate = slug.strip().toLowerCase(Locale.ROOT);
        if (candidate.length() > 160 || !VALID.matcher(candidate).matches()) {
            throw new IllegalArgumentException("Slug must be lowercase alphanumeric words separated by single hyphens");
        }
        return candidate;
    }
}