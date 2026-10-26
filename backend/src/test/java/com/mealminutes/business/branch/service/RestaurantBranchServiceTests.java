package com.mealminutes.business.branch.service;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"unchecked", "rawtypes"})
class RestaurantBranchServiceTests {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final RestaurantBranchService service = new RestaurantBranchService(jdbc);

    @Test
    void createsBranchAndNormalizesCountryCode() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.query(contains("FROM restaurant_branch WHERE branch_id = ?"), any(RowMapper.class), anyString()))
                .thenReturn(List.of(branch("branch-1")));

        var response = service.createBranch(command());

        assertThat(response.branchId()).isEqualTo("branch-1");
        assertThat(response.countryCode()).isEqualTo("IN");
        verify(jdbc).update(contains("INSERT INTO restaurant_branch"), any(Object[].class));
    }

    @Test
    void mapsDuplicateBranchSlugToConflict() {
        when(jdbc.update(contains("INSERT INTO restaurant_branch"), any(Object[].class)))
                .thenThrow(new DuplicateKeyException("duplicate slug"));

        assertThatThrownBy(() -> service.createBranch(command()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void requiresExactlySevenOperatingHoursEntries() {
        assertThatThrownBy(() -> service.replaceOperatingHours("branch-1", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Exactly seven");
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsDuplicateDaysBeforeReplacingExistingHours() {
        when(jdbc.query(contains("FOR UPDATE"), any(RowMapper.class), eq("branch-1")))
                .thenReturn(List.of("branch-1"));
        var duplicateDays = List.of(
                new RestaurantBranchService.OperatingHoursCommand(1, null, null, true),
                new RestaurantBranchService.OperatingHoursCommand(1, null, null, true),
                new RestaurantBranchService.OperatingHoursCommand(3, null, null, true),
                new RestaurantBranchService.OperatingHoursCommand(4, null, null, true),
                new RestaurantBranchService.OperatingHoursCommand(5, null, null, true),
                new RestaurantBranchService.OperatingHoursCommand(6, null, null, true),
                new RestaurantBranchService.OperatingHoursCommand(7, null, null, true));

        assertThatThrownBy(() -> service.replaceOperatingHours("branch-1", duplicateDays))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Each day");
        verify(jdbc, never()).update(contains("DELETE FROM restaurant_branch_operating_hours"), any(Object[].class));
    }

    @Test
    void replacesFullWeeklySchedule() {
        when(jdbc.query(contains("FOR UPDATE"), any(RowMapper.class), eq("branch-1")))
                .thenReturn(List.of("branch-1"));
        when(jdbc.query(contains("SELECT branch_id FROM restaurant_branch WHERE branch_id = ?"),
                any(RowMapper.class), eq("branch-1"))).thenReturn(List.of("branch-1"));
        when(jdbc.query(contains("FROM restaurant_branch_operating_hours"), any(RowMapper.class), eq("branch-1")))
                .thenReturn(List.of(new RestaurantBranchService.OperatingHoursResponse(1, "09:00", "17:00", false)));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        var hours = List.of(
                new RestaurantBranchService.OperatingHoursCommand(1, "09:00", "17:00", false),
                new RestaurantBranchService.OperatingHoursCommand(2, "09:00", "17:00", false),
                new RestaurantBranchService.OperatingHoursCommand(3, "09:00", "17:00", false),
                new RestaurantBranchService.OperatingHoursCommand(4, "09:00", "17:00", false),
                new RestaurantBranchService.OperatingHoursCommand(5, "09:00", "17:00", false),
                new RestaurantBranchService.OperatingHoursCommand(6, null, null, true),
                new RestaurantBranchService.OperatingHoursCommand(7, null, null, true));

        var result = service.replaceOperatingHours("branch-1", hours);

        assertThat(result).hasSize(1);
        verify(jdbc).update(contains("DELETE FROM restaurant_branch_operating_hours"), any(Object[].class));
        verify(jdbc, times(7)).update(contains("INSERT INTO restaurant_branch_operating_hours"), any(Object[].class));
    }

    @Test
    void listsBranchesUsingTheRequestedInactiveFilter() {
        when(jdbc.query(contains("WHERE (? = TRUE OR is_active = TRUE)"), any(RowMapper.class), eq(false)))
                .thenReturn(List.of(branch("branch-1")));
        assertThat(service.listBranches(false)).containsExactly(branch("branch-1"));
    }

    @Test
    void listsPublicHoursOnlyForAnActiveBranch() {
        when(jdbc.query(contains("AND is_active = TRUE"), any(RowMapper.class), eq("branch-1")))
                .thenReturn(List.of("branch-1"));
        when(jdbc.query(contains("FROM restaurant_branch_operating_hours"), any(RowMapper.class), eq("branch-1")))
                .thenReturn(List.of());
        assertThat(service.listOperatingHours("branch-1", true)).isEmpty();
    }

    @Test
    void rejectsPublicHoursForInactiveBranch() {
        when(jdbc.query(contains("AND is_active = TRUE"), any(RowMapper.class), eq("branch-1")))
                .thenReturn(List.of());
        assertThatThrownBy(() -> service.listOperatingHours("branch-1", true))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectsInvalidOpeningTimeBeforeDeletingExistingSchedule() {
        when(jdbc.query(contains("FOR UPDATE"), any(RowMapper.class), eq("branch-1")))
                .thenReturn(List.of("branch-1"));
        var hours = List.of(
                new RestaurantBranchService.OperatingHoursCommand(1, "25:00", "17:00", false),
                new RestaurantBranchService.OperatingHoursCommand(2, null, null, true),
                new RestaurantBranchService.OperatingHoursCommand(3, null, null, true),
                new RestaurantBranchService.OperatingHoursCommand(4, null, null, true),
                new RestaurantBranchService.OperatingHoursCommand(5, null, null, true),
                new RestaurantBranchService.OperatingHoursCommand(6, null, null, true),
                new RestaurantBranchService.OperatingHoursCommand(7, null, null, true));
        assertThatThrownBy(() -> service.replaceOperatingHours("branch-1", hours))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("opensAt");
        verify(jdbc, never()).update(contains("DELETE FROM restaurant_branch_operating_hours"), any(Object[].class));
    }

    @Test
    void rejectsOpeningTimesOnClosedDaysBeforeDeletingSchedule() {
        when(jdbc.query(contains("FOR UPDATE"), any(RowMapper.class), eq("branch-1")))
                .thenReturn(List.of("branch-1"));
        var hours = List.of(
                new RestaurantBranchService.OperatingHoursCommand(1, "09:00", "17:00", false),
                new RestaurantBranchService.OperatingHoursCommand(2, "09:00", "17:00", true),
                new RestaurantBranchService.OperatingHoursCommand(3, null, null, true),
                new RestaurantBranchService.OperatingHoursCommand(4, null, null, true),
                new RestaurantBranchService.OperatingHoursCommand(5, null, null, true),
                new RestaurantBranchService.OperatingHoursCommand(6, null, null, true),
                new RestaurantBranchService.OperatingHoursCommand(7, null, null, true));
        assertThatThrownBy(() -> service.replaceOperatingHours("branch-1", hours))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Closed days");
        verify(jdbc, never()).update(contains("DELETE FROM restaurant_branch_operating_hours"), any(Object[].class));
    }

    @Test
    void activatesBranchAndReturnsUpdatedState() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.query(contains("FROM restaurant_branch WHERE branch_id = ?"), any(RowMapper.class), eq("branch-1")))
                .thenReturn(List.of(branch("branch-1")));
        assertThat(service.setBranchActive("branch-1", true).active()).isTrue();
        verify(jdbc).update(contains("SET is_active = ?"), any(Object[].class));
    }

    private static RestaurantBranchService.BranchCommand command() {
        return new RestaurantBranchService.BranchCommand("Downtown", "downtown", "12 Main Road",
                null, "Jaipur", "Rajasthan", "302001", "in", "+911234567890",
                new BigDecimal("26.9124"), new BigDecimal("75.7873"), true, true);
    }

    private static RestaurantBranchService.BranchResponse branch(String id) {
        return new RestaurantBranchService.BranchResponse(id, "Downtown", "downtown", "12 Main Road",
                null, "Jaipur", "Rajasthan", "302001", "IN", "+911234567890",
                new BigDecimal("26.9124"), new BigDecimal("75.7873"), true, true, true);
    }
}
