package com.mealminutes.business.profile.service;

import com.mealminutes.business.menu.api.RestaurantProfileResponse;
import com.mealminutes.platform.tenant.domain.TenantContext;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "mealminutes.tenant-provisioning", name = "enabled", havingValue = "true")
public class RestaurantProfileService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Autowired
    public RestaurantProfileService(@Qualifier("tenantBusinessJdbcTemplate") JdbcTemplate jdbc,
                                    ObjectMapper objectMapper) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public java.util.Optional<RestaurantProfileResponse> getProfile() {
        return jdbc.query("""
                SELECT restaurant_id, restaurant_name, description, logo_url, support_email, support_phone, currency, timezone
                FROM restaurant_profile WHERE is_active = TRUE LIMIT 1
                """, (rs, row) -> new RestaurantProfileResponse(rs.getString("restaurant_id"),
                rs.getString("restaurant_name"), rs.getString("description"), rs.getString("logo_url"),
                rs.getString("support_email"), rs.getString("support_phone"), rs.getString("currency"),
                rs.getString("timezone"))).stream().findFirst();
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public RestaurantProfileResponse saveProfile(String actorUserId, ProfileCommand command) {
        requireId(actorUserId, "actorUserId");
        Objects.requireNonNull(command, "command");
        String name = required(command.name());
        String description = optional(command.description());
        String logoUrl = normalizeLogoUrl(command.logoUrl());
        String supportEmail = optional(command.supportEmail());
        String supportPhone = optional(command.supportPhone());
        String currency = normalizeCurrency(command.currency());
        String timezone = normalizeTimezone(command.timezone());

        List<String> active = jdbc.query("""
                SELECT restaurant_id FROM restaurant_profile
                WHERE is_active = TRUE ORDER BY created_at ASC LIMIT 1 FOR UPDATE
                """, (rs, row) -> rs.getString("restaurant_id"));
        String profileId;
        boolean created = false;
        if (!active.isEmpty()) {
            profileId = active.getFirst();
        } else {
            List<String> existing = jdbc.query("""
                    SELECT restaurant_id FROM restaurant_profile
                    ORDER BY created_at ASC, restaurant_id ASC LIMIT 1 FOR UPDATE
                    """, (rs, row) -> rs.getString("restaurant_id"));
            if (existing.isEmpty()) {
                profileId = UUID.randomUUID().toString();
                created = true;
            } else {
                profileId = existing.getFirst();
            }
        }

        try {
            if (created) {
                jdbc.update("""
                        INSERT INTO restaurant_profile(restaurant_id, restaurant_name, description, logo_url,
                            support_email, support_phone, currency, timezone, is_active)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE)
                        """, profileId, name, description, logoUrl, supportEmail, supportPhone, currency, timezone);
            } else {
                jdbc.update("""
                        UPDATE restaurant_profile SET restaurant_name = ?, description = ?, logo_url = ?,
                            support_email = ?, support_phone = ?, currency = ?, timezone = ?, is_active = TRUE,
                            updated_at = CURRENT_TIMESTAMP(6)
                        WHERE restaurant_id = ?
                        """, name, description, logoUrl, supportEmail, supportPhone, currency, timezone, profileId);
            }
        } catch (DuplicateKeyException duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Another profile update won the race; reload and retry", duplicate);
        }

        recordProfileChange(actorUserId, profileId, created, name, currency, timezone);
        return new RestaurantProfileResponse(profileId, name, description, logoUrl, supportEmail,
                supportPhone, currency, timezone);
    }

    private void recordProfileChange(String actorUserId, String profileId, boolean created,
                                     String name, String currency, String timezone) {
        String tenantId = TenantContext.requireCurrent().tenantId();
        String correlationId = MDC.get("correlationId");
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("restaurantId", profileId);
        details.put("name", name);
        details.put("currency", currency);
        details.put("timezone", timezone);
        details.put("action", created ? "CREATED" : "UPDATED");
        String json = writeJson(details);
        jdbc.update("""
                INSERT INTO outbox_event(event_id, tenant_id, aggregate_type, aggregate_id, event_type,
                    schema_version, correlation_id, payload)
                VALUES (?, ?, 'RESTAURANT_PROFILE', ?, 'restaurant.profile_updated', 1, ?, ?)
                """, UUID.randomUUID().toString(), tenantId, profileId, correlationId, json);
        jdbc.update("""
                INSERT INTO audit_event(audit_id, actor_user_id, action, resource_type, resource_id,
                    correlation_id, details)
                VALUES (?, ?, 'RESTAURANT_PROFILE_UPDATED', 'RESTAURANT_PROFILE', ?, ?, ?)
                """, UUID.randomUUID().toString(), actorUserId, profileId, correlationId, json);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize restaurant profile event", exception);
        }
    }

    private static String normalizeLogoUrl(String value) {
        String normalized = optional(value);
        if (normalized == null) return null;
        if (normalized.length() > 2048) throw new IllegalArgumentException("logoUrl is too long");
        try {
            URI uri = URI.create(normalized);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null) {
                throw new IllegalArgumentException("logoUrl must be an absolute HTTP(S) URL");
            }
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("logoUrl must be an absolute HTTP(S) URL", invalid);
        }
        return normalized;
    }

    private static String normalizeCurrency(String value) {
        String normalized = required(value).toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) throw new IllegalArgumentException("currency must be a three-letter code");
        return normalized;
    }

    private static String normalizeTimezone(String value) {
        String normalized = required(value);
        try {
            ZoneId.of(normalized);
        } catch (Exception invalid) {
            throw new IllegalArgumentException("timezone must be a valid IANA timezone", invalid);
        }
        return normalized;
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Required profile field is missing");
        return value.strip();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static void requireId(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 36) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    public record ProfileCommand(String name, String description, String logoUrl, String supportEmail,
                                 String supportPhone, String currency, String timezone) { }
}
