package com.queuedockyard.orderproducer.service;

import com.queuedockyard.orderproducer.model.OrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Responsible for publishing order events to Kafka.
 *
 * KafkaTemplate is Spring's wrapper around the Kafka producer client.
 * It handles serialization, partitioning, and async send internally.
 *
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderProducerService {

    /**
     * Spring auto-configures this based on your application.yml settings.
     * Generic types: <String, OrderEvent>
     *   String     → the message key (orderId)
     *   OrderEvent → the message value (the actual payload)
     */
    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    @Value("${app.kafka.topic.orders}")
    private String ordersTopic;

    /**
     * Publishes an order event to Kafka.
     *
     * Key insight: we use orderId as the message KEY.
     * Kafka uses the key to determine which partition to write to.
     * Same orderId → same partition → guaranteed ordering for that order.
     *
     * The send is asynchronous — this method returns immediately.
     * The CompletableFuture callback tells us if it succeeded or failed.
     *
     * @param event the order event to publish
     */
    public void publishOrderEvent(OrderEvent event) {

        // send(topic, key, value)
        // key = orderId ensures all events for the same order go to same partition
        CompletableFuture<SendResult<String, OrderEvent>> future =
                kafkaTemplate.send(ordersTopic, event.getOrderId(), event);

        // callback — fires when Kafka confirms the message was written
        future.whenComplete((result, exception) -> {

            if (exception != null) {
                // Kafka failed to write after all retries — log and alert
                log.error("Failed to publish order event for orderId: {} | reason: {}",
                        event.getOrderId(), exception.getMessage());
                return;
            }

            // successfully written — log partition and offset for traceability
            log.info("Order event published | orderId: {} | topic: {} | partition: {} | offset: {}",
                    event.getOrderId(),
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        });
    }

}