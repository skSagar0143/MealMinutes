package com.mealminutes.business.profile.service;

import com.mealminutes.platform.tenant.domain.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"unchecked", "rawtypes"})
class RestaurantProfileServiceTests {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final RestaurantProfileService service = new RestaurantProfileService(jdbc, new ObjectMapper());

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void createsFirstProfileAndWritesAuditAndOutbox() {
        when(jdbc.query(contains("WHERE is_active = TRUE ORDER BY created_at ASC LIMIT 1 FOR UPDATE"),
                any(RowMapper.class))).thenReturn(List.of());
        when(jdbc.query(contains("SELECT restaurant_id FROM restaurant_profile ORDER BY created_at ASC"),
                any(RowMapper.class))).thenReturn(List.of());
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        try (TenantContext.Scope ignored = TenantContext.bind(tenant())) {
            var result = service.saveProfile("owner-1", command("Asia/Kolkata"));

            assertThat(result.name()).isEqualTo("Local Kitchen");
            assertThat(result.currency()).isEqualTo("INR");
        }

        verify(jdbc).update(contains("INSERT INTO restaurant_profile"), any(Object[].class));
        verify(jdbc).update(contains("INSERT INTO outbox_event"), any(Object[].class));
        verify(jdbc).update(contains("INSERT INTO audit_event"), any(Object[].class));
    }

    @Test
    void updatesExistingActiveProfile() {
        when(jdbc.query(contains("WHERE is_active = TRUE ORDER BY created_at ASC LIMIT 1 FOR UPDATE"),
                any(RowMapper.class))).thenReturn(List.of("profile-1"));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        try (TenantContext.Scope ignored = TenantContext.bind(tenant())) {
            var result = service.saveProfile("owner-1", command("Asia/Kolkata"));
            assertThat(result.id()).isEqualTo("profile-1");
        }

        verify(jdbc).update(contains("UPDATE restaurant_profile SET restaurant_name"), any(Object[].class));
    }

    @Test
    void rejectsUnknownTimezoneBeforeDatabaseWrites() {
        assertThatThrownBy(() -> service.saveProfile("owner-1", command("Not/A_Real_Zone")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IANA timezone");
        verifyNoInteractions(jdbc);
    }

    @Test
    void mapsConcurrentActiveProfileCreationToConflict() {
        when(jdbc.query(contains("WHERE is_active = TRUE ORDER BY created_at ASC LIMIT 1 FOR UPDATE"),
                any(RowMapper.class))).thenReturn(List.of());
        when(jdbc.query(contains("SELECT restaurant_id FROM restaurant_profile ORDER BY created_at ASC"),
                any(RowMapper.class))).thenReturn(List.of());
        when(jdbc.update(contains("INSERT INTO restaurant_profile"), any(Object[].class)))
                .thenThrow(new DuplicateKeyException("active profile key"));

        try (TenantContext.Scope ignored = TenantContext.bind(tenant())) {
            assertThatThrownBy(() -> service.saveProfile("owner-1", command("Asia/Kolkata")))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @Test
    void returnsActiveProfileWhenPresent() {
        when(jdbc.query(contains("FROM restaurant_profile WHERE is_active = TRUE LIMIT 1"),
                any(RowMapper.class))).thenReturn(List.of(profile()));
        assertThat(service.getProfile()).contains(profile());
    }

    @Test
    void reactivatesAnExistingInactiveProfileWhenNoActiveProfileExists() {
        when(jdbc.query(contains("WHERE is_active = TRUE ORDER BY created_at ASC LIMIT 1 FOR UPDATE"),
                any(RowMapper.class))).thenReturn(List.of());
        when(jdbc.query(contains("SELECT restaurant_id FROM restaurant_profile ORDER BY created_at ASC"),
                any(RowMapper.class))).thenReturn(List.of("profile-1"));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        try (TenantContext.Scope ignored = TenantContext.bind(tenant())) {
            var result = service.saveProfile("owner-1", command("Asia/Kolkata"));
            assertThat(result.id()).isEqualTo("profile-1");
        }
        verify(jdbc).update(contains("UPDATE restaurant_profile SET restaurant_name"), any(Object[].class));
    }

    @Test
    void rejectsNonHttpLogoUrlsBeforeDatabaseWrites() {
        var invalid = new RestaurantProfileService.ProfileCommand("Local Kitchen", null,
                "ftp://example.com/logo.png", null, null, "INR", "Asia/Kolkata");
        assertThatThrownBy(() -> service.saveProfile("owner-1", invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTP(S)");
        verifyNoInteractions(jdbc);
    }

    private static com.mealminutes.business.menu.api.RestaurantProfileResponse profile() {
        return new com.mealminutes.business.menu.api.RestaurantProfileResponse("profile-1", "Local Kitchen",
                "Fresh meals", "https://example.com/logo.png", "support@example.com",
                "+911234567890", "INR", "Asia/Kolkata");
    }

    private static RestaurantProfileService.ProfileCommand command(String timezone) {
        return new RestaurantProfileService.ProfileCommand("Local Kitchen", "Fresh meals",
                "https://example.com/logo.png", "support@example.com", "+911234567890", "inr", timezone);
    }

    private static TenantContext.TenantDescriptor tenant() {
        return new TenantContext.TenantDescriptor("tenant-1", "abc-food", "tenant_abc_food", "secret://db");
    }
}
