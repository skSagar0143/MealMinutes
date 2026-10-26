package com.mealminutes.business.menu.service;

import com.mealminutes.business.menu.api.MenuCategoryResponse;
import com.mealminutes.platform.tenant.domain.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"unchecked", "rawtypes"})
class TenantMenuCacheTests {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final TenantMenuCache cache = new TenantMenuCache(redis, mapper, true, 60);

    TenantMenuCacheTests() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void readsCategoriesUsingTenantAndVersionScopedKey() {
        when(values.get("mealminutes:tenant:tenant-1:storefront:version")).thenReturn("4");
        when(values.get("mealminutes:tenant:tenant-1:storefront:v4:categories"))
                .thenReturn("[{\"categoryId\":\"cat-1\",\"name\":\"Lunch\",\"slug\":\"lunch\",\"description\":null,\"sortOrder\":1}]");

        try (TenantContext.Scope ignored = TenantContext.bind(tenant())) {
            assertThat(cache.getCategories()).containsExactly(
                    new MenuCategoryResponse("cat-1", "Lunch", "lunch", null, 1));
        }
    }

    @Test
    void writesJsonWithConfiguredTtl() {
        when(values.get("mealminutes:tenant:tenant-1:storefront:version")).thenReturn("2");
        try (TenantContext.Scope ignored = TenantContext.bind(tenant())) {
            cache.putCategories(List.of(new MenuCategoryResponse("cat-1", "Lunch", "lunch", null, 1)));
        }

        verify(values).set(eq("mealminutes:tenant:tenant-1:storefront:v2:categories"), anyString(),
                eq(Duration.ofSeconds(60)));
    }

    @Test
    void invalidatesCurrentTenantByIncrementingItsVersion() {
        try (TenantContext.Scope ignored = TenantContext.bind(tenant())) {
            cache.evictCurrentTenant();
        }
        verify(values).increment("mealminutes:tenant:tenant-1:storefront:version");
    }

    @Test
    void fallsBackToDatabaseWhenRedisIsUnavailable() {
        when(values.get("mealminutes:tenant:tenant-1:storefront:version"))
                .thenThrow(new RedisConnectionFailureException("redis unavailable"));
        try (TenantContext.Scope ignored = TenantContext.bind(tenant())) {
            assertThat(cache.getCategories()).isNull();
        }
    }

    @Test
    void bypassesCacheWhenNoTenantContextExists() {
        assertThat(cache.getCategories()).isNull();
        verifyNoInteractions(redis, values);
    }

    @Test
    void readsCachedRestaurantProfile() {
        when(values.get("mealminutes:tenant:tenant-1:storefront:version")).thenReturn("1");
        when(values.get("mealminutes:tenant:tenant-1:storefront:v1:profile"))
                .thenReturn("{\"id\":\"profile-1\",\"name\":\"Local Kitchen\",\"description\":null,\"logoUrl\":null,\"supportEmail\":null,\"supportPhone\":null,\"currency\":\"INR\",\"timezone\":\"Asia/Kolkata\"}");
        try (TenantContext.Scope ignored = TenantContext.bind(tenant())) {
            assertThat(cache.getProfile()).isEqualTo(new com.mealminutes.business.menu.api.RestaurantProfileResponse(
                    "profile-1", "Local Kitchen", null, null, null, null, "INR", "Asia/Kolkata"));
        }
    }

    @Test
    void readsItemPageWithoutPuttingRawSearchTextInTheRedisKey() {
        when(values.get("mealminutes:tenant:tenant-1:storefront:version")).thenReturn("3");
        when(values.get(startsWith("mealminutes:tenant:tenant-1:storefront:v3:items:")))
                .thenReturn("[{\"id\":\"item-1\",\"categoryId\":\"cat-1\",\"name\":\"Veg Bowl\",\"slug\":\"veg-bowl\",\"description\":null,\"price\":12.50,\"currency\":\"INR\",\"imageUrl\":null,\"vegetarian\":true,\"vegan\":true,\"allergens\":null,\"available\":true,\"displayOrder\":1}]");
        try (TenantContext.Scope ignored = TenantContext.bind(tenant())) {
            var result = cache.getItems(null, "customer-private-search", 0, 20);
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().name()).isEqualTo("Veg Bowl");
        }
        verify(values, never()).get(contains("customer-private-search"));
    }

    @Test
    void usesVersionZeroWhenTenantHasNoCacheVersionYet() {
        when(values.get("mealminutes:tenant:tenant-1:storefront:version")).thenReturn(null);
        try (TenantContext.Scope ignored = TenantContext.bind(tenant())) {
            cache.putCategories(List.of(new MenuCategoryResponse("cat-1", "Lunch", "lunch", null, 1)));
        }
        verify(values).set(eq("mealminutes:tenant:tenant-1:storefront:v0:categories"), anyString(),
                eq(Duration.ofSeconds(60)));
    }

    @Test
    void invalidatesAfterCommitWhenTransactionSynchronizationIsActive() {
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try (TenantContext.Scope ignored = TenantContext.bind(tenant())) {
            cache.evictCurrentTenantAfterCommit();
            verify(values, never()).increment(anyString());
            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                    .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);
            verify(values).increment("mealminutes:tenant:tenant-1:storefront:version");
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static TenantContext.TenantDescriptor tenant() {
        return new TenantContext.TenantDescriptor("tenant-1", "abc-food", "tenant_abc_food", "secret://db");
    }
}
