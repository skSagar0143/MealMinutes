package com.mealminutes.business.menu;

import com.mealminutes.business.menu.api.MenuCategoryResponse;
import com.mealminutes.business.menu.api.MenuItemResponse;
import com.mealminutes.business.menu.api.RestaurantProfileResponse;
import com.mealminutes.business.menu.service.TenantMenuQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"unchecked", "rawtypes"})
class TenantMenuQueryServiceTests {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final TenantMenuQueryService service = new TenantMenuQueryService(jdbc);

    @Test
    void returnsActiveRestaurantProfileWhenPresent() {
        var expected = new RestaurantProfileResponse("r1", "Test Kitchen", null, null,
                "hello@example.com", null, "INR", "Asia/Kolkata");
        when(jdbc.query(contains("FROM restaurant_profile"), any(RowMapper.class))).thenReturn(List.of(expected));
        assertThat(service.getRestaurantProfile()).contains(expected);
    }

    @Test
    void returnsEmptyWhenNoActiveRestaurantProfileExists() {
        when(jdbc.query(contains("FROM restaurant_profile"), any(RowMapper.class))).thenReturn(List.of());
        assertThat(service.getRestaurantProfile()).isEmpty();
    }

    @Test
    void listsActiveCategoriesInRepositoryOrder() {
        var expected = List.of(new MenuCategoryResponse("c1", "Mains", "mains", null, 1));
        when(jdbc.query(contains("FROM menu_category"), any(RowMapper.class))).thenReturn(expected);

        assertThat(service.listCategories()).isEqualTo(expected);
        verify(jdbc).query(contains("WHERE is_active = TRUE"), any(RowMapper.class));
    }

    @Test
    void listsMenuItemsWithBoundFiltersAndPagination() {
        var expected = List.of(new MenuItemResponse("i1", "c1", "Paneer Wrap", "paneer-wrap",
                null, new BigDecimal("149.00"), "INR", null, true, false, null, true, 1));
        when(jdbc.query(contains("FROM menu_item i"), any(RowMapper.class), any(), any(),
                any(), any(), any(), anyInt(), anyInt())).thenReturn(expected);

        assertThat(service.listItems("Mains", "paneer", 2, 10)).isEqualTo(expected);
        verify(jdbc).query(contains("LIMIT ? OFFSET ?"), any(RowMapper.class),
                eq("mains"), eq("mains"), eq("paneer"), eq("paneer"), eq("paneer"), eq(10), eq(20));
    }

    @Test
    void validatesPaginationSearchAndCategory() {
        assertThatThrownBy(() -> service.listItems(null, null, -1, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.listItems(null, null, 0, 101)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.listItems(null, "x".repeat(101), 0, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.listItems("../admin", null, 0, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.listItems(null, null, Integer.MAX_VALUE, 100)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void permitsNullOptionalFiltersAndTrimsInput() {
        when(jdbc.query(contains("FROM menu_item i"), any(RowMapper.class), any(), any(),
                any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        assertThat(service.listItems(null, "  ", 0, 20)).isEmpty();
        verify(jdbc).query(contains("FROM menu_item i"), any(RowMapper.class),
                isNull(), isNull(), eq(""), eq(""), eq(""), eq(20), eq(0));
    }
}
