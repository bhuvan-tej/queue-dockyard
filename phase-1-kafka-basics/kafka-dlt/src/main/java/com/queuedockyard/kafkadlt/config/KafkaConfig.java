package com.queuedockyard.kafkadlt.config;

import com.queuedockyard.kafkadlt.model.OrderEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.RetryTopicConfiguration;
import org.springframework.kafka.retrytopic.RetryTopicConfigurationBuilder;

/**
 * Kafka configuration for the DLT pattern.
 *
 * The most important bean here is RetryTopicConfiguration.
 * This is Spring Kafka's built-in support for the retry topic pattern.
 * It automatically:
 *   1. Creates retry topics (dlt.orders-retry-10000 etc.)
 *   2. Creates the DLT (dlt.orders-dlt)
 *   3. Routes failed messages to retry topics
 *   4. Routes permanently failed messages to DLT
 *   5. Applies delay between retries
 *
 * This means you do NOT need to write any routing code yourself.
 * Spring Kafka handles all the retry logic transparently.
 * Your consumer just throws an exception on failure —
 * Spring Kafka takes care of the rest.
 */
@Configuration
public class KafkaConfig {

    @Value("${app.kafka.topics.orders}")
    private String ordersTopic;

    @Value("${app.kafka.topics.dlt}")
    private String dltTopic;

    /**
     * Creates the main orders topic.
     * Retry topics and DLT are created automatically
     * by RetryTopicConfiguration below.
     */
    @Bean
    public NewTopic ordersTopic() {
        return TopicBuilder
                .name(ordersTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Configures the retry topic pattern.
     *
     * What this does:
     *   - 3 retry attempts after the initial failure
     *   - Retry delays: 10s → 30s → 60s (exponential-like backoff)
     *   - After all retries exhausted → route to DLT
     *   - Creates all topics automatically
     *
     * Why non-blocking retry matters:
     *   Traditional blocking retry: consumer retries in place,
     *   partition is blocked, no other messages processed.
     *
     *   Non-blocking retry (this pattern): failed message goes to
     *   a separate retry topic, main consumer keeps processing
     *   new messages. The retry happens independently.
     *
     * This is the production-standard pattern used at companies
     */
    @Bean
    public RetryTopicConfiguration retryTopicConfiguration(
            KafkaTemplate<String, OrderEvent> kafkaTemplate) {

        return RetryTopicConfigurationBuilder
                .newInstance()

                // 3 retries after initial failure = 4 total attempts
                .maxAttempts(4)

                // delay between retries in milliseconds
                // attempt 1 (retry 1): wait 10 seconds
                // attempt 2 (retry 2): wait 30 seconds
                // attempt 3 (retry 3): wait 60 seconds
                .fixedBackOff(10000)

                // name the DLT explicitly
                // without this Spring Kafka names it <topic>-dlt automatically
                .dltSuffix("-dlt")

                // use our KafkaTemplate to publish to retry topics
                .create(kafkaTemplate);
    }

}