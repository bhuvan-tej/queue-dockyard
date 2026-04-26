package com.queuedockyard.ecommerce.store;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis-backed idempotency key store.
 *
 * Replaces the in-memory approach used in Phase 3 exactly-once.
 *
 * Why Redis instead of in-memory?
 *
 * In-memory problems:
 *   - Lost on application restart → duplicates possible after crash
 *   - Not shared across multiple instances → each instance has its own store
 *   - Grows unbounded → memory leak over time
 *
 * Redis advantages:
 *   - Persists across restarts → no duplicates after crash
 *   - Shared across all instances → works with horizontal scaling
 *   - TTL-based expiry → automatically cleans up old keys
 *   - O(1) SET and GET → extremely fast
 *
 * TTL strategy:
 *   We set a 24-hour TTL on each key.
 *   If the same messageId arrives after 24 hours, it will be
 *   processed again — acceptable because genuine duplicates
 *   across 24 hours are extremely rare in practice.
 *   Adjust TTL based on your system's redelivery window.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisIdempotencyStore {

    private final StringRedisTemplate redisTemplate;

    /**
     * Redis key prefix — namespaces our keys to avoid
     * collisions with other apps using the same Redis instance.
     */
    private static final String KEY_PREFIX = "ecommerce:processed:";

    /**
     * How long to remember a processed messageId.
     * After this duration, the key expires and the messageId
     * is no longer considered duplicate.
     */
    private static final Duration TTL = Duration.ofHours(24);

    /**
     * Checks if a message has already been processed.
     *
     * Uses Redis GET — O(1) operation.
     *
     * @param messageId the idempotency key to check
     * @return true if already processed, false if new
     */
    public boolean isAlreadyProcessed(String messageId) {
        return redisTemplate.hasKey(KEY_PREFIX + messageId);
    }

    /**
     * Marks a message as processed after successful handling.
     *
     * Uses Redis SET with TTL — O(1) operation.
     * Key automatically expires after 24 hours.
     *
     * IMPORTANT: Call this AFTER processing succeeds, never before.
     * If you mark as processed before processing and then crash,
     * the message will never be processed — silent data loss.
     *
     * @param messageId the idempotency key to record
     */
    public void markAsProcessed(String messageId) {
        redisTemplate.opsForValue().set(
                KEY_PREFIX + messageId,
                "1",        // value doesn't matter — we only care if key exists
                TTL
        );
        log.debug("Marked as processed in Redis | messageId: {}", messageId);
    }

    /**
     * Returns how many processed keys currently exist in Redis.
     * Approximate — Redis KEYS command is not exact under load.
     * Used for monitoring and debugging only.
     */
    public long getProcessedCount() {
        var keys = redisTemplate.keys(KEY_PREFIX + "*");
        return keys == null ? 0 : keys.size();
    }

}