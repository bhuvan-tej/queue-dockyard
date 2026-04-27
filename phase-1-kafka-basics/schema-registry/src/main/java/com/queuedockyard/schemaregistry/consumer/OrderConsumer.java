package com.queuedockyard.schemaregistry.consumer;

import com.queuedockyard.schema.OrderEvent;
import com.queuedockyard.schema.OrderEventV2;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes Avro-serialized order events from both topics.
 *
 * The Confluent deserializer handles schema fetching transparently.
 * You just declare the type you expect and it works.
 *
 * Key thing to observe in logs:
 *   The consumer receives strongly typed OrderEvent and OrderEventV2
 *   objects — not raw bytes, not generic maps, not JSON strings.
 *   The schema enforces the contract end to end.
 */
@Slf4j
@Component
public class OrderConsumer {

    /**
     * Consumes V1 order events.
     *
     * The type OrderEvent here is the Avro-generated class.
     * The deserializer fetches the schema from Registry,
     * validates it matches what we expect, and deserializes.
     */
    @KafkaListener(
            topics = "${app.kafka.topics.orders-v1}",
            groupId = "schema-registry-group"
    )
    public void onV1Order(ConsumerRecord<String, OrderEvent> record) {

        OrderEvent event = record.value();

        log.info("V1 CONSUMER | received | orderId: {} | customerId: {} | amount: {} | status: {}",
                event.getOrderId(),
                event.getCustomerId(),
                event.getAmount(),
                event.getStatus());

        // status is an Avro enum — not a plain String
        // the compiler enforces you handle it correctly
        switch (event.getStatus()) {
            case PLACED    -> log.info("V1 CONSUMER | new order placed | orderId: {}", event.getOrderId());
            case CONFIRMED -> log.info("V1 CONSUMER | order confirmed | orderId: {}", event.getOrderId());
            case CANCELLED -> log.info("V1 CONSUMER | order cancelled | orderId: {}", event.getOrderId());
            default        -> log.info("V1 CONSUMER | status: {} | orderId: {}", event.getStatus(), event.getOrderId());
        }
    }

    /**
     * Consumes V2 order events.
     *
     * V2 has two additional fields — region and customerEmail.
     * Both are accessible directly on the generated class.
     * No casting, no null checks for missing fields — the schema
     * guarantees defaults are applied if the field was missing.
     */
    @KafkaListener(
            topics = "${app.kafka.topics.orders-v2}",
            groupId = "schema-registry-group"
    )
    public void onV2Order(ConsumerRecord<String, OrderEventV2> record) {

        OrderEventV2 event = record.value();

        log.info("V2 CONSUMER | received | orderId: {} | customerId: {} | amount: {} | region: {} | email: {}",
                event.getOrderId(),
                event.getCustomerId(),
                event.getAmount(),
                event.getRegion(),
                event.getCustomerEmail());
    }

}