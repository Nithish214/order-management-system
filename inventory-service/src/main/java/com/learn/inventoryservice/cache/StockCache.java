package com.learn.inventoryservice.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

// A thin wrapper around StringRedisTemplate, rather than using it directly in
// StockController -- keeps the mechanics of caching (JSON (de)serialization via the same
// ObjectMapper already used for Kafka payloads, a shared TTL, and treating any Redis error
// as a miss/no-op) in exactly one place instead of repeated at every call site. That last
// point matters most: a cache is only ever a speedup, never a dependency the request's
// correctness relies on -- if Redis is briefly unreachable, every method here just falls
// back to "as if nothing was cached" rather than turning a Redis hiccup into a failed
// request for data that Postgres has perfectly fine.
@Component
public class StockCache {

    private static final Logger log = LoggerFactory.getLogger(StockCache.class);

    // A safety net, not the primary invalidation mechanism -- every write path actively
    // deletes the relevant key the moment stock changes (see StockController's restock, and
    // OrderCreatedListener/OrderCancelledListener for the other two ways stock moves), so
    // this only matters if a delete were ever missed entirely (a bug, or stock edited
    // directly in the database).
    private static final Duration TTL = Duration.ofMinutes(5);

    // The naming convention lives here, not in each caller -- three different classes now
    // write to this cache (the restock endpoint, and both Kafka listeners that move stock
    // as orders are created/cancelled), and they all need to agree on the exact same keys
    // for invalidation to actually reach whatever a read populated.
    public static final String LIST_KEY = "stock:list";

    public static String itemKey(Long productId) {
        return "stock:" + productId;
    }

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public StockCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public <T> T get(String key, Class<T> type) {
        String json = safeGet(key);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("Corrupt cache entry for key '{}', treating as a miss: {}", key, e.getMessage());
            return null;
        }
    }

    public <T> List<T> getList(String key, Class<T> elementType) {
        String json = safeGet(key);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, elementType));
        } catch (Exception e) {
            log.warn("Corrupt cache entry for key '{}', treating as a miss: {}", key, e.getMessage());
            return null;
        }
    }

    public void set(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), TTL);
        } catch (Exception e) {
            log.warn("Failed to populate cache key '{}': {}", key, e.getMessage());
        }
    }

    // Deletes, rather than re-populating with a "corrected" value -- the next read rebuilds
    // it from Postgres itself, which can't drift from whatever the database actually
    // contains. Re-populating here directly would mean this class also has to be right
    // about what a "restock" changes, which is exactly the kind of duplicated logic that
    // quietly goes stale when the real business logic changes later and this doesn't.
    public void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Failed to delete cache key '{}' (it will still expire via TTL): {}", key, e.getMessage());
        }
    }

    private String safeGet(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis unavailable reading key '{}', falling back to the database: {}", key, e.getMessage());
            return null;
        }
    }
}
