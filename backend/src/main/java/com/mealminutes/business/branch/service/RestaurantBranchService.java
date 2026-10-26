package com.mealminutes.business.branch.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Time;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "mealminutes.tenant-provisioning", name = "enabled", havingValue = "true")
public class RestaurantBranchService {
    private final JdbcTemplate jdbc;

    @Autowired
    public RestaurantBranchService(@Qualifier("tenantBusinessJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<BranchResponse> listBranches(boolean includeInactive) {
        return jdbc.query("""
                SELECT branch_id, name, slug, address_line1, address_line2, city, region, postal_code,
                       country_code, phone, latitude, longitude, pickup_enabled, delivery_enabled, is_active
                FROM restaurant_branch
                WHERE (? = TRUE OR is_active = TRUE)
                ORDER BY city ASC, name ASC, branch_id ASC
                """, branchMapper(), includeInactive);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public BranchResponse createBranch(BranchCommand command) {
        Objects.requireNonNull(command, "command");
        String branchId = UUID.randomUUID().toString();
        try {
            jdbc.update("""
                    INSERT INTO restaurant_branch(branch_id, name, slug, address_line1, address_line2, city,
                        region, postal_code, country_code, phone, latitude, longitude, pickup_enabled,
                        delivery_enabled, is_active)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE)
                    """, branchId, required(command.name()), slug(command.slug()), required(command.addressLine1()),
                    optional(command.addressLine2()), required(command.city()), optional(command.region()),
                    optional(command.postalCode()), countryCode(command.countryCode()), optional(command.phone()),
                    command.latitude(), command.longitude(), command.pickupEnabled(), command.deliveryEnabled());
        } catch (DuplicateKeyException duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Branch slug is already in use", duplicate);
        }
        return findBranch(branchId)
                .orElseThrow(() -> new IllegalStateException("Created branch could not be reloaded"));
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public BranchResponse updateBranch(String branchId, BranchCommand command) {
        requireId(branchId, "branchId");
        Objects.requireNonNull(command, "command");
        ensureBranchExistsForUpdate(branchId);
        try {
            int updated = jdbc.update("""
                    UPDATE restaurant_branch SET name = ?, slug = ?, address_line1 = ?, address_line2 = ?,
                        city = ?, region = ?, postal_code = ?, country_code = ?, phone = ?, latitude = ?,
                        longitude = ?, pickup_enabled = ?, delivery_enabled = ?, updated_at = CURRENT_TIMESTAMP(6)
                    WHERE branch_id = ?
                    """, required(command.name()), slug(command.slug()), required(command.addressLine1()),
                    optional(command.addressLine2()), required(command.city()), optional(command.region()),
                    optional(command.postalCode()), countryCode(command.countryCode()), optional(command.phone()),
                    command.latitude(), command.longitude(), command.pickupEnabled(), command.deliveryEnabled(), branchId);
            if (updated != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found");
        } catch (DuplicateKeyException duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Branch slug is already in use", duplicate);
        }
        return findBranch(branchId)
                .orElseThrow(() -> new IllegalStateException("Updated branch could not be reloaded"));
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public BranchResponse setBranchActive(String branchId, boolean active) {
        requireId(branchId, "branchId");
        int updated = jdbc.update("""
                UPDATE restaurant_branch SET is_active = ?, updated_at = CURRENT_TIMESTAMP(6)
                WHERE branch_id = ?
                """, active, branchId);
        if (updated != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found");
        return findBranch(branchId)
                .orElseThrow(() -> new IllegalStateException("Updated branch could not be reloaded"));
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<OperatingHoursResponse> listOperatingHours(String branchId, boolean publicOnly) {
        requireId(branchId, "branchId");
        if (publicOnly) ensurePublicBranchExists(branchId);
        else ensureBranchExists(branchId);
        return jdbc.query("""
                SELECT day_of_week, opens_at, closes_at, is_closed
                FROM restaurant_branch_operating_hours
                WHERE branch_id = ?
                ORDER BY day_of_week ASC
                """, (rs, row) -> new OperatingHoursResponse(rs.getInt("day_of_week"),
                timeText(rs.getTime("opens_at")), timeText(rs.getTime("closes_at")),
                rs.getBoolean("is_closed")), branchId);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public List<OperatingHoursResponse> replaceOperatingHours(String branchId, List<OperatingHoursCommand> hours) {
        requireId(branchId, "branchId");
        if (hours == null || hours.size() != 7) {
            throw new IllegalArgumentException("Exactly seven operating-hours entries are required");
        }
        ensureBranchExistsForUpdate(branchId);
        Set<Integer> days = new HashSet<>();
        List<NormalizedHours> normalized = hours.stream().map(this::normalizeHours).toList();
        for (NormalizedHours entry : normalized) {
            if (!days.add(entry.dayOfWeek())) throw new IllegalArgumentException("Each day of week must appear once");
        }
        if (days.size() != 7 || !days.containsAll(Set.of(1, 2, 3, 4, 5, 6, 7))) {
            throw new IllegalArgumentException("Operating hours must cover days 1 through 7");
        }

        jdbc.update("DELETE FROM restaurant_branch_operating_hours WHERE branch_id = ?", branchId);
        for (NormalizedHours entry : normalized) {
            jdbc.update("""
                    INSERT INTO restaurant_branch_operating_hours(branch_id, day_of_week, opens_at, closes_at, is_closed)
                    VALUES (?, ?, ?, ?, ?)
                    """, branchId, entry.dayOfWeek(), entry.opensAt(), entry.closesAt(), entry.closed());
        }
        return listOperatingHours(branchId, false);
    }

    private NormalizedHours normalizeHours(OperatingHoursCommand command) {
        Objects.requireNonNull(command, "operating-hours entry");
        if (command.dayOfWeek() < 1 || command.dayOfWeek() > 7) {
            throw new IllegalArgumentException("dayOfWeek must be between 1 and 7");
        }
        if (command.closed()) {
            if (notBlank(command.opensAt()) || notBlank(command.closesAt())) {
                throw new IllegalArgumentException("Closed days must not specify opening or closing times");
            }
            return new NormalizedHours(command.dayOfWeek(), null, null, true);
        }
        LocalTime opens = parseTime(command.opensAt(), "opensAt");
        LocalTime closes = parseTime(command.closesAt(), "closesAt");
        if (opens.equals(closes)) throw new IllegalArgumentException("Opening and closing times must differ");
        return new NormalizedHours(command.dayOfWeek(), Time.valueOf(opens), Time.valueOf(closes), false);
    }

    private java.util.Optional<BranchResponse> findBranch(String branchId) {
        return jdbc.query("""
                SELECT branch_id, name, slug, address_line1, address_line2, city, region, postal_code,
                       country_code, phone, latitude, longitude, pickup_enabled, delivery_enabled, is_active
                FROM restaurant_branch WHERE branch_id = ?
                """, branchMapper(), branchId).stream().findFirst();
    }

    private void ensureBranchExists(String branchId) {
        if (jdbc.query("SELECT branch_id FROM restaurant_branch WHERE branch_id = ?",
                (rs, row) -> rs.getString("branch_id"), branchId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found");
        }
    }

    private void ensurePublicBranchExists(String branchId) {
        if (jdbc.query("SELECT branch_id FROM restaurant_branch WHERE branch_id = ? AND is_active = TRUE",
                (rs, row) -> rs.getString("branch_id"), branchId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Active branch not found");
        }
    }

    private void ensureBranchExistsForUpdate(String branchId) {
        if (jdbc.query("SELECT branch_id FROM restaurant_branch WHERE branch_id = ? FOR UPDATE",
                (rs, row) -> rs.getString("branch_id"), branchId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found");
        }
    }

    private static RowMapper<BranchResponse> branchMapper() {
        return (rs, row) -> new BranchResponse(rs.getString("branch_id"), rs.getString("name"),
                rs.getString("slug"), rs.getString("address_line1"), rs.getString("address_line2"),
                rs.getString("city"), rs.getString("region"), rs.getString("postal_code"),
                rs.getString("country_code"), rs.getString("phone"), rs.getBigDecimal("latitude"),
                rs.getBigDecimal("longitude"), rs.getBoolean("pickup_enabled"),
                rs.getBoolean("delivery_enabled"), rs.getBoolean("is_active"));
    }

    private static String slug(String value) {
        String normalized = required(value).toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9]+(?:-[a-z0-9]+)*") || normalized.length() > 80) {
            throw new IllegalArgumentException("slug must be lowercase letters, digits, and single hyphens");
        }
        return normalized;
    }

    private static String countryCode(String value) {
        String normalized = required(value).toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{2}")) throw new IllegalArgumentException("countryCode must contain two letters");
        return normalized;
    }

    private static LocalTime parseTime(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required for an open day");
        try {
            return LocalTime.parse(value.strip());
        } catch (DateTimeParseException invalid) {
            throw new IllegalArgumentException(field + " must be a valid local time such as 09:00", invalid);
        }
    }

    private static String timeText(Time value) {
        return value == null ? null : value.toLocalTime().toString();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Required branch field is missing");
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

    public record BranchCommand(String name, String slug, String addressLine1, String addressLine2,
                               String city, String region, String postalCode, String countryCode,
                               String phone, BigDecimal latitude, BigDecimal longitude,
                               boolean pickupEnabled, boolean deliveryEnabled) { }

    public record BranchResponse(String branchId, String name, String slug, String addressLine1,
                                 String addressLine2, String city, String region, String postalCode,
                                 String countryCode, String phone, BigDecimal latitude, BigDecimal longitude,
                                 boolean pickupEnabled, boolean deliveryEnabled, boolean active) { }

    public record OperatingHoursCommand(int dayOfWeek, String opensAt, String closesAt, boolean closed) { }
    public record OperatingHoursResponse(int dayOfWeek, String opensAt, String closesAt, boolean closed) { }
    private record NormalizedHours(int dayOfWeek, Time opensAt, Time closesAt, boolean closed) { }
}
