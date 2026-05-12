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
 * Analytics Service — consumes the same Avro order events as Inventory
 * but via a completely independent consumer group.
 *
 * Both groups receive every message — neither knows the other exists.
 * This is pub/sub at the consumer group level.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyticsConsumer {

    private final MetricsService metricsService;

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

        log.info("ANALYTICS | recording | revenue: {} | customer: {} | items: {}",
                event.getAmount(),
                event.getCustomerId(),
                event.getItems());

        acknowledgment.acknowledge();
        metricsService.recordAnalyticsProcessed();

        log.info("ANALYTICS | recorded | orderId: {}", event.getOrderId());
    }

}