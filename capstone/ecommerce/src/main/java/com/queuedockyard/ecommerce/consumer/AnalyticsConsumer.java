package com.queuedockyard.ecommerce.consumer;

import com.queuedockyard.ecommerce.metrics.MetricsService;
import com.queuedockyard.ecommerce.model.OrderEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Analytics Service — consumes the same Kafka order events
 * as Inventory but completely independently.
 *
 * Uses group-id: ecommerce-analytics-group
 * Different group from InventoryConsumer — receives ALL events
 * regardless of what InventoryConsumer has processed.
 *
 * Key insight:
 * Both InventoryConsumer and AnalyticsConsumer read from the
 * SAME topic (ecommerce.order.events) but have DIFFERENT group IDs.
 * Kafka delivers every message to every group independently.
 * This is pub/sub at the consumer group level.
 *
 * Responsibility: record order analytics (revenue, customer activity).
 */
@Slf4j
@Component
public class AnalyticsConsumer {

    private final MetricsService metricsService;

    public AnalyticsConsumer(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.orders}",
            groupId = "ecommerce-analytics-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderEvent(ConsumerRecord<String, OrderEvent> record,
                             Acknowledgment acknowledgment) {

        OrderEvent event = record.value();

        log.info("ANALYTICS | received | messageId: {} | orderId: {} | amount: {} | customerId: {}",
                event.getMessageId(),
                event.getOrderId(),
                event.getAmount(),
                event.getCustomerId());

        // simulate recording analytics
        log.info("ANALYTICS | recording | revenue: {} | customer: {} | items: {}",
                event.getAmount(),
                event.getCustomerId(),
                event.getItems());

        acknowledgment.acknowledge();

        metricsService.recordAnalyticsProcessed();

        log.info("ANALYTICS | recorded | orderId: {}", event.getOrderId());
    }

}