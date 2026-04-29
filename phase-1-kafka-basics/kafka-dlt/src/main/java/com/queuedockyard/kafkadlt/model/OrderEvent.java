package com.queuedockyard.kafkadlt.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents an order event flowing through the DLT pipeline.
 *
 * The key field for this demo is shouldFail — when true,
 * the consumer throws an exception on every attempt,
 * triggering the full retry → DLT flow.
 *
 * In production shouldFail would not exist — failures happen
 * because of real issues: downstream service down, database
 * unavailable, invalid data, network timeout etc.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEvent {

    /** Unique idempotency key */
    private String messageId;

    /** Order identifier */
    private String orderId;

    /** Customer who placed the order */
    private String customerId;

    /** Order value */
    private Double amount;

    /** Current order status */
    private String status;

    /**
     * Demo control flag.
     * true  → consumer throws exception on every attempt
     *         triggers full retry → DLT flow
     * false → consumer processes successfully on first attempt
     */
    private boolean shouldFail;

    /** When this event was created */
    private LocalDateTime createdAt;

}