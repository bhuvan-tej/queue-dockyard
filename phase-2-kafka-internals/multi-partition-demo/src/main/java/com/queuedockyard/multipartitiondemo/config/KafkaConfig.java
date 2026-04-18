package com.queuedockyard.multipartitiondemo.config;

import com.queuedockyard.multipartitiondemo.model.InventoryEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;

/**
 * Kafka configuration for the multi-partition demo.
 *
 * Key difference from Phase 1:
 * We explicitly configure ConcurrentKafkaListenerContainerFactory
 * with setConcurrency(3) — this tells Spring Kafka to spin up
 * 3 consumer threads within a single app instance.
 *
 * This means even with ONE running instance of this app,
 * it can consume from all 3 partitions in parallel.
 *
 * When you run 3 separate instances, each gets 1 partition
 * because Kafka balances across all consumers in the group
 * regardless of whether they are in the same JVM or not.
 */
@Configuration
public class KafkaConfig {

    @Value("${app.kafka.topic.inventory}")
    private String inventoryTopic;

    /**
     * Creates the inventory-events topic with 3 partitions.
     *
     * Why 3 partitions?
     * This matches our demo setup of 3 consumer instances.
     * Rule of thumb: number of partitions = max consumers you expect.
     * You can add partitions later but cannot reduce them.
     */
    @Bean
    public NewTopic inventoryEventsTopic() {
        return TopicBuilder
                .name(inventoryTopic)
                .partitions(3)   // 3 partitions → 3 consumers can work in parallel
                .replicas(1)     // 1 replica — single broker local setup
                .build();
    }

    /**
     * Configures how many threads this single app instance uses
     * to consume from Kafka.
     *
     * setConcurrency(3) → 3 threads → can handle all 3 partitions
     * in a single JVM. Each thread is assigned one partition.
     *
     * This is useful when you want parallelism within one instance
     * rather than running multiple separate instances.
     *
     * For the rebalancing experiment, we run 3 separate instances
     * each with concurrency(1) — clearer to observe what happens.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, InventoryEvent>
    kafkaListenerContainerFactory(ConsumerFactory<String, InventoryEvent> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, InventoryEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);

        // 3 threads — one per partition
        // change this to 1 when running 3 separate instances
        factory.setConcurrency(3);

        return factory;
    }

}