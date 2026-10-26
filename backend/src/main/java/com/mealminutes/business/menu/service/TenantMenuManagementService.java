package com.mealminutes.business.menu.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.mealminutes.business.menu.api.MenuCategoryResponse;
import com.mealminutes.business.menu.api.MenuItemResponse;
import com.mealminutes.platform.tenant.domain.TenantContext;
import com.mealminutes.platform.tenant.service.TenantProvisionerUnavailableException;
import com.mealminutes.platform.tenant.service.TenantSlugNormalizer;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class TenantMenuManagementService {
    private final Supplier<JdbcTemplate> jdbcProvider;
    private final Supplier<PlatformTransactionManager> transactionManagerProvider;
    private final ObjectMapper objectMapper;

    @Autowired
    public TenantMenuManagementService(
            @Qualifier("tenantBusinessJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcProvider,
            @Qualifier("tenantTransactionManager") ObjectProvider<PlatformTransactionManager> transactionManagers,
            ObjectMapper objectMapper) {
        this(jdbcProvider::getIfAvailable, transactionManagers::getIfAvailable, objectMapper);
    }

    TenantMenuManagementService(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, ObjectMapper objectMapper) {
        this(() -> jdbc, () -> transactionManager, objectMapper);
    }

    TenantMenuManagementService(Supplier<JdbcTemplate> jdbcProvider,
                               Supplier<PlatformTransactionManager> transactionManagerProvider,
                               ObjectMapper objectMapper) {
        this.jdbcProvider = Objects.requireNonNull(jdbcProvider);
        this.transactionManagerProvider = Objects.requireNonNull(transactionManagerProvider);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public List<MenuCategoryResponse> listCategories(boolean includeInactive) {
        String sql = "SELECT category_id, name, slug, description, sort_order FROM menu_category "
                + (includeInactive ? "" : "WHERE is_active = TRUE ")
                + "ORDER BY sort_order ASC, name ASC";
        return jdbc().query(sql, categoryMapper());
    }

    public List<MenuItemResponse> listItems(String categoryId, boolean includeUnavailable, boolean includeInactiveCategories) {
        StringBuilder sql = new StringBuilder("""
                SELECT i.item_id, i.category_id, i.name, i.slug, i.description, i.price,
                       i.currency, i.image_url, i.vegetarian, i.vegan, i.allergens,
                       i.is_available, i.display_order
                FROM menu_item i
                INNER JOIN menu_category c ON c.category_id = i.category_id
                WHERE 1 = 1
                """);
        List<Object> parameters = new ArrayList<>();
        if (!includeUnavailable) sql.append(" AND i.is_available = TRUE");
        if (!includeInactiveCategories) sql.append(" AND c.is_active = TRUE");
        if (categoryId != null && !categoryId.isBlank()) {
            sql.append(" AND i.category_id = ?");
            parameters.add(categoryId);
        }
        sql.append(" ORDER BY c.sort_order ASC, i.display_order ASC, i.name ASC");
        return jdbc().query(sql.toString(), itemMapper(), parameters.toArray());
    }

    public MenuCategoryResponse createCategory(CreateCategoryCommand command, String actorUserId) {
        String name = requiredText(command.name(), "Category name", 120);
        String slug = normalizeSlug(command.slug(), name);
        String description = optionalText(command.description(), 500);
        if (command.sortOrder() < 0) throw new IllegalArgumentException("sortOrder must be zero or greater");
        return inTenantTransaction(() -> {
            String id = UUID.randomUUID().toString();
            try {
                jdbc().update("""
                        INSERT INTO menu_category(category_id, name, slug, description, sort_order, is_active)
                        VALUES (?, ?, ?, ?, ?, TRUE)
                        """, id, name, slug, description, command.sortOrder());
            } catch (DuplicateKeyException exception) {
                throw new IllegalArgumentException("A menu category with this slug already exists");
            }
            recordOutbox("MENU_CATEGORY", id, "menu.category.created", Map.of("id", id, "slug", slug));
            recordAudit(actorUserId, "menu.category.created", "MENU_CATEGORY", id, Map.of("slug", slug));
            return new MenuCategoryResponse(id, name, slug, description, command.sortOrder());
        });
    }

    public void setCategoryActive(String categoryId, boolean active, String actorUserId) {
        requireId(categoryId, "categoryId");
        inTenantTransaction(() -> {
            int updated = jdbc().update("UPDATE menu_category SET is_active = ?, updated_at = CURRENT_TIMESTAMP(6) WHERE category_id = ?",
                    active, categoryId);
            if (updated == 0) throw new IllegalArgumentException("Menu category not found");
            recordOutbox("MENU_CATEGORY", categoryId, "menu.category.visibility-changed",
                    Map.of("id", categoryId, "active", active));
            recordAudit(actorUserId, "menu.category.visibility-changed", "MENU_CATEGORY", categoryId,
                    Map.of("active", active));
            return null;
        });
    }

    public MenuItemResponse createItem(CreateMenuItemCommand command, String actorUserId) {
        String categoryId = requiredText(command.categoryId(), "categoryId", 36);
        String name = requiredText(command.name(), "Menu item name", 160);
        String slug = normalizeSlug(command.slug(), name);
        String description = optionalText(command.description(), 2000);
        BigDecimal price = normalizePrice(command.price());
        String currency = command.currency() == null ? "INR" : command.currency().strip().toUpperCase(java.util.Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("currency must be a three-letter ISO code");
        String imageUrl = validateImageUrl(command.imageUrl());
        String allergens = optionalText(command.allergens(), 1000);
        if (command.displayOrder() < 0) throw new IllegalArgumentException("displayOrder must be zero or greater");
        return inTenantTransaction(() -> {
            Integer categoryExists = jdbc().queryForObject(
                    "SELECT COUNT(*) FROM menu_category WHERE category_id = ? AND is_active = TRUE",
                    Integer.class, categoryId);
            if (categoryExists == null || categoryExists == 0) {
                throw new IllegalArgumentException("An active menu category is required");
            }
            String id = UUID.randomUUID().toString();
            try {
                jdbc().update("""
                        INSERT INTO menu_item(item_id, category_id, name, slug, description, price, currency,
                            image_url, vegetarian, vegan, allergens, is_available, display_order)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?)
                        """, id, categoryId, name, slug, description, price, currency, imageUrl,
                        command.vegetarian(), command.vegan(), allergens, command.displayOrder());
            } catch (DuplicateKeyException exception) {
                throw new IllegalArgumentException("A menu item with this slug already exists");
            }
            recordOutbox("MENU_ITEM", id, "menu.item.created", Map.of("id", id, "categoryId", categoryId, "slug", slug));
            recordAudit(actorUserId, "menu.item.created", "MENU_ITEM", id, Map.of("categoryId", categoryId, "slug", slug));
            return new MenuItemResponse(id, categoryId, name, slug, description, price, currency, imageUrl,
                    command.vegetarian(), command.vegan(), allergens, true, command.displayOrder());
        });
    }

    public void setItemAvailability(String itemId, boolean available, String actorUserId) {
        requireId(itemId, "itemId");
        inTenantTransaction(() -> {
            int updated = jdbc().update("UPDATE menu_item SET is_available = ?, updated_at = CURRENT_TIMESTAMP(6) WHERE item_id = ?",
                    available, itemId);
            if (updated == 0) throw new IllegalArgumentException("Menu item not found");
            recordOutbox("MENU_ITEM", itemId, "menu.item.availability-changed",
                    Map.of("id", itemId, "available", available));
            recordAudit(actorUserId, "menu.item.availability-changed", "MENU_ITEM", itemId,
                    Map.of("available", available));
            return null;
        });
    }

    private <T> T inTenantTransaction(Supplier<T> operation) {
        PlatformTransactionManager manager = transactionManagerProvider.get();
        if (manager == null) throw new TenantProvisionerUnavailableException();
        return new TransactionTemplate(manager).execute(status -> operation.get());
    }

    private JdbcTemplate jdbc() {
        JdbcTemplate template = jdbcProvider.get();
        if (template == null) throw new TenantProvisionerUnavailableException();
        return template;
    }

    private void recordOutbox(String aggregateType, String aggregateId, String eventType, Map<String, Object> payload) {
        jdbc().update("""
                INSERT INTO outbox_event(event_id, tenant_id, aggregate_type, aggregate_id, event_type,
                    schema_version, correlation_id, payload)
                VALUES (?, ?, ?, ?, ?, 1, ?, ?)
                """, UUID.randomUUID().toString(), TenantContext.requireCurrent().tenantId(), aggregateType,
                aggregateId, eventType, MDC.get("correlationId"), toJson(payload));
    }

    private void recordAudit(String actorUserId, String action, String resourceType,
                             String resourceId, Map<String, Object> details) {
        jdbc().update("""
                INSERT INTO audit_event(audit_id, actor_user_id, action, resource_type, resource_id,
                    correlation_id, details)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID().toString(), actorUserId, action, resourceType, resourceId,
                MDC.get("correlationId"), toJson(details));
    }

    private String toJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JacksonException exception) { throw new IllegalStateException("Unable to serialize audit/event payload", exception); }
    }

    private static RowMapper<MenuCategoryResponse> categoryMapper() {
        return (rs, row) -> new MenuCategoryResponse(rs.getString("category_id"), rs.getString("name"),
                rs.getString("slug"), rs.getString("description"), rs.getInt("sort_order"));
    }

    private static RowMapper<MenuItemResponse> itemMapper() {
        return (rs, row) -> new MenuItemResponse(rs.getString("item_id"), rs.getString("category_id"),
                rs.getString("name"), rs.getString("slug"), rs.getString("description"), rs.getBigDecimal("price"),
                rs.getString("currency"), rs.getString("image_url"), rs.getBoolean("vegetarian"),
                rs.getBoolean("vegan"), rs.getString("allergens"), rs.getBoolean("is_available"),
                rs.getInt("display_order"));
    }

    private static String normalizeSlug(String supplied, String name) {
        String slug = supplied == null || supplied.isBlank() ? TenantSlugNormalizer.fromName(name)
                : TenantSlugNormalizer.requireSlug(supplied.strip().toLowerCase(java.util.Locale.ROOT));
        return TenantSlugNormalizer.requireSlug(slug);
    }

    private static String requiredText(String value, String field, int max) {
        if (value == null || value.isBlank() || value.strip().length() > max)
            throw new IllegalArgumentException(field + " is required and must be at most " + max + " characters");
        return value.strip();
    }

    private static String optionalText(String value, int max) {
        if (value == null || value.isBlank()) return null;
        if (value.strip().length() > max) throw new IllegalArgumentException("Text field exceeds " + max + " characters");
        return value.strip();
    }

    private static BigDecimal normalizePrice(BigDecimal value) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException("Price must be zero or greater");
        try { return value.setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException exception) { throw new IllegalArgumentException("Price may have at most two decimal places", exception); }
    }

    private static String validateImageUrl(String value) {
        String imageUrl = optionalText(value, 2048);
        if (imageUrl == null) return null;
        try {
            URI uri = URI.create(imageUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null)
                throw new IllegalArgumentException("imageUrl must be an HTTPS URL");
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("imageUrl must be a valid HTTPS URL", exception);
        }
        return imageUrl;
    }

    private static void requireId(String id, String field) {
        if (id == null || id.isBlank() || id.length() > 36) throw new IllegalArgumentException(field + " is required");
    }

    public record CreateCategoryCommand(String name, String slug, String description, int sortOrder) { }
    public record CreateMenuItemCommand(String categoryId, String name, String slug, String description,
                                        BigDecimal price, String currency, String imageUrl,
                                        boolean vegetarian, boolean vegan, String allergens, int displayOrder) { }
}