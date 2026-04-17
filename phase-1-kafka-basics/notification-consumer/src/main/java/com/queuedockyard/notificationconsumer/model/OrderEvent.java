package com.queuedockyard.notificationconsumer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Mirror of the OrderEvent in order-producer.
 *
 * In a real microservices setup, this class would live in a
 * shared library (a separate Maven module) that both the producer
 * and consumer depend on. That way there is a single source of truth
 * for the message contract and you can't accidentally break it.
 *
 * For learning purposes we duplicate it here so each app is
 * self-contained and easier to understand independently.
 *
 * Rule: if the producer changes this model, this file must be
 * updated to match — otherwise deserialization will fail.
 */
@Data
@Builder
@NoArgsConstructor      // required by JsonDeserializer to construct the object
@AllArgsConstructor     // required by @Builder
public class OrderEvent {

    /** Unique identifier for the order. Also the Kafka message key. */
    private String orderId;

    /** The customer who placed this order. */
    private String customerId;

    /** Total value of the order. */
    private Double amount;

    /**
     * Current status of the order.
     * Examples: PLACED, CONFIRMED, SHIPPED, DELIVERED, CANCELLED
     */
    private String status;

    /** Timestamp when the event was created — set by the producer. */
    private LocalDateTime createdAt;

}