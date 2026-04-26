package com.queuedockyard.kafkastreams.service;

import com.queuedockyard.kafkastreams.model.OrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Publishes test order events to the input topic.
 *
 * This exists purely to feed data into the stream pipelines
 * without needing the capstone ecommerce app running.
 *
 * In a real setup, the ecommerce app (or any producer) would
 * publish to order-events and the streams would process them.
 * Here we simulate that with a REST-triggered publisher.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    @Value("${app.kafka.topics.orders-input}")
    private String ordersInputTopic;

    /**
     * Publishes a single order event.
     *
     * @param customerId the customer placing the order
     * @param amount     order value
     * @param status     order status — try PLACED, CONFIRMED, CANCELLED
     */
    public void publishOrder(String customerId, Double amount, String status) {

        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        OrderEvent event = OrderEvent.builder()
                .messageId(UUID.randomUUID().toString())
                .orderId(orderId)
                .customerId(customerId)
                .amount(amount)
                .items("Item A, Item B")
                .status(status)
                .createdAt(LocalDateTime.now())
                .build();

        kafkaTemplate.send(ordersInputTopic, orderId, event);

        log.info("Published | orderId: {} | customerId: {} | amount: {} | status: {}",
                orderId, customerId, amount, status);
    }

    /**
     * Publishes a batch of mixed orders across multiple customers.
     * Good for observing all three pipelines simultaneously.
     *
     * Mix of statuses — only PLACED ones pass Pipeline 1.
     * Multiple orders per customer — Pipeline 2 counts them.
     * Various amounts — Pipeline 3 sums them per window.
     */
    public void publishBatch() {

        List<Object[]> orders = List.of(
                new Object[]{"CUST-001", 1500.00, "PLACED"},
                new Object[]{"CUST-002", 2500.00, "PLACED"},
                new Object[]{"CUST-001", 800.00,  "CONFIRMED"},   // filtered by Pipeline 1
                new Object[]{"CUST-003", 3200.00, "PLACED"},
                new Object[]{"CUST-002", 1200.00, "PLACED"},
                new Object[]{"CUST-001", 4500.00, "PLACED"},
                new Object[]{"CUST-003", 600.00,  "CANCELLED"},   // filtered by Pipeline 1
                new Object[]{"CUST-004", 2800.00, "PLACED"},
                new Object[]{"CUST-001", 950.00,  "PLACED"}
        );

        for (Object[] order : orders) {
            publishOrder(
                    (String) order[0],
                    (Double) order[1],
                    (String) order[2]
            );
        }

        log.info("Batch published | total: {} | PLACED: 7 | non-PLACED: 2",
                orders.size());
    }

}