package com.queuedockyard.ecommerce.config;

import com.queuedockyard.ecommerce.avro.OrderEvent;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for the ecommerce capstone.
 *
 * Uses Avro serialization with Schema Registry instead of JSON.
 *
 * Why this matters:
 *   JSON: producer renames "amount" → consumer gets null silently
 *   Avro: producer tries to rename "amount" → Schema Registry rejects it
 *         consumer never breaks
 *
 * Every message now starts with 5 bytes:
 *   [0x00][schema-id-4-bytes][avro binary payload]
 * Consumer reads schema ID → fetches schema → deserializes correctly.
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${app.kafka.topics.orders}")
    private String ordersTopic;

    // Schema Registry URL — where schemas are registered and fetched
    private static final String SCHEMA_REGISTRY_URL = "http://localhost:8081";

    // ── Topic creation ──────────────────────────────────────────

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder
                .name(ordersTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    // ── Producer ───────────────────────────────────────────────

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();

        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class);

        // Confluent Avro serializer — registers schema on first publish,
        // serializes to compact binary, prepends schema ID header
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                KafkaAvroSerializer.class);

        props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG,
                SCHEMA_REGISTRY_URL);

        // auto-register schemas on first publish
        // set to false in production — schemas registered via CI/CD only
        props.put(AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS, true);

        // idempotent producer — prevents duplicate messages from retries
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);

        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // ── Consumer ───────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();

        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);

        // Confluent Avro deserializer — reads schema ID from message header,
        // fetches schema from Registry, deserializes to Java object
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                KafkaAvroDeserializer.class);

        props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG,
                SCHEMA_REGISTRY_URL);

        // deserialize into specific generated class (OrderEvent)
        // without this you get GenericRecord instead — useless
        props.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG,
                true);

        // disable auto-commit — we commit manually after processing
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object>
    kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        // manual offset commit — commit only after successful processing
        factory.getContainerProperties()
                .setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }

}