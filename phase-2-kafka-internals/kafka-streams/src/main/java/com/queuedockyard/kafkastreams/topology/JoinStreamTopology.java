package com.queuedockyard.kafkastreams.topology;

import com.queuedockyard.kafkastreams.model.EnrichedOrderEvent;
import com.queuedockyard.kafkastreams.model.OrderEvent;
import com.queuedockyard.kafkastreams.model.PaymentEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.support.serializer.JsonSerde;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Defines two join pipelines:
 *
 * Pipeline 1 — Inner Join
 *   order-events + payment-events → enriched-order-events
 *   Only produces output when BOTH sides match within 30 seconds.
 *   Use when: both order and payment are required for downstream processing.
 *
 * Pipeline 2 — Left Join
 *   order-events + payment-events → enriched-order-events (left)
 *   Always produces output when an order arrives.
 *   Payment fields are null if no matching payment within 30 seconds.
 *   Use when: order must always be processed, payment is supplemental.
 *
 * Key concept — why we selectKey before joining:
 *   Kafka Streams joins on the MESSAGE KEY.
 *   Both streams must be keyed by the same field — orderId.
 *   If the key is already orderId, no rekey needed.
 *   If not, use selectKey() to change the key first.
 *
 * Key concept — co-partitioning requirement:
 *   Both input topics must have the SAME number of partitions.
 *   Kafka Streams joins partition 0 of topic A with partition 0 of topic B.
 *   If partition counts differ, the join will miss matches.
 *   Both order-events and payment-events are created with 3 partitions.
 */
@Slf4j
@Component
public class JoinStreamTopology {

    @Value("${app.kafka.topics.orders-input}")
    private String ordersInputTopic;

    @Value("${app.kafka.topics.payment-events}")
    private String paymentEventsTopic;

    @Value("${app.kafka.topics.enriched-orders}")
    private String enrichedOrdersTopic;

    @Value("${app.kafka.topics.enriched-orders-left}")
    private String enrichedOrdersLeftTopic;

    /**
     * Builds both join topologies.
     * Called by Spring after the bean is created —
     * same pattern as OrderStreamTopology.
     */
    @Autowired
    public void buildJoinTopology(StreamsBuilder builder) {

        Map<String, Object> serdeConfig = Map.of(
                "spring.json.trusted.packages", "com.queuedockyard.*"
        );

        JsonSerde<OrderEvent> orderEventSerde = new JsonSerde<>(OrderEvent.class);
        orderEventSerde.configure(serdeConfig, false);

        JsonSerde<PaymentEvent> paymentEventSerde = new JsonSerde<>(PaymentEvent.class);
        paymentEventSerde.configure(serdeConfig, false);

        JsonSerde<EnrichedOrderEvent> enrichedOrderSerde =
                new JsonSerde<>(EnrichedOrderEvent.class);
        enrichedOrderSerde.configure(serdeConfig, false);

        KStream<String, OrderEvent> orderStream = builder.stream(
                ordersInputTopic,
                Consumed.with(Serdes.String(), orderEventSerde)
        );

        KStream<String, PaymentEvent> paymentStream = builder.stream(
                paymentEventsTopic,
                Consumed.with(Serdes.String(), paymentEventSerde)
        );

        // pass serdes explicitly to each pipeline method
        buildInnerJoin(orderStream, paymentStream,
                orderEventSerde, paymentEventSerde,
                enrichedOrderSerde, enrichedOrdersTopic);

        buildLeftJoin(orderStream, paymentStream,
                orderEventSerde, paymentEventSerde,
                enrichedOrderSerde, enrichedOrdersLeftTopic);

        log.info("Join topologies built | inner → {} | left → {}",
                enrichedOrdersTopic, enrichedOrdersLeftTopic);
    }

    /**
     * Pipeline 1 — Inner Join
     *
     * Produces an enriched event ONLY when both order AND payment
     * arrive with the same orderId within the join window (30 seconds).
     *
     * Timeline:
     *   t=0s:  Order ORD-001 arrives → Kafka Streams opens a 30s window
     *   t=15s: Payment for ORD-001 arrives → MATCH → enriched event produced
     *   t=31s: Payment for ORD-002 arrives (order was at t=0s) → window closed → NO match
     *
     * The JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofSeconds(30)) means:
     *   Either event can arrive first
     *   The other must arrive within 30 seconds
     *   No grace period — late arrivals beyond 30s are dropped
     */
    private void buildInnerJoin(KStream<String, OrderEvent> orderStream,
                                KStream<String, PaymentEvent> paymentStream,
                                JsonSerde<OrderEvent> orderEventSerde,
                                JsonSerde<PaymentEvent> paymentEventSerde,
                                JsonSerde<EnrichedOrderEvent> enrichedOrderSerde,
                                String outputTopic) {

        orderStream
                .join(
                        paymentStream,

                        // ValueJoiner — how to combine the two events
                        // called when a matching pair is found
                        (orderEvent, paymentEvent) -> {
                            log.info("INNER JOIN | match found | orderId: {} | paymentStatus: {}",
                                    orderEvent.getOrderId(),
                                    paymentEvent.getPaymentStatus());

                            return EnrichedOrderEvent.builder()
                                    // from order
                                    .orderId(orderEvent.getOrderId())
                                    .customerId(orderEvent.getCustomerId())
                                    .orderAmount(orderEvent.getAmount())
                                    .items(orderEvent.getItems())
                                    .orderStatus(orderEvent.getStatus())
                                    .orderCreatedAt(orderEvent.getCreatedAt())
                                    // from payment
                                    .paymentId(paymentEvent.getPaymentId())
                                    .paymentMethod(paymentEvent.getPaymentMethod())
                                    .paymentStatus(paymentEvent.getPaymentStatus())
                                    .paymentCreatedAt(paymentEvent.getCreatedAt())
                                    // metadata
                                    .joinType("INNER_JOIN")
                                    .joinedAt(LocalDateTime.now())
                                    .build();
                        },

                        // join window — both events must arrive within 30 seconds
                        JoinWindows.ofTimeDifferenceWithNoGrace(
                                Duration.ofSeconds(30)
                        ),

                        // serdes for the join
                        StreamJoined.with(
                                Serdes.String(),
                                orderEventSerde,
                                paymentEventSerde
                        )
                )
                .peek((orderId, enriched) ->
                        log.info("INNER JOIN | enriched event produced | orderId: {} | paymentMethod: {} | paymentStatus: {}",
                                orderId,
                                enriched.getPaymentMethod(),
                                enriched.getPaymentStatus())
                )
                .to(outputTopic,
                        Produced.with(Serdes.String(), enrichedOrderSerde));
    }

    /**
     * Pipeline 2 — Left Join
     *
     * Always produces output when an ORDER arrives.
     * Payment fields are null if no matching payment within the window.
     *
     * Use case:
     *   Every order must trigger downstream processing.
     *   Payment data is enrichment — nice to have but not blocking.
     *   Order without payment = payment pending, process order anyway.
     *
     * The joinType field tells downstream services whether
     * payment data is present so they can handle both cases:
     *   INNER_JOIN → payment data available
     *   LEFT_JOIN  → no payment match, payment fields are null
     */
    private void buildLeftJoin(KStream<String, OrderEvent> orderStream,
                               KStream<String, PaymentEvent> paymentStream,
                               JsonSerde<OrderEvent> orderEventSerde,
                               JsonSerde<PaymentEvent> paymentEventSerde,
                               JsonSerde<EnrichedOrderEvent> enrichedOrderSerde,
                               String outputTopic) {

        orderStream
                .leftJoin(
                        paymentStream,

                        // ValueJoiner — paymentEvent is null if no match
                        (orderEvent, paymentEvent) -> {

                            boolean hasPayment = paymentEvent != null;

                            if (hasPayment) {
                                log.info("LEFT JOIN | order with payment | orderId: {}",
                                        orderEvent.getOrderId());
                            } else {
                                log.warn("LEFT JOIN | order without payment | orderId: {} | payment pending or failed",
                                        orderEvent.getOrderId());
                            }

                            return EnrichedOrderEvent.builder()
                                    // from order — always present
                                    .orderId(orderEvent.getOrderId())
                                    .customerId(orderEvent.getCustomerId())
                                    .orderAmount(orderEvent.getAmount())
                                    .items(orderEvent.getItems())
                                    .orderStatus(orderEvent.getStatus())
                                    .orderCreatedAt(orderEvent.getCreatedAt())
                                    // from payment — null if no match
                                    .paymentId(hasPayment
                                            ? paymentEvent.getPaymentId() : null)
                                    .paymentMethod(hasPayment
                                            ? paymentEvent.getPaymentMethod() : null)
                                    .paymentStatus(hasPayment
                                            ? paymentEvent.getPaymentStatus() : "PENDING")
                                    .paymentCreatedAt(hasPayment
                                            ? paymentEvent.getCreatedAt() : null)
                                    // metadata
                                    .joinType(hasPayment ? "INNER_JOIN" : "LEFT_JOIN")
                                    .joinedAt(LocalDateTime.now())
                                    .build();
                        },

                        JoinWindows.ofTimeDifferenceWithNoGrace(
                                Duration.ofSeconds(30)
                        ),

                        StreamJoined.with(
                                Serdes.String(),
                                orderEventSerde,
                                paymentEventSerde
                        )
                )
                .peek((orderId, enriched) ->
                        log.info("LEFT JOIN | enriched event produced | orderId: {} | joinType: {} | paymentStatus: {}",
                                orderId,
                                enriched.getJoinType(),
                                enriched.getPaymentStatus())
                )
                .to(outputTopic,
                        Produced.with(Serdes.String(), enrichedOrderSerde));
    }

}