package com.mealminutes.business.address.service;

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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "mealminutes.tenant-provisioning", name = "enabled", havingValue = "true")
public class CustomerAddressService {
    private final JdbcTemplate jdbc;

    @Autowired
    public CustomerAddressService(@Qualifier("tenantBusinessJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<AddressResponse> listAddresses(String customerUserId) {
        requireId(customerUserId, "customerUserId");
        return jdbc.query("""
                SELECT address_id, label, recipient_name, recipient_phone, address_line1, address_line2,
                       city, region, postal_code, country_code, delivery_instructions, is_default, created_at
                FROM customer_address
                WHERE customer_user_id = ?
                ORDER BY is_default DESC, created_at DESC, address_id DESC
                """, addressMapper(), customerUserId);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public AddressResponse createAddress(String customerUserId, AddressCommand command) {
        requireId(customerUserId, "customerUserId");
        Objects.requireNonNull(command, "command");
        List<String> existing = jdbc.query("""
                SELECT address_id FROM customer_address
                WHERE customer_user_id = ? ORDER BY created_at ASC FOR UPDATE
                """, (rs, row) -> rs.getString("address_id"), customerUserId);
        boolean makeDefault = command.isDefault() || existing.isEmpty();
        if (makeDefault) {
            jdbc.update("UPDATE customer_address SET is_default = FALSE WHERE customer_user_id = ? AND is_default = TRUE",
                    customerUserId);
        }

        String addressId = UUID.randomUUID().toString();
        try {
            jdbc.update("""
                    INSERT INTO customer_address(address_id, customer_user_id, label, recipient_name, recipient_phone,
                        address_line1, address_line2, city, region, postal_code, country_code,
                        delivery_instructions, is_default)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, addressId, customerUserId, required(command.label()), required(command.recipientName()),
                    required(command.recipientPhone()), required(command.addressLine1()), optional(command.addressLine2()),
                    required(command.city()), optional(command.region()), optional(command.postalCode()),
                    countryCode(command.countryCode()), optional(command.deliveryInstructions()), makeDefault);
        } catch (DuplicateKeyException duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A default address was changed concurrently; reload and retry", duplicate);
        }
        return findAddress(customerUserId, addressId)
                .orElseThrow(() -> new IllegalStateException("Created address could not be reloaded"));
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public AddressResponse updateAddress(String customerUserId, String addressId, AddressCommand command) {
        requireId(customerUserId, "customerUserId");
        requireId(addressId, "addressId");
        Objects.requireNonNull(command, "command");
        AddressRecord current = findForUpdate(customerUserId, addressId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));

        if (command.isDefault()) {
            jdbc.update("""
                    UPDATE customer_address SET is_default = FALSE
                    WHERE customer_user_id = ? AND address_id <> ? AND is_default = TRUE
                    """, customerUserId, addressId);
        }
        try {
            int updated = jdbc.update("""
                    UPDATE customer_address SET label = ?, recipient_name = ?, recipient_phone = ?,
                        address_line1 = ?, address_line2 = ?, city = ?, region = ?, postal_code = ?,
                        country_code = ?, delivery_instructions = ?, is_default = ?,
                        updated_at = CURRENT_TIMESTAMP(6)
                    WHERE address_id = ? AND customer_user_id = ?
                    """, required(command.label()), required(command.recipientName()), required(command.recipientPhone()),
                    required(command.addressLine1()), optional(command.addressLine2()), required(command.city()),
                    optional(command.region()), optional(command.postalCode()), countryCode(command.countryCode()),
                    optional(command.deliveryInstructions()), command.isDefault(), addressId, customerUserId);
            if (updated != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found");
        } catch (DuplicateKeyException duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A default address was changed concurrently; reload and retry", duplicate);
        }
        return findAddress(customerUserId, addressId)
                .orElseThrow(() -> new IllegalStateException("Updated address could not be reloaded"));
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public void deleteAddress(String customerUserId, String addressId) {
        requireId(customerUserId, "customerUserId");
        requireId(addressId, "addressId");
        AddressRecord current = findForUpdate(customerUserId, addressId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
        int deleted = jdbc.update("DELETE FROM customer_address WHERE address_id = ? AND customer_user_id = ?",
                addressId, customerUserId);
        if (deleted != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found");

        if (current.isDefault()) {
            List<String> replacement = jdbc.query("""
                    SELECT address_id FROM customer_address
                    WHERE customer_user_id = ? ORDER BY created_at ASC, address_id ASC LIMIT 1 FOR UPDATE
                    """, (rs, row) -> rs.getString("address_id"), customerUserId);
            if (!replacement.isEmpty()) {
                jdbc.update("""
                        UPDATE customer_address SET is_default = TRUE
                        WHERE address_id = ? AND customer_user_id = ?
                        """, replacement.getFirst(), customerUserId);
            }
        }
    }

    private java.util.Optional<AddressRecord> findForUpdate(String customerUserId, String addressId) {
        return jdbc.query("""
                SELECT address_id, is_default FROM customer_address
                WHERE customer_user_id = ? AND address_id = ? FOR UPDATE
                """, (rs, row) -> new AddressRecord(rs.getString("address_id"), rs.getBoolean("is_default")),
                customerUserId, addressId).stream().findFirst();
    }

    private java.util.Optional<AddressResponse> findAddress(String customerUserId, String addressId) {
        return jdbc.query("""
                SELECT address_id, label, recipient_name, recipient_phone, address_line1, address_line2,
                       city, region, postal_code, country_code, delivery_instructions, is_default, created_at
                FROM customer_address WHERE customer_user_id = ? AND address_id = ?
                """, addressMapper(), customerUserId, addressId).stream().findFirst();
    }

    private static RowMapper<AddressResponse> addressMapper() {
        return (rs, row) -> new AddressResponse(rs.getString("address_id"), rs.getString("label"),
                rs.getString("recipient_name"), rs.getString("recipient_phone"), rs.getString("address_line1"),
                rs.getString("address_line2"), rs.getString("city"), rs.getString("region"),
                rs.getString("postal_code"), rs.getString("country_code"), rs.getString("delivery_instructions"),
                rs.getBoolean("is_default"), toInstant(rs.getTimestamp("created_at")));
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Required address field is missing");
        return value.strip();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String countryCode(String value) {
        String normalized = required(value).toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{2}")) throw new IllegalArgumentException("countryCode must contain two letters");
        return normalized;
    }

    private static void requireId(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 36) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private record AddressRecord(String addressId, boolean isDefault) { }

    public record AddressCommand(String label, String recipientName, String recipientPhone, String addressLine1,
                                 String addressLine2, String city, String region, String postalCode,
                                 String countryCode, String deliveryInstructions, boolean isDefault) { }

    public record AddressResponse(String addressId, String label, String recipientName, String recipientPhone,
                                  String addressLine1, String addressLine2, String city, String region,
                                  String postalCode, String countryCode, String deliveryInstructions,
                                  boolean isDefault, Instant createdAt) { }
}
