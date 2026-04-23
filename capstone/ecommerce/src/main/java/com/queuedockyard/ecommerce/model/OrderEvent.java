package com.queuedockyard.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * The core event model for the entire capstone.
 *
 * A single OrderEvent is created when a customer places an order.
 * The SAME event is then published to all three systems:
 *   Kafka    → for Inventory and Analytics
 *   RabbitMQ → for Email and SMS notifications
 *   SQS      → for Invoice generation
 *
 * Why the same event across all three?
 * In a real system, each downstream service only needs the data
 * it cares about. But using a single model here keeps the capstone
 * focused on the messaging patterns rather than data modelling.
 *
 * The messageId is the global idempotency key —
 * generated once at order placement and stamped on every
 * message sent to every system. Any consumer that sees the
 * same messageId twice can safely skip processing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEvent {

    /**
     * Global idempotency key.
     * Generated once at order placement using UUID.
     * Same messageId across Kafka, RabbitMQ, and SQS messages.
     */
    private String messageId;

    /** Unique order identifier. */
    private String orderId;

    /** Customer who placed the order. */
    private String customerId;

    /** Customer's email address — used by Email Service. */
    private String customerEmail;

    /** Customer's phone number — used by SMS Service. */
    private String customerPhone;

    /** Total order value. */
    private Double amount;

    /** Items ordered — comma separated for simplicity. */
    private String items;

    /** Current status of the order. */
    private String status;

    /** When this order was placed. */
    private LocalDateTime createdAt;

}