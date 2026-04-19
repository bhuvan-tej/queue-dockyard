package com.queuedockyard.jobqueue.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a generic job message sent through RabbitMQ.
 *
 * This single model is used across all three exchange type demos
 * to keep the focus on the routing behavior rather than the payload.
 *
 * In a real system each queue/exchange would have its own
 * purpose-specific message model.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobMessage {

    /** Unique identifier for this message. */
    private String messageId;

    /**
     * The routing key used to publish this message.
     * Examples: order.process, order.placed, payment.success
     * Useful for consumers to know how this message was routed.
     */
    private String routingKey;

    /** The business payload — what this job is about. */
    private String payload;

    /**
     * Which exchange type sent this message.
     * Examples: DIRECT, FANOUT, TOPIC
     * Makes logs easier to read in the demo.
     */
    private String exchangeType;

    /** When this message was created. */
    private LocalDateTime createdAt;

}