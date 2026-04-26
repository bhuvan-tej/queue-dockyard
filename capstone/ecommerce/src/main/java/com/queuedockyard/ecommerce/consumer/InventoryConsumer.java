package com.queuedockyard.ecommerce.consumer;

import com.queuedockyard.ecommerce.metrics.MetricsService;
import com.queuedockyard.ecommerce.model.OrderEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Inventory Service — consumes order events from Kafka.
 *
 * Responsibility: update stock levels when an order is placed.
 *
 * Uses group-id: ecommerce-inventory-group
 * This is a DIFFERENT group from AnalyticsConsumer —
 * both receive every order event independently.
 *
 * In a real microservices setup, this would be a completely
 * separate Spring Boot application. Here it lives in the same
 * app to keep the capstone self-contained.
 */
@Slf4j
@Component
public class InventoryConsumer {

    private final MetricsService metricsService;

    public InventoryConsumer(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.orders}",
            groupId = "ecommerce-inventory-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderEvent(ConsumerRecord<String, OrderEvent> record,
                             Acknowledgment acknowledgment) {

        OrderEvent event = record.value();

        log.info("INVENTORY | received | messageId: {} | orderId: {} | items: {} | partition: {} | offset: {}",
                event.getMessageId(),
                event.getOrderId(),
                event.getItems(),
                record.partition(),
                record.offset());

        // simulate stock deduction
        log.info("INVENTORY | deducting stock for items: {} | orderId: {}",
                event.getItems(), event.getOrderId());

        // commit offset — message fully processed
        acknowledgment.acknowledge();

        metricsService.recordInventoryProcessed();

        log.info("INVENTORY | stock updated | orderId: {}", event.getOrderId());
    }

}