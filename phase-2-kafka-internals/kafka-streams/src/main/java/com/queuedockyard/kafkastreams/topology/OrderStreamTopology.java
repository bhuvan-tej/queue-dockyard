package com.queuedockyard.kafkastreams.topology;

import com.queuedockyard.kafkastreams.model.OrderEvent;
import com.queuedockyard.kafkastreams.model.RevenueWindow;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.kafka.support.serializer.JsonSerde;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Defines all three stream processing pipelines.
 *
 * This is where Kafka Streams topology is built.
 * Think of this as your data pipeline definition —
 * you describe WHAT to do with each event,
 * and Kafka Streams handles HOW to do it at scale.
 *
 * The @Autowired StreamsBuilder is injected by Spring
 * because of @EnableKafkaStreams in KafkaStreamsConfig.
 * You add operations to it and Spring builds the topology.
 *
 * All three pipelines read from the SAME input topic
 * but process independently — like three consumer groups
 * but with transformation capabilities built in.
 */
@Slf4j
@Component
public class OrderStreamTopology {

    private final StreamsBuilderFactoryBean streamsBuilderFactoryBean;

    @Value("${app.kafka.topics.orders-input}")
    private String ordersInputTopic;

    @Value("${app.kafka.topics.placed-orders}")
    private String placedOrdersTopic;

    @Value("${app.kafka.topics.customer-counts}")
    private String customerCountsTopic;

    @Value("${app.kafka.topics.revenue-per-minute}")
    private String revenuePerMinuteTopic;

    // state store name — used to query Pipeline 2 results via REST
    public static final String CUSTOMER_COUNTS_STORE = "customer-counts-store";

    public OrderStreamTopology(StreamsBuilderFactoryBean streamsBuilderFactoryBean) {
        this.streamsBuilderFactoryBean = streamsBuilderFactoryBean;
    }

    /**
     * Builds all three stream topologies.
     *
     * @Autowired on a method means Spring calls this method
     * after the bean is created, injecting StreamsBuilder.
     * We use this pattern to access StreamsBuilder
     * and define our topology in one place.
     */
    @Autowired
    public void buildTopology(StreamsBuilder builder) {

        // create a JsonSerde for OrderEvent — handles serialization
        // within the stream topology
        JsonSerde<OrderEvent> orderEventSerde = new JsonSerde<>(OrderEvent.class);
        orderEventSerde.configure(
                java.util.Map.of(
                        "spring.json.trusted.packages", "com.queuedockyard.*"
                ), false
        );

        // ── Source stream — reads from order-events topic ──────
        // This is the starting point for ALL three pipelines.
        // KStream<String, OrderEvent>
        //   String     → message key (orderId)
        //   OrderEvent → message value
        KStream<String, OrderEvent> orderStream = builder.stream(
                ordersInputTopic,
                Consumed.with(Serdes.String(), orderEventSerde)
        );

        // build all three pipelines from the same source stream
        buildFilterPipeline(orderStream, orderEventSerde);
        buildCustomerCountPipeline(orderStream, orderEventSerde);
        buildRevenueWindowPipeline(orderStream, orderEventSerde);

        log.info("Kafka Streams topology built | pipelines: 3 | input: {}",
                ordersInputTopic);
    }

    /**
     * Pipeline 1 — Order Filter
     *
     * Reads all order events → keeps only PLACED orders → writes to new topic.
     *
     * Before streams:
     *   Every consumer had to filter PLACED orders themselves.
     *   Duplicate filtering logic across multiple services.
     *
     * After streams:
     *   One pipeline filters once, downstream services consume
     *   the already-filtered placed-order-events topic.
     *
     * Topology:
     *   order-events → filter(PLACED) → placed-order-events
     */
    private void buildFilterPipeline(KStream<String, OrderEvent> orderStream,
                                     JsonSerde<OrderEvent> orderEventSerde) {

        orderStream
                // filter — only pass through PLACED orders
                // predicate: (key, value) → boolean
                .filter((orderId, event) -> {
                    boolean isPlaced = "PLACED".equals(event.getStatus());
                    if (!isPlaced) {
                        log.debug("FILTER PIPELINE | filtered out | orderId: {} | status: {}",
                                orderId, event.getStatus());
                    }
                    return isPlaced;
                })
                // peek — log without modifying the stream (useful for debugging)
                .peek((orderId, event) ->
                        log.info("FILTER PIPELINE | passed | orderId: {} | customer: {} | amount: {}",
                                orderId, event.getCustomerId(), event.getAmount())
                )
                // write filtered events to the output topic
                .to(placedOrdersTopic,
                        Produced.with(Serdes.String(), orderEventSerde));
    }

    /**
     * Pipeline 2 — Customer Order Counter
     *
     * Counts how many orders each customer has placed — continuously updated.
     *
     * This produces a KTable — not a stream of events but a
     * continuously updated table of the latest count per customer.
     *
     * KTable<customerId, count>:
     *   CUST-001 → 1   (after first order)
     *   CUST-001 → 2   (after second order)
     *   CUST-002 → 1   (after first order from new customer)
     *
     * The state is stored locally in RocksDB (Kafka Streams built-in store).
     * We name the store so we can query it via REST without consuming a topic.
     *
     * Topology:
     *   order-events → selectKey(customerId) → groupByKey → count → KTable
     */
    private void buildCustomerCountPipeline(KStream<String, OrderEvent> orderStream,
                                            JsonSerde<OrderEvent> orderEventSerde) {

        orderStream
                // rekey — change the message key from orderId to customerId
                // this is critical — groupByKey groups by KEY
                // without this, grouping would be by orderId (useless for counting per customer)
                .selectKey((orderId, event) -> event.getCustomerId())

                // peek to log the rekey operation
                .peek((customerId, event) ->
                        log.info("COUNTER PIPELINE | processing | customerId: {} | orderId: {}",
                                customerId, event.getOrderId())
                )

                // groupByKey — groups all messages with the same key (customerId)
                .groupByKey(Grouped.with(Serdes.String(), orderEventSerde))

                // count — for each customerId, count how many messages
                // Materialized.as → name the state store so we can query it
                .count(Materialized.as(CUSTOMER_COUNTS_STORE))

                // convert KTable back to a stream to write to output topic
                .toStream()

                // peek to log the count result
                .peek((customerId, count) ->
                        log.info("COUNTER PIPELINE | updated count | customerId: {} | totalOrders: {}",
                                customerId, count)
                )

                // write to output topic
                // key = customerId, value = count (Long)
                .to(customerCountsTopic,
                        Produced.with(Serdes.String(), Serdes.Long()));
    }

    /**
     * Pipeline 3 — Revenue per Minute (Windowed Aggregation)
     *
     * Sums total revenue within each 1-minute time window.
     * This is the most advanced pipeline — windowing is unique to stream processing.
     *
     * What is a time window?
     *   Instead of a running total, we aggregate within fixed time buckets.
     *   Window 1: 14:00:00 → 14:01:00 → total revenue = 5000
     *   Window 2: 14:01:00 → 14:02:00 → total revenue = 3000
     *   Window 3: 14:02:00 → 14:03:00 → total revenue = 8500
     *
     * This is exactly how dashboards show "revenue per minute" in prod.
     *
     * Topology:
     *   order-events
     *     → selectKey("global") — single group for all orders
     *     → groupByKey
     *     → windowedBy(1 minute tumbling window)
     *     → aggregate(sum amounts)
     *     → toStream
     *     → map to RevenueWindow
     *     → revenue-per-minute topic
     */
    private void buildRevenueWindowPipeline(KStream<String, OrderEvent> orderStream,
                                            JsonSerde<OrderEvent> orderEventSerde) {

        JsonSerde<RevenueWindow> revenueWindowSerde = new JsonSerde<>(RevenueWindow.class);
        revenueWindowSerde.configure(
                java.util.Map.of(
                        "spring.json.trusted.packages", "com.queuedockyard.*"
                ), false
        );

        orderStream
                // rekey to a constant — groups ALL orders into one group
                // so we aggregate revenue across ALL customers per window
                // in a real system you might key by region or category
                .selectKey((orderId, event) -> "global")

                // group all events under the "global" key
                .groupByKey(Grouped.with(Serdes.String(), orderEventSerde))

                // tumbling window — non-overlapping 1-minute buckets
                // TumblingWindows: each event belongs to exactly one window
                // HoppingWindows: events can belong to multiple overlapping windows
                .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(1)))

                // aggregate — for each window, sum all order amounts
                // initializer: starting value (0.0)
                // aggregator: (key, newEvent, currentTotal) → newTotal
                .aggregate(
                        () -> 0.0,
                        (key, event, currentTotal) -> {
                            double newTotal = currentTotal + event.getAmount();
                            log.info("REVENUE PIPELINE | window update | added: {} | running total: {}",
                                    event.getAmount(), newTotal);
                            return newTotal;
                        },
                        Materialized.with(Serdes.String(), Serdes.Double())
                )

                // convert windowed KTable to stream
                // Windowed<String> key contains window start/end timestamps
                .toStream()

                // map windowed result to our RevenueWindow model
                .map((windowedKey, totalRevenue) -> {
                    RevenueWindow window = RevenueWindow.builder()
                            .windowStart(windowedKey.window().start())
                            .windowEnd(windowedKey.window().end())
                            .totalRevenue(totalRevenue)
                            .orderCount(0L)   // count not tracked here — kept simple
                            .build();

                    log.info("REVENUE PIPELINE | window complete | start: {} | end: {} | revenue: {}",
                            windowedKey.window().startTime(),
                            windowedKey.window().endTime(),
                            totalRevenue);

                    // use window start as key for the output topic
                    return new org.apache.kafka.streams.KeyValue<>(
                            String.valueOf(windowedKey.window().start()),
                            window
                    );
                })

                // write to output topic
                .to(revenuePerMinuteTopic,
                        Produced.with(Serdes.String(), revenueWindowSerde));
    }

    /**
     * Queries the customer order count state store directly.
     * This is an interactive query — reads the local RocksDB store
     * without consuming a Kafka topic.
     *
     * Returns the total order count for a specific customer,
     * or 0 if the customer has no orders yet.
     *
     * @param customerId the customer to query
     * @return total orders placed by this customer
     */
    public Long getCustomerOrderCount(String customerId) {
        try {
            ReadOnlyKeyValueStore<String, Long> store =
                    streamsBuilderFactoryBean
                            .getKafkaStreams()
                            .store(
                                    org.apache.kafka.streams.StoreQueryParameters
                                            .fromNameAndType(
                                                    CUSTOMER_COUNTS_STORE,
                                                    QueryableStoreTypes.keyValueStore()
                                            )
                            );

            Long count = store.get(customerId);
            return count != null ? count : 0L;

        } catch (Exception e) {
            log.warn("State store not ready yet | customerId: {} | reason: {}",
                    customerId, e.getMessage());
            return 0L;
        }
    }

}