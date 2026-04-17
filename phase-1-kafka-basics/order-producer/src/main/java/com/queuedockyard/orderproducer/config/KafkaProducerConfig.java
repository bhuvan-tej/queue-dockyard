package com.queuedockyard.orderproducer.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka configuration for the producer.
 *
 * Spring reads all kafka.producer.* properties from application.yml automatically
 * via auto-configuration — we don't need to manually wire KafkaTemplate.
 *
 * What we DO need to configure manually:
 *   - Topic creation (Kafka doesn't create topics automatically in production)
 */
@Configuration
public class KafkaProducerConfig {

    /**
     * Reads the topic name from application.yml
     * so we never hardcode topic names in multiple places.
     */
    @Value("${app.kafka.topic.orders}")
    private String ordersTopic;

    /**
     * Creates the Kafka topic on application startup if it doesn't already exist.
     *
     * partitions(3)  → allows 3 consumers to read in parallel
     * replicas(1)    → only 1 broker locally, so replication factor must be 1
     *                  in production with 3 brokers you'd set this to 3
     */
    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder
                .name(ordersTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

}