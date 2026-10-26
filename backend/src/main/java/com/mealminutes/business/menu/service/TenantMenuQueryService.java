package com.mealminutes.business.menu.service;

import com.mealminutes.business.menu.api.MenuCategoryResponse;
import com.mealminutes.business.menu.api.MenuItemResponse;
import com.mealminutes.business.menu.api.RestaurantProfileResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import com.mealminutes.platform.tenant.service.TenantProvisionerUnavailableException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Read-only storefront queries. The injected JdbcTemplate must be backed by
 * TenantAwareDataSource; the tenant context is resolved from the verified host
 * before this service is invoked.
 */
@Service
public class TenantMenuQueryService {
    private static final int MAX_PAGE_SIZE = 100;
    private final Supplier<JdbcTemplate> jdbcProvider;

    @Autowired
    public TenantMenuQueryService(@Qualifier("tenantBusinessJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcProvider) {
        this.jdbcProvider = jdbcProvider::getIfAvailable;
    }

    public TenantMenuQueryService(JdbcTemplate jdbc) {
        this.jdbcProvider = () -> Objects.requireNonNull(jdbc, "jdbc");
    }

    private JdbcTemplate jdbc() {
        JdbcTemplate template = jdbcProvider.get();
        if (template == null) throw new TenantProvisionerUnavailableException();
        return template;
    }

    public java.util.Optional<RestaurantProfileResponse> getRestaurantProfile() {
        List<RestaurantProfileResponse> profiles = jdbc().query("""
                SELECT restaurant_id, restaurant_name, description, logo_url,
                       support_email, support_phone, currency, timezone
                FROM restaurant_profile
                WHERE is_active = TRUE
                LIMIT 1
                """, (rs, row) -> new RestaurantProfileResponse(
                rs.getString("restaurant_id"), rs.getString("restaurant_name"),
                rs.getString("description"), rs.getString("logo_url"),
                rs.getString("support_email"), rs.getString("support_phone"),
                rs.getString("currency"), rs.getString("timezone")));
        return profiles.stream().findFirst();
    }

    
    public List<MenuCategoryResponse> listCategories() {
        return jdbc().query("""
                SELECT category_id, name, slug, description, sort_order
                FROM menu_category
                WHERE is_active = TRUE
                ORDER BY sort_order ASC, name ASC
                """, (rs, row) -> new MenuCategoryResponse(
                rs.getString("category_id"), rs.getString("name"), rs.getString("slug"),
                rs.getString("description"), rs.getInt("sort_order")));
    }

    
    public List<MenuItemResponse> listItems(String categorySlug, String search, int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page must be zero or greater");
        if (size < 1 || size > MAX_PAGE_SIZE) throw new IllegalArgumentException("size must be between 1 and 100");
        String normalizedSearch = search == null ? null : search.strip();
        if (normalizedSearch != null && normalizedSearch.length() > 100) {
            throw new IllegalArgumentException("search must be at most 100 characters");
        }
        String normalizedCategory = categorySlug == null ? null : categorySlug.strip().toLowerCase(java.util.Locale.ROOT);
        if (normalizedCategory != null && !normalizedCategory.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new IllegalArgumentException("categorySlug has an invalid format");
        }
        int offset;
        try {
            offset = Math.multiplyExact(page, size);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("page is too large", ex);
        }
        String sql = """
                SELECT i.item_id, i.category_id, i.name, i.slug, i.description, i.price,
                       i.currency, i.image_url, i.vegetarian, i.vegan, i.allergens,
                       i.is_available, i.display_order
                FROM menu_item i
                INNER JOIN menu_category c ON c.category_id = i.category_id
                WHERE i.is_available = TRUE AND c.is_active = TRUE
                  AND (? IS NULL OR c.slug = ?)
                  AND (? IS NULL OR i.name LIKE CONCAT('%', ?, '%') OR i.description LIKE CONCAT('%', ?, '%'))
                ORDER BY i.display_order ASC, i.name ASC
                LIMIT ? OFFSET ?
                """;
        RowMapper<MenuItemResponse> mapper = (rs, row) -> new MenuItemResponse(
                rs.getString("item_id"), rs.getString("category_id"), rs.getString("name"),
                rs.getString("slug"), rs.getString("description"), rs.getBigDecimal("price"),
                rs.getString("currency"), rs.getString("image_url"), rs.getBoolean("vegetarian"),
                rs.getBoolean("vegan"), rs.getString("allergens"), rs.getBoolean("is_available"),
                rs.getInt("display_order"));
        return jdbc().query(sql, mapper, normalizedCategory, normalizedCategory,
                normalizedSearch, normalizedSearch, normalizedSearch, size, offset);
    }
}
