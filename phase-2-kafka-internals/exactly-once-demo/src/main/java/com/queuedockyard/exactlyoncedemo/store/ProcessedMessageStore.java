package com.queuedockyard.exactlyoncedemo.store;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * In-memory store of processed message IDs.
 *
 * This is the heart of the idempotency pattern.
 * Before processing any message, the consumer checks here.
 * After processing, it records the messageId here.
 *
 * If the same message arrives again (duplicate delivery),
 * the consumer finds its messageId already in this store
 * and skips processing entirely.
 *
 * Production note:
 * In a real system this would be a Redis SET or a database table,
 * not an in-memory store — so it survives application restarts.
 * An in-memory store loses all IDs on restart, making it
 * vulnerable to duplicates after a crash + restart cycle.
 *
 * For this learning, in-memory is sufficient to demonstrate
 * the concept clearly without adding infrastructure complexity.
 */
@Slf4j
@Component
public class ProcessedMessageStore {

    /**
     * Thread-safe set of processed message IDs.
     *
     * LinkedHashSet preserves insertion order — useful for debugging.
     * Wrapped in synchronizedSet for thread safety since multiple
     * Kafka consumer threads may access this concurrently.
     *
     * In production with Redis: SISMEMBER to check, SADD to add.
     * Both are O(1) operations — extremely fast.
     */
    private final Set<String> processedIds = Collections.synchronizedSet(
            new LinkedHashSet<>()
    );

    /**
     * Checks if a message has already been processed.
     *
     * @param messageId the idempotency key to check
     * @return true if already processed, false if new
     */
    public boolean isAlreadyProcessed(String messageId) {
        return processedIds.contains(messageId);
    }

    /**
     * Marks a message as processed after successful handling.
     * Call this AFTER processing succeeds — never before.
     *
     * @param messageId the idempotency key to record
     */
    public void markAsProcessed(String messageId) {
        processedIds.add(messageId);
        log.debug("Marked as processed | messageId: {}", messageId);
    }

    /**
     * Returns how many unique messages have been processed.
     * Used by the REST endpoint to show the current state.
     */
    public int getProcessedCount() {
        return processedIds.size();
    }

    /**
     * Returns all processed message IDs.
     * Used by the REST endpoint for inspection.
     */
    public Set<String> getAllProcessedIds() {
        return Collections.unmodifiableSet(processedIds);
    }

}