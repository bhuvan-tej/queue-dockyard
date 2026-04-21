package com.queuedockyard.localstack.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents an order message sent through SQS.
 *
 * SQS messages are plain strings — there is no built-in
 * serialization like Kafka's JsonSerializer or RabbitMQ's
 * Jackson2JsonMessageConverter.
 *
 * We serialize this object to JSON manually using ObjectMapper
 * before sending, and deserialize it manually after receiving.
 * This is a key difference from Kafka and RabbitMQ.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderMessage {

    /**
     * Unique identifier — used as the idempotency key.
     * Also used as MessageDeduplicationId for FIFO queues —
     * SQS FIFO rejects duplicate messages with the same ID
     * within a 5-minute deduplication window.
     */
    private String messageId;

    /** The order being processed. */
    private String orderId;

    /** Customer who placed the order. */
    private String customerId;

    /** Order value. */
    private Double amount;

    /**
     * Payment method used.
     * Examples: UPI, CREDIT_CARD, NET_BANKING
     */
    private String paymentMethod;

    /** Current status of the order. */
    private String status;

    /** When this message was created. */
    private LocalDateTime createdAt;

}