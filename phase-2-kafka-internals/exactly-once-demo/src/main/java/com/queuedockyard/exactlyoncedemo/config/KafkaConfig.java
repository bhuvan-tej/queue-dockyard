package com.queuedockyard.exactlyoncedemo.config;

import com.queuedockyard.exactlyoncedemo.model.PaymentEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

/**
 * Kafka configuration for the exactly-once demo.
 *
 * The critical setting here is AckMode.MANUAL_IMMEDIATE.
 *
 * Default behavior (auto-commit):
 *   Kafka commits offsets on a timer (every 5 seconds by default).
 *   If your consumer crashes between the auto-commit and finishing
 *   the actual work — the message is silently lost.
 *
 * Manual commit behavior:
 *   The offset is committed ONLY when your code explicitly calls
 *   acknowledgment.acknowledge(). This gives you full control:
 *   commit after successful processing, don't commit on failure.
 */
@Configuration
public class KafkaConfig {

    @Value("${app.kafka.topic.payments}")
    private String paymentsTopic;

    /**
     * Creates the payment-events topic.
     * 3 partitions — supports up to 3 parallel consumer threads.
     */
    @Bean
    public NewTopic paymentEventsTopic() {
        return TopicBuilder
                .name(paymentsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Configures the listener container factory with MANUAL_IMMEDIATE ack mode.
     *
     * MANUAL_IMMEDIATE means: commit the offset to Kafka immediately
     * when acknowledgment.acknowledge() is called in the listener method.
     *
     * This is what allows the idempotent consumer to say:
     * "I have successfully processed this message — now commit the offset."
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentEvent>
    kafkaListenerContainerFactory(ConsumerFactory<String, PaymentEvent> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, PaymentEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);

        // MANUAL_IMMEDIATE — offset committed only when we call ack.acknowledge()
        factory.getContainerProperties().setAckMode(
                ContainerProperties.AckMode.MANUAL_IMMEDIATE
        );

        return factory;
    }

}