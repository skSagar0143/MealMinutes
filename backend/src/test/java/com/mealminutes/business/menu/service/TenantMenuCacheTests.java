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

    private static TenantContext.TenantDescriptor tenant() {
        return new TenantContext.TenantDescriptor("tenant-1", "abc-food", "tenant_abc_food", "secret://db");
    }
}
