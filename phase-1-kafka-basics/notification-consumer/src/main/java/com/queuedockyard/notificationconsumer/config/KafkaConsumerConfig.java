package com.queuedockyard.notificationconsumer.config;

import org.springframework.context.annotation.Configuration;

/**
 * Kafka consumer configuration.
 *
 * Spring Boot auto-configures the ConsumerFactory and
 * KafkaListenerContainerFactory from application.yml automatically.
 * We don't need to manually define these beans for basic usage.
 *
 * This class exists as a placeholder for when you need to:
 *   - Configure custom error handlers (Phase 3)
 *   - Set up Dead Letter Queue routing (Phase 4)
 *   - Configure concurrency (how many threads poll Kafka)
 *   - Set up manual offset committing
 *
 * For now — application.yml handles everything.
 */
@Configuration
public class KafkaConsumerConfig {

    /*
     * Example of what you would add in Phase 3:
     *
     * @Bean
     * public ConcurrentKafkaListenerContainerFactory<String, OrderEvent> kafkaListenerContainerFactory() {
     *     ConcurrentKafkaListenerContainerFactory<String, OrderEvent> factory =
     *             new ConcurrentKafkaListenerContainerFactory<>();
     *     factory.setConcurrency(3);  // one thread per partition
     *     factory.setCommonErrorHandler(new DefaultErrorHandler());
     *     return factory;
     * }
     */

}
