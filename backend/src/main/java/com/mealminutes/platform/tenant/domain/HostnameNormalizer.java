package com.mealminutes.platform.tenant.domain;

import java.net.IDN;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Canonicalizes hostnames before registry lookup. This is input normalization,
 * not domain ownership verification or user authorization.
 */
public final class HostnameNormalizer {
    private static final Pattern LABEL = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");

    private HostnameNormalizer() { }

    public static String normalize(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Hostname is required");
        }
        String candidate = input.trim();
        if (candidate.isEmpty() || candidate.length() > 260
                || candidate.contains("/") || candidate.contains("\\")
                || candidate.contains("@") || candidate.contains(":")
                || candidate.contains("*") || candidate.contains("?")
                || candidate.contains("#")) {
            throw new IllegalArgumentException("Invalid hostname");
        }
        while (candidate.endsWith(".")) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        if (candidate.isEmpty()) {
            throw new IllegalArgumentException("Invalid hostname");
        }

        final String ascii;
        try {
            ascii = IDN.toASCII(candidate, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid hostname", ex);
        }
        if (ascii.length() > 253) {
            throw new IllegalArgumentException("Hostname is too long");
        }
        String[] labels = ascii.split("\\.", -1);
        for (String label : labels) {
            if (label.length() > 63 || !LABEL.matcher(label).matches()) {
                throw new IllegalArgumentException("Invalid hostname");
            }
        }
        return ascii;
    }
}