package com.queuedockyard.deadletter.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents an order message flowing through the queue.
 *
 * The key fields for the DLQ demo are:
 *   shouldFail  — lets us deliberately trigger failures via REST
 *   retryCount  — tracked by our consumer to enforce the retry limit
 *
 * In production you would not put shouldFail in a real message —
 * it exists here purely to make the demo easy to control.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderMessage {

    /** Unique identifier for this message. */
    private String messageId;

    /** The order this message is about. */
    private String orderId;

    /** Customer who placed the order. */
    private String customerId;

    /** Order value. */
    private Double amount;

    /**
     * Demo control flag — when true, the consumer will
     * simulate a processing failure for this message.
     * This lets you trigger the retry + DLQ flow on demand.
     */
    private boolean shouldFail;

    /**
     * How many times this message has been attempted.
     * Incremented by the consumer on each retry.
     * When this reaches max-retries, the message goes to DLQ.
     */
    private int retryCount;

    /** When this message was originally created. */
    private LocalDateTime createdAt;

}