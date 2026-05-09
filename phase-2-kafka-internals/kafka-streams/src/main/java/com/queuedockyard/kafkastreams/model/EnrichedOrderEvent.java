package com.queuedockyard.kafkastreams.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * The output of the join pipeline.
 *
 * Contains fields from BOTH OrderEvent and PaymentEvent.
 * Produced when a matching orderId is found in both streams
 * within the join time window.
 *
 * Why this matters in production:
 *   Without join: downstream service subscribes to two topics,
 *   manually correlates by orderId, manages its own state.
 *   With join: downstream service subscribes to one enriched topic,
 *   gets everything it needs in a single message.
 *
 * The join pipeline does the correlation once —
 * every downstream service benefits automatically.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrichedOrderEvent {

    // ── From OrderEvent ───────────────────────────────────────

    /** The shared join key */
    private String orderId;

    /** Customer who placed the order */
    private String customerId;

    /** Order value */
    private Double orderAmount;

    /** Items ordered */
    private String items;

    /** Order status at time of join */
    private String orderStatus;

    /** When the order was placed */
    private LocalDateTime orderCreatedAt;

    // ── From PaymentEvent ─────────────────────────────────────

    /**
     * Payment transaction ID.
     * Null if this was produced by a left join with no matching payment.
     */
    private String paymentId;

    /**
     * How the customer paid.
     * Null if no matching payment within the join window.
     */
    private String paymentMethod;

    /**
     * Payment outcome.
     * Null if no matching payment within the join window.
     */
    private String paymentStatus;

    /** When the payment was processed */
    private LocalDateTime paymentCreatedAt;

    // ── Join metadata ─────────────────────────────────────────

    /**
     * Which type of join produced this event.
     * INNER_JOIN → both order and payment matched
     * LEFT_JOIN  → order arrived, no matching payment in window
     *
     * Useful for downstream services to know if payment data
     * is present or if they need to handle the no-payment case.
     */
    private String joinType;

    /** When this enriched event was created by the join */
    private LocalDateTime joinedAt;

}