package com.queuedockyard.kafkastreams.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a payment event published to Kafka.
 *
 * The key field for joining is orderId.
 * Both OrderEvent and PaymentEvent use orderId as the Kafka message KEY.
 * Kafka Streams joins on KEY — so messages with the same orderId
 * from both topics get joined together.
 *
 * Real world flow:
 *   Customer places order → OrderEvent published (key = orderId)
 *   Payment gateway confirms → PaymentEvent published (key = orderId)
 *   Join produces: EnrichedOrderEvent containing both
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEvent {

    /**
     * Must match the orderId in the corresponding OrderEvent.
     * This is the join key — Kafka Streams matches events
     * from both topics where the keys are equal.
     */
    private String orderId;

    /** Unique payment transaction identifier */
    private String paymentId;

    /** How the customer paid */
    private String paymentMethod;

    /**
     * Payment outcome.
     * Examples: SUCCESS, FAILED, PENDING, REFUNDED
     */
    private String paymentStatus;

    /** Amount charged — should match the order amount */
    private Double amount;

    /** When this payment event was created */
    private LocalDateTime createdAt;

}