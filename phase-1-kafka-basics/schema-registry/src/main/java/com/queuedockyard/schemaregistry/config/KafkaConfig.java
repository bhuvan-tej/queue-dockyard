package com.queuedockyard.schemaregistry.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Creates the demo topics on startup.
 *
 * Two separate topics — one per schema version.
 * In production you would typically evolve the schema
 * on the same topic — both are shown here for clarity.
 */
@Configuration
public class KafkaConfig {

    @Value("${app.kafka.topics.orders-v1}")
    private String ordersV1Topic;

    @Value("${app.kafka.topics.orders-v2}")
    private String ordersV2Topic;

    @Bean
    public NewTopic ordersV1Topic() {
        return TopicBuilder
                .name(ordersV1Topic)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic ordersV2Topic() {
        return TopicBuilder
                .name(ordersV2Topic)
                .partitions(1)
                .replicas(1)
                .build();
    }

}