package com.queuedockyard.kafkastreams.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Input event model — mirrors the OrderEvent from other phases.
 *
 * This is the message that flows through all three stream pipelines.
 * The streams read this from the order-events topic and transform it
 * into different output formats depending on the pipeline.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEvent {

    /** Idempotency key — unique per event */
    private String messageId;

    /** Order identifier */
    private String orderId;

    /** Customer who placed the order */
    private String customerId;

    /** Order value */
    private Double amount;

    /** Items in the order */
    private String items;

    /**
     * Current status of the order.
     * Pipeline 1 filters on this — only PLACED orders pass through.
     * Examples: PLACED, CONFIRMED, SHIPPED, DELIVERED, CANCELLED
     */
    private String status;

    /** When this event was created */
    private LocalDateTime createdAt;

}