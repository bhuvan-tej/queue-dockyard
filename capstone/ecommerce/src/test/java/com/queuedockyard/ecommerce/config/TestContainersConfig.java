package com.queuedockyard.ecommerce.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Shared Testcontainers configuration for all integration tests.
 *
 * @TestConfiguration — this config is ONLY loaded during tests.
 * Never included in production application context.
 *
 * @ServiceConnection — Spring Boot 3.1+ magic.
 * When you annotate a container bean with @ServiceConnection,
 * Spring Boot automatically reads the container's host and port
 * and overrides the corresponding application properties.
 *
 * For example:
 *   KafkaContainer starts on random port 32768
 *   @ServiceConnection reads that port
 *   Overrides spring.kafka.bootstrap-servers automatically
 *   Your app connects to the test container — not localhost:9092
 *
 * This means you do NOT need @DynamicPropertySource to manually
 * set bootstrap-servers, rabbitmq host/port etc.
 * @ServiceConnection handles it all automatically.
 *
 * Why static containers?
 *   Static fields mean the container starts ONCE per test class
 *   and is reused across all test methods.
 *   Non-static would start a new container per test method —
 *   extremely slow (each Kafka startup takes ~10 seconds).
 */
@TestConfiguration
public class TestContainersConfig {

    /**
     * Real Kafka broker running in Docker.
     *
     * ConfluentKafkaContainer — Confluent's Kafka image.
     * Includes all Kafka functionality including Schema Registry support.
     * Version must match your docker/kafka-compose.yml version.
     *
     * @ServiceConnection automatically sets:
     *   spring.kafka.bootstrap-servers
     */
    @Bean
    @ServiceConnection
    public KafkaContainer kafkaContainer() {
        return new KafkaContainer(
                DockerImageName.parse("confluentinc/cp-kafka:7.7.0")
        );
    }

    /**
     * Real RabbitMQ broker running in Docker.
     *
     * @ServiceConnection automatically sets:
     *   spring.rabbitmq.host
     *   spring.rabbitmq.port
     *   spring.rabbitmq.username
     *   spring.rabbitmq.password
     */
    @Bean
    @ServiceConnection
    public RabbitMQContainer rabbitMQContainer() {
        return new RabbitMQContainer(
                DockerImageName.parse("rabbitmq:3.13-management")
        );
    }

    /**
     * Real Redis running in Docker.
     *
     * Redis does not have a dedicated Testcontainers module —
     * we use GenericContainer which works for any Docker image.
     *
     * @ServiceConnection automatically sets:
     *   spring.data.redis.host
     *   spring.data.redis.port
     */
    @Bean
    @ServiceConnection(name = "redis")
    public GenericContainer<?> redisContainer() {
        return new GenericContainer<>(
                DockerImageName.parse("redis:7.2")
        ).withExposedPorts(6379);
    }

    /**
     * Override SQS queue initializer for tests.
     * LocalStack is not running during tests — we skip SQS
     * queue creation by providing a no-op initializer.
     */
    @Bean
    @Primary
    public SqsQueueInitializer sqsQueueInitializer(SqsClient sqsClient) {
        return new SqsQueueInitializer(sqsClient) {
            @Override
            public void initQueues() {
                // skip SQS queue creation during tests
                // LocalStack is not available in the test environment
            }
        };
    }
}