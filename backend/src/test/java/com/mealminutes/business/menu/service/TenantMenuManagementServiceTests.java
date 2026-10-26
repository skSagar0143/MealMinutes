package com.mealminutes.business.menu.service;

import tools.jackson.databind.ObjectMapper;
import com.mealminutes.platform.tenant.domain.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TenantMenuManagementServiceTests {
    @AfterEach void clearTenant() { TenantContext.clear(); }

    @Test void createsCategoryAndWritesAuditAndOutboxInOneTenantTransaction() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PlatformTransactionManager tx = transactionManager();
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        var service = new TenantMenuManagementService(jdbc, tx, new ObjectMapper());
        try (var ignored = TenantContext.bind(tenant())) {
            var category = service.createCategory(
                    new TenantMenuManagementService.CreateCategoryCommand("Breakfast & Brunch", null, null, 2), "user-1");
            assertThat(category.name()).isEqualTo("Breakfast & Brunch");
            assertThat(category.slug()).isEqualTo("breakfast-brunch");
            assertThat(category.sortOrder()).isEqualTo(2);
        }
        verify(jdbc, times(3)).update(anyString(), any(Object[].class));
        verify(tx).commit(any(TransactionStatus.class));
    }

    @Test void createsMenuItemOnlyForActiveCategoryAndRecordsEvents() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PlatformTransactionManager tx = transactionManager();
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("category-1"))).thenReturn(1);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        var service = new TenantMenuManagementService(jdbc, tx, new ObjectMapper());
        try (var ignored = TenantContext.bind(tenant())) {
            var item = service.createItem(new TenantMenuManagementService.CreateMenuItemCommand(
                    "category-1", "Café Latte", null, null, new BigDecimal("2.50"), "INR",
                    "https://cdn.example.com/latte.jpg", true, false, null, 0), "user-1");
            assertThat(item.slug()).isEqualTo("cafe-latte");
            assertThat(item.price()).isEqualByComparingTo("2.50");
            assertThat(item.available()).isTrue();
        }
        verify(jdbc, times(3)).update(anyString(), any(Object[].class));
        verify(tx).commit(any(TransactionStatus.class));
    }

    @Test void rejectsInvalidPriceAndUnsafeImageUrlBeforeDatabaseWrites() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var service = new TenantMenuManagementService(jdbc, transactionManager(), new ObjectMapper());
        assertThatThrownBy(() -> service.createItem(new TenantMenuManagementService.CreateMenuItemCommand(
                "category-1", "Tea", null, null, new BigDecimal("-1.00"), "INR", null,
                false, false, null, 0), "user-1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createItem(new TenantMenuManagementService.CreateMenuItemCommand(
                "category-1", "Tea", null, null, new BigDecimal("1.00"), "INR", "javascript:alert(1)",
                false, false, null, 0), "user-1")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbc);
    }

    @Test void duplicateCategorySlugRollsBackTransaction() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PlatformTransactionManager tx = transactionManager();
        when(jdbc.update(anyString(), any(Object[].class))).thenThrow(new DuplicateKeyException("duplicate"));
        var service = new TenantMenuManagementService(jdbc, tx, new ObjectMapper());
        try (var ignored = TenantContext.bind(tenant())) {
            assertThatThrownBy(() -> service.createCategory(
                    new TenantMenuManagementService.CreateCategoryCommand("Coffee", "coffee", null, 0), "user-1"))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already exists");
        }
        verify(tx).rollback(any(TransactionStatus.class));
    }

    @Test void failsClosedWhenTenantDatasourceOrTransactionManagerIsMissing() {
        var service = new TenantMenuManagementService(() -> null, () -> null, new ObjectMapper());
        assertThatThrownBy(() -> service.createCategory(
                new TenantMenuManagementService.CreateCategoryCommand("Coffee", null, null, 0), "user-1"))
                .isInstanceOf(com.mealminutes.platform.tenant.service.TenantProvisionerUnavailableException.class);
    }

    @Test void listsCategoriesWithoutInactiveRowsByDefault() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        doReturn(List.of()).when(jdbc).query(anyString(), any(RowMapper.class));
        var service = new TenantMenuManagementService(jdbc, transactionManager(), new ObjectMapper());
        assertThat(service.listCategories(false)).isEmpty();
        verify(jdbc).query(contains("WHERE is_active = TRUE"), any(RowMapper.class));
    }

    private PlatformTransactionManager transactionManager() {
        PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any(TransactionDefinition.class))).thenReturn(mock(TransactionStatus.class));
        return tx;
    }
    private TenantContext.TenantDescriptor tenant() {
        return new TenantContext.TenantDescriptor("tenant-1", "abc-food", "tenant_abc_food", "secret://abc");
    }

    @Test void listsItemsWithoutOptionalFiltersWhenRequested() {
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        assertThat(service.listItems(null, true, true)).isEmpty();
        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        assertThat(sql.getValue()).contains("WHERE 1 = 1")
                .doesNotContain("i.is_available = TRUE", "c.is_active = TRUE", "i.category_id = ?");
    }

    @Test void defaultsCurrencyAndAcceptsExplicitMenuSlug() {
        bindTenant();
        when(jdbc.queryForObject(contains("SELECT COUNT(*) FROM menu_category"),eq(Integer.class),eq("cat-1"))).thenReturn(1);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        MenuItemResponse result=service.createItem(new TenantMenuManagementService.CreateMenuItemCommand(
                "cat-1","Green Tea","green-tea",null,new BigDecimal("5.00"),null,null,false,true,"  ",0),"user-1");
        assertThat(result.slug()).isEqualTo("green-tea");
        assertThat(result.currency()).isEqualTo("INR");
        assertThat(result.imageUrl()).isNull();
        assertThat(result.allergens()).isNull();
    }

    @Test void rejectsDuplicateMenuItemSlugAndOversizedOptionalText() {
        bindTenant();
        when(jdbc.queryForObject(contains("SELECT COUNT(*) FROM menu_category"),eq(Integer.class),eq("cat-1"))).thenReturn(1);
        when(jdbc.update(contains("INSERT INTO menu_item"),any(Object[].class)))
                .thenThrow(new DuplicateKeyException("duplicate"));
        assertThatThrownBy(() -> service.createItem(new TenantMenuManagementService.CreateMenuItemCommand(
                "cat-1","Tea","tea",null,new BigDecimal("1.00"),"INR",null,false,false,null,0),"user-1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already exists");
        assertThatThrownBy(() -> service.createCategory(new TenantMenuManagementService.CreateCategoryCommand(
                "Drinks",null,"x".repeat(501),0),"user-1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createItem(new TenantMenuManagementService.CreateMenuItemCommand(
                "cat-1","Tea",null,null,null,"INR",null,false,false,null,0),"user-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

}