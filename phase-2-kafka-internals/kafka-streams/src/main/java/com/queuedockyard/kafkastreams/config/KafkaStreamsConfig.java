package com.queuedockyard.kafkastreams.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka Streams configuration.
 *
 * @EnableKafkaStreams is the key annotation here.
 * It tells Spring to:
 *   1. Look for @Bean methods that return KStream or KTable
 *   2. Build the stream topology from those beans
 *   3. Start the Kafka Streams runtime on application startup
 *   4. Manage the lifecycle (start, stop, restart on failure)
 *
 * Without @EnableKafkaStreams, your stream topology beans
 * would be created but never started — nothing would process.
 *
 * Topic creation:
 *   We create all input and output topics here.
 *   In production, output topics would be created via
 *   Kafka CLI or Terraform before deploying the streams app.
 */
@Configuration
@EnableKafkaStreams
public class KafkaStreamsConfig {

    @Value("${app.kafka.topics.orders-input}")
    private String ordersInputTopic;

    @Value("${app.kafka.topics.placed-orders}")
    private String placedOrdersTopic;

    @Value("${app.kafka.topics.customer-counts}")
    private String customerCountsTopic;

    @Value("${app.kafka.topics.revenue-per-minute}")
    private String revenuePerMinuteTopic;

    /**
     * Input topic — where OrderEvents come from.
     * This is the same topic the capstone ecommerce app publishes to.
     * If you run both apps simultaneously, real orders feed into streams.
     */
    @Bean
    public NewTopic ordersInputTopic() {
        return TopicBuilder
                .name(ordersInputTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Pipeline 1 output — filtered PLACED orders only.
     * Downstream services that only care about new orders
     * can consume from here instead of filtering themselves.
     */
    @Bean
    public NewTopic placedOrdersTopic() {
        return TopicBuilder
                .name(placedOrdersTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Pipeline 2 output — order count per customer.
     * A KTable changelog topic — each message is the latest
     * count for that customerId (keyed by customerId).
     */
    @Bean
    public NewTopic customerCountsTopic() {
        return TopicBuilder
                .name(customerCountsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Pipeline 3 output — revenue aggregated per 1-minute window.
     * Each message represents one completed time window.
     */
    @Bean
    public NewTopic revenuePerMinuteTopic() {
        return TopicBuilder
                .name(revenuePerMinuteTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

}