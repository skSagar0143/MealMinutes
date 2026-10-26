package com.mealminutes.business.menu.service;

import com.mealminutes.business.menu.api.MenuCategoryResponse;
import com.mealminutes.business.menu.api.MenuItemResponse;
import com.mealminutes.business.menu.api.RestaurantProfileResponse;
import com.mealminutes.platform.tenant.domain.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Supplier;

@Component
public class TenantMenuCache {
    private static final Logger log = LoggerFactory.getLogger(TenantMenuCache.class);
    private static final String KEY_PREFIX = "mealminutes:tenant:";
    private final Supplier<StringRedisTemplate> redisProvider;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final long ttlSeconds;

    @Autowired
    public TenantMenuCache(ObjectProvider<StringRedisTemplate> redisProvider, ObjectMapper objectMapper,
                           @Value("${mealminutes.redis.enabled:false}") boolean enabled,
                           @Value("${mealminutes.redis.cache-ttl-seconds:60}") long ttlSeconds) {
        this(redisProvider::getIfAvailable, objectMapper, enabled, ttlSeconds);
    }

    private TenantMenuCache(Supplier<StringRedisTemplate> redisProvider, ObjectMapper objectMapper,
                            boolean enabled, long ttlSeconds) {
        this.redisProvider = redisProvider;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.ttlSeconds = Math.max(1, Math.min(3600, ttlSeconds));
    }

    TenantMenuCache(StringRedisTemplate redis, ObjectMapper objectMapper, boolean enabled, long ttlSeconds) {
        this(() -> redis, objectMapper, enabled, ttlSeconds);
    }

    public TenantMenuCache() {
        this(() -> null, null, false, 60);
    }

    public RestaurantProfileResponse getProfile() {
        return read("profile", RestaurantProfileResponse.class);
    }

    public void putProfile(RestaurantProfileResponse profile) {
        write("profile", profile);
    }

    public List<MenuCategoryResponse> getCategories() {
        MenuCategoryResponse[] values = read("categories", MenuCategoryResponse[].class);
        return values == null ? null : Arrays.asList(values);
    }

    public void putCategories(List<MenuCategoryResponse> categories) {
        write("categories", categories);
    }

    public List<MenuItemResponse> getItems(String categorySlug, String search, int page, int size) {
        String suffix = "items:" + hash(String.valueOf(categorySlug) + "\u001f" + String.valueOf(search)
                + "\u001f" + page + "\u001f" + size);
        MenuItemResponse[] values = read(suffix, MenuItemResponse[].class);
        return values == null ? null : Arrays.asList(values);
    }

    public void putItems(String categorySlug, String search, int page, int size, List<MenuItemResponse> items) {
        String suffix = "items:" + hash(String.valueOf(categorySlug) + "\u001f" + String.valueOf(search)
                + "\u001f" + page + "\u001f" + size);
        write(suffix, items);
    }

    public void evictCurrentTenant() {
        TenantContext.TenantDescriptor tenant = TenantContext.currentOrNull();
        if (tenant != null) evictTenant(tenant.tenantId());
    }

    public void evictCurrentTenantAfterCommit() {
        TenantContext.TenantDescriptor tenant = TenantContext.currentOrNull();
        if (tenant == null || !enabled) return;
        String tenantId = tenant.tenantId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictTenant(tenantId);
                }
            });
        } else {
            evictTenant(tenantId);
        }
    }

    private <T> T read(String suffix, Class<T> type) {
        String key = cacheKey(suffix);
        if (key == null) return null;
        try {
            String json = redisProvider.get().opsForValue().get(key);
            return json == null ? null : objectMapper.readValue(json, type);
        } catch (Exception failure) {
            log.warn("Redis storefront cache read failed; using database fallback ({})", failure.getClass().getSimpleName());
            return null;
        }
    }

    private void write(String suffix, Object value) {
        String key = cacheKey(suffix);
        if (key == null || value == null) return;
        try {
            String json = objectMapper.writeValueAsString(value);
            redisProvider.getIfAvailable().opsForValue().set(key, json, Duration.ofSeconds(ttlSeconds));
        } catch (Exception failure) {
            log.warn("Redis storefront cache write failed ({})", failure.getClass().getSimpleName());
        }
    }

    private String cacheKey(String suffix) {
        if (!enabled || objectMapper == null || redisProvider.getIfAvailable() == null) return null;
        TenantContext.TenantDescriptor tenant = TenantContext.currentOrNull();
        if (tenant == null) return null;
        String versionKey = KEY_PREFIX + tenant.tenantId() + ":storefront:version";
        try {
            String version = redisProvider.getIfAvailable().opsForValue().get(versionKey);
            return KEY_PREFIX + tenant.tenantId() + ":storefront:v" + (version == null ? "0" : version) + ":" + suffix;
        } catch (RuntimeException failure) {
            log.warn("Redis storefront cache version lookup failed; using database fallback ({})",
                    failure.getClass().getSimpleName());
            return null;
        }
    }

    private void evictTenant(String tenantId) {
        if (!enabled || redisProvider.getIfAvailable() == null) return;
        try {
            redisProvider.getIfAvailable().opsForValue().increment(KEY_PREFIX + tenantId + ":storefront:version");
        } catch (RuntimeException failure) {
            log.error("Unable to invalidate storefront cache for tenant {}; entries expire after {} seconds",
                    tenantId, ttlSeconds, failure);
        }
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

}
