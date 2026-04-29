package com.queuedockyard.kafkadlt.service;

import com.queuedockyard.kafkadlt.model.OrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Publishes order events to the main orders topic.
 *
 * Two methods:
 *   publishOrder        → normal message, processes successfully
 *   publishFailingOrder → shouldFail=true, triggers retry → DLT flow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPublisherService {

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    @Value("${app.kafka.topics.orders}")
    private String ordersTopic;

    /**
     * Publishes a normal order — processed successfully on first attempt.
     */
    public void publishOrder(String customerId, Double amount) {

        OrderEvent event = OrderEvent.builder()
                .messageId(UUID.randomUUID().toString())
                .orderId("ORD-" + UUID.randomUUID().toString()
                        .substring(0, 8).toUpperCase())
                .customerId(customerId)
                .amount(amount)
                .status("PLACED")
                .shouldFail(false)
                .createdAt(LocalDateTime.now())
                .build();

        kafkaTemplate.send(ordersTopic, event.getOrderId(), event);

        log.info("Published normal order | orderId: {} | customerId: {}",
                event.getOrderId(), customerId);
    }

    /**
     * Publishes a failing order — triggers full retry → DLT flow.
     *
     * Watch the logs after calling this:
     *   Attempt 1 → fails → routed to retry-10000 topic
     *   After 10s: Attempt 2 → fails → routed to retry-30000 topic
     *   After 30s: Attempt 3 → fails → routed to retry-60000 topic
     *   After 60s: Attempt 4 → fails → routed to DLT topic
     *   DLT handler fires → ALERT logged
     */
    public void publishFailingOrder(String customerId, Double amount) {

        OrderEvent event = OrderEvent.builder()
                .messageId(UUID.randomUUID().toString())
                .orderId("ORD-" + UUID.randomUUID().toString()
                        .substring(0, 8).toUpperCase())
                .customerId(customerId)
                .amount(amount)
                .status("PLACED")
                .shouldFail(true)
                .createdAt(LocalDateTime.now())
                .build();

        kafkaTemplate.send(ordersTopic, event.getOrderId(), event);

        log.info("Published failing order | orderId: {} | will retry 3 times then DLT",
                event.getOrderId());
    }

}