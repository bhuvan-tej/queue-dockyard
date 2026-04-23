package com.queuedockyard.ecommerce.config;

import com.queuedockyard.ecommerce.model.OrderEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

/**
 * Kafka configuration for the capstone.
 *
 * One topic: ecommerce.order.events
 * Two consumer groups read this independently:
 *   ecommerce-inventory-group → Inventory Service
 *   ecommerce-analytics-group → Analytics Service
 *
 * Both groups receive every order event.
 * Neither knows the other exists — fully decoupled.
 */
@Configuration
public class KafkaConfig {

    @Value("${app.kafka.topics.orders}")
    private String ordersTopic;

    /**
     * Creates the orders topic with 3 partitions.
     * Supports up to 3 parallel consumers per group.
     */
    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder
                .name(ordersTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Manual ACK mode — offset committed only after
     * successful processing. Prevents message loss on crash.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderEvent>
    kafkaListenerContainerFactory(
            ConsumerFactory<String, OrderEvent> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, OrderEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties()
                .setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        return factory;
    }

}