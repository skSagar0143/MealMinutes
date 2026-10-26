package com.mealminutes.business.address.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"unchecked", "rawtypes"})
class CustomerAddressServiceTests {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CustomerAddressService service = new CustomerAddressService(jdbc);

    @Test
    void firstAddressBecomesDefault() {
        when(jdbc.query(contains("SELECT address_id FROM customer_address"), any(RowMapper.class),
                eq("customer-1"))).thenReturn(List.of());
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.query(contains("FROM customer_address WHERE customer_user_id = ? AND address_id = ?"),
                any(RowMapper.class), eq("customer-1"), anyString()))
                .thenReturn(List.of(response("address-1", true)));

        var result = service.createAddress("customer-1", command(true));

        assertThat(result.isDefault()).isTrue();
        verify(jdbc).update(contains("INSERT INTO customer_address"), any(Object[].class));
    }

    @Test
    void updatingNonOwnedAddressReturnsNotFound() {
        when(jdbc.query(contains("WHERE customer_user_id = ? AND address_id = ? FOR UPDATE"),
                any(RowMapper.class), eq("customer-1"), eq("address-foreign"))).thenReturn(List.of());

        assertThatThrownBy(() -> service.updateAddress("customer-1", "address-foreign", command(false)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void settingDefaultClearsThePreviousDefault() {
        when(jdbc.query(contains("WHERE customer_user_id = ? AND address_id = ? FOR UPDATE"),
                any(RowMapper.class), eq("customer-1"), eq("address-2")))
                .thenReturn(List.of(new CustomerAddressService.AddressRecord("address-2", false)));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.query(contains("FROM customer_address WHERE customer_user_id = ? AND address_id = ?"),
                any(RowMapper.class), eq("customer-1"), eq("address-2")))
                .thenReturn(List.of(response("address-2", true)));

        var result = service.updateAddress("customer-1", "address-2", command(true));

        assertThat(result.isDefault()).isTrue();
        verify(jdbc).update(contains("address_id <> ? AND is_default = TRUE"), any(Object[].class));
        verify(jdbc).update(contains("SET label = ?"), any(Object[].class));
    }

    @Test
    void deletingDefaultPromotesAnExistingAddress() {
        when(jdbc.query(contains("WHERE customer_user_id = ? AND address_id = ? FOR UPDATE"),
                any(RowMapper.class), eq("customer-1"), eq("address-1")))
                .thenReturn(List.of(new CustomerAddressService.AddressRecord("address-1", true)));
        when(jdbc.query(contains("SELECT address_id FROM customer_address"),
                any(RowMapper.class), eq("customer-1"))).thenReturn(List.of("address-2"));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        service.deleteAddress("customer-1", "address-1");

        verify(jdbc).update(contains("DELETE FROM customer_address"), any(Object[].class));
        verify(jdbc).update(contains("SET is_default = TRUE"), any(Object[].class));
    }

    private static CustomerAddressService.AddressCommand command(boolean isDefault) {
        return new CustomerAddressService.AddressCommand("Home", "Customer One", "+919876543210",
                "12 Example Road", null, "Jaipur", "Rajasthan", "302001", "IN", null, isDefault);
    }

    private static CustomerAddressService.AddressResponse response(String id, boolean isDefault) {
        return new CustomerAddressService.AddressResponse(id, "Home", "Customer One", "+919876543210",
                "12 Example Road", null, "Jaipur", "Rajasthan", "302001", "IN", null, isDefault, Instant.now());
    }
}
