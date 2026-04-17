package com.queuedockyard.orderproducer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents an order event published to Kafka.
 *
 * This is the message contract between the producer and consumer.
 * Both sides must agree on this structure — if you change a field here,
 * you must update the consumer too.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEvent {

    /**
     * Unique identifier for this order.
     * Also used as the Kafka message key — this ensures all events
     * for the same order always go to the same partition (ordering guarantee).
     */
    private String orderId;

    /**
     * The customer who placed this order.
     */
    private String customerId;

    /**
     * Total value of the order in INR.
     */
    private Double amount;

    /**
     * Current status of the order.
     * Examples: PLACED, CONFIRMED, SHIPPED, DELIVERED, CANCELLED
     */
    private String status;

    /**
     * Timestamp when this event was created.
     * Always set this at the producer side — never trust the consumer's clock.
     */
    private LocalDateTime createdAt;

}