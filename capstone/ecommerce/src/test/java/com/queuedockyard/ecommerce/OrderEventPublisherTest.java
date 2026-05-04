package com.queuedockyard.ecommerce;

import com.queuedockyard.ecommerce.config.TestContainersConfig;
import com.queuedockyard.ecommerce.metrics.MetricsService;
import com.queuedockyard.ecommerce.model.OrderEvent;
import com.queuedockyard.ecommerce.service.OrderEventPublisher;
import com.queuedockyard.ecommerce.store.RedisIdempotencyStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for OrderEventPublisher.
 *
 * Tests the publisher in isolation — verifies it:
 *   1. Generates a unique messageId per publish
 *   2. Stores the messageId in Redis after publishing
 *   3. Returns the messageId to the caller
 *   4. Does not re-publish if messageId already in Redis
 *
 * Uses the same Testcontainers setup as the pipeline test —
 * real Kafka, RabbitMQ, and Redis.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
class OrderEventPublisherTest {

    @Autowired
    private OrderEventPublisher publisher;

    @Autowired
    private RedisIdempotencyStore idempotencyStore;

    @Autowired
    private MetricsService metricsService;

    // ── Test 1 ─────────────────────────────────────────────────

    @Test
    @DisplayName("publishOrderEvent should return a non-null messageId")
    void publishOrderEvent_shouldReturnNonNullMessageId() {

        String messageId = publisher.publishOrderEvent(
                "ORD-PUB-001",
                "CUST-PUB-001",
                "pub@example.com",
                "+91-1111111111",
                1500.00,
                "Publisher Test Item"
        );

        assertThat(messageId)
                .as("publishOrderEvent should return a messageId")
                .isNotNull()
                .isNotEmpty();
    }

    // ── Test 2 ─────────────────────────────────────────────────

    @Test
    @DisplayName("publishOrderEvent should generate unique messageId per call")
    void publishOrderEvent_shouldGenerateUniqueMessageIdPerCall() {

        String messageId1 = publisher.publishOrderEvent(
                "ORD-PUB-002",
                "CUST-PUB-002",
                "pub2@example.com",
                "+91-2222222222",
                2000.00,
                "Item A"
        );

        String messageId2 = publisher.publishOrderEvent(
                "ORD-PUB-003",
                "CUST-PUB-003",
                "pub3@example.com",
                "+91-3333333333",
                3000.00,
                "Item B"
        );

        assertThat(messageId1)
                .as("Each publish should generate a unique messageId")
                .isNotEqualTo(messageId2);
    }

    // ── Test 3 ─────────────────────────────────────────────────

    @Test
    @DisplayName("publishOrderEvent should store messageId in Redis after publishing")
    void publishOrderEvent_shouldStoreMessageIdInRedis() {

        String messageId = publisher.publishOrderEvent(
                "ORD-PUB-004",
                "CUST-PUB-004",
                "pub4@example.com",
                "+91-4444444444",
                4000.00,
                "Redis Test Item"
        );

        // wait for async publishing to complete and Redis to be updated
        await()
                .atMost(5, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() ->
                        assertThat(idempotencyStore.isAlreadyProcessed(messageId))
                                .as("messageId should be in Redis after publishing")
                                .isTrue()
                );
    }

    // ── Test 4 ─────────────────────────────────────────────────

    @Test
    @DisplayName("messageId should be a valid UUID format")
    void publishOrderEvent_messageIdShouldBeValidUUID() {

        String messageId = publisher.publishOrderEvent(
                "ORD-PUB-005",
                "CUST-PUB-005",
                "pub5@example.com",
                "+91-5555555511",
                500.00,
                "UUID Test Item"
        );

        assertThat(messageId)
                .as("messageId should match UUID format")
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    // ── Test 5 ─────────────────────────────────────────────────

    @Test
    @DisplayName("Redis idempotency store should detect already processed messageId")
    void redisIdempotencyStore_shouldDetectAlreadyProcessedMessage() {

        String messageId = publisher.publishOrderEvent(
                "ORD-PUB-006",
                "CUST-PUB-006",
                "pub6@example.com",
                "+91-6666666611",
                600.00,
                "Idempotency Test Item"
        );

        // wait for Redis write
        await()
                .atMost(5, TimeUnit.SECONDS)
                .until(() -> idempotencyStore.isAlreadyProcessed(messageId));

        // now check it's truly detected as duplicate
        assertThat(idempotencyStore.isAlreadyProcessed(messageId))
                .as("Redis should detect already processed messageId")
                .isTrue();

        // and a random new UUID should NOT be detected as processed
        assertThat(idempotencyStore.isAlreadyProcessed(
                "00000000-0000-0000-0000-000000000000"))
                .as("Unknown messageId should not be in Redis")
                .isFalse();
    }

}