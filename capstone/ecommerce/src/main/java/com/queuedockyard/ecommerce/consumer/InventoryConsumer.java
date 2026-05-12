package com.queuedockyard.ecommerce.consumer;

import com.queuedockyard.ecommerce.avro.OrderEvent;
import com.queuedockyard.ecommerce.metrics.MetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Inventory Service — consumes Avro-serialized order events from Kafka.
 *
 * The OrderEvent here is the Avro-generated class.
 * The Confluent deserializer fetches the schema from Registry,
 * validates compatibility, and deserializes to this strongly typed class.
 *
 * Status is an Avro enum — the compiler enforces valid values.
 * No null checks for missing fields — the schema guarantees structure.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryConsumer {

    private final MetricsService metricsService;

    @KafkaListener(
            topics = "${app.kafka.topics.orders}",
            groupId = "ecommerce-inventory-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderEvent(ConsumerRecord<String, OrderEvent> record,
                             Acknowledgment acknowledgment) {

        OrderEvent event = record.value();

        log.info("INVENTORY | received Avro event | messageId: {} | orderId: {} | items: {} | partition: {} | offset: {}",
                event.getMessageId(),
                event.getOrderId(),
                event.getItems(),
                record.partition(),
                record.offset());

        // status is an Avro enum — switch is exhaustive, compiler enforces it
        switch (event.getStatus()) {
            case PLACED -> log.info(
                    "INVENTORY | deducting stock | orderId: {} | items: {}",
                    event.getOrderId(), event.getItems());
            case CANCELLED -> log.info(
                    "INVENTORY | restoring stock | orderId: {}",
                    event.getOrderId());
            default -> log.info(
                    "INVENTORY | no stock action for status: {} | orderId: {}",
                    event.getStatus(), event.getOrderId());
        }

        acknowledgment.acknowledge();
        metricsService.recordInventoryProcessed();

        log.info("INVENTORY | processed and committed | orderId: {}",
                event.getOrderId());
    }

}