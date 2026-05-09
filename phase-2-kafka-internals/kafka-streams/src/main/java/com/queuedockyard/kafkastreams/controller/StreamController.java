package com.queuedockyard.kafkastreams.controller;

import com.queuedockyard.kafkastreams.service.OrderEventPublisher;
import com.queuedockyard.kafkastreams.topology.OrderStreamTopology;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST endpoints to trigger events and query stream results.
 *
 * POST /api/streams/order        → publish single order
 * POST /api/streams/batch        → publish mixed batch of 9 orders
 * GET  /api/streams/customer/{id} → query customer order count from state store
 */
@RestController
@RequestMapping("/api/streams")
@RequiredArgsConstructor
public class StreamController {

    private final OrderEventPublisher publisher;
    private final OrderStreamTopology topology;

    /**
     * Publishes a single order event.
     *
     * Try different statuses to observe Pipeline 1 filtering:
     *   status: PLACED    → passes through to placed-order-events
     *   status: CONFIRMED → filtered out, does not appear in output topic
     *   status: CANCELLED → filtered out
     *
     * POST http://localhost:8089/api/streams/order
     * {
     *   "customerId": "CUST-001",
     *   "amount": 1500.00,
     *   "status": "PLACED"
     * }
     */
    @PostMapping("/order")
    public ResponseEntity<Map<String, String>> publishOrder(@RequestBody OrderRequest request) {

        publisher.publishOrder(
                request.customerId(),
                request.amount(),
                request.status()
        );

        return ResponseEntity.ok(Map.of(
                "status", "published",
                "note", "Watch logs — all three pipelines process this event"
        ));
    }

    /**
     * Publishes a mixed batch of 9 orders across 4 customers.
     * Best way to observe all three pipelines simultaneously.
     *
     * After calling this:
     *   Pipeline 1 → 7 events in placed-order-events (2 filtered out)
     *   Pipeline 2 → CUST-001: 4 orders, CUST-002: 2, CUST-003: 1, CUST-004: 1
     *   Pipeline 3 → revenue window accumulating
     *
     * POST http://localhost:8089/api/streams/batch
     */
    @PostMapping("/batch")
    public ResponseEntity<Map<String, String>> publishBatch() {
        publisher.publishBatch();
        return ResponseEntity.ok(Map.of(
                "status", "batch published",
                "total", "9",
                "placed", "7",
                "filtered", "2",
                "note", "Check logs for all three pipeline responses"
        ));
    }

    /**
     * Queries the customer order count directly from the state store.
     *
     * This is an interactive query — reads local RocksDB state
     * without consuming a Kafka topic. Extremely fast.
     *
     * Try after publishing the batch:
     *   GET http://localhost:8089/api/streams/customer/CUST-001 → 4
     *   GET http://localhost:8089/api/streams/customer/CUST-002 → 2
     *   GET http://localhost:8089/api/streams/customer/CUST-999 → 0
     */
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<Map<String, Object>> getCustomerCount(@PathVariable String customerId) {

        Long count = topology.getCustomerOrderCount(customerId);

        return ResponseEntity.ok(Map.of(
                "customerId", customerId,
                "totalOrders", count,
                "source", "kafka-streams-state-store (RocksDB)"
        ));
    }

    public record OrderRequest(
            String customerId,
            Double amount,
            String status
    ) {}

    /**
     * Publishes a payment event for a specific order.
     * Use the orderId returned from POST /api/streams/order.
     *
     * POST http://localhost:8089/api/streams/payment
     * {
     *   "orderId": "ORD-XXXXXXXX",
     *   "paymentMethod": "UPI",
     *   "paymentStatus": "SUCCESS",
     *   "amount": 1500.00
     * }
     */
    @PostMapping("/payment")
    public ResponseEntity<Map<String, String>> publishPayment(@RequestBody PaymentRequest request) {

        publisher.publishPayment(
                request.orderId(),
                request.paymentMethod(),
                request.paymentStatus(),
                request.amount()
        );

        return ResponseEntity.ok(Map.of(
                "status", "payment published",
                "orderId", request.orderId(),
                "note", "If order was published within 30s — inner join will fire"
        ));
    }

    /**
     * Publishes a matched order + payment pair in quick succession.
     * Both use the same orderId — guaranteed to hit the join window.
     *
     * This is the easiest way to see the inner join fire —
     * no need to copy-paste orderId between two separate curl calls.
     *
     * POST http://localhost:8089/api/streams/join-demo
     * {
     *   "customerId": "CUST-001",
     *   "amount": 2500.00,
     *   "paymentMethod": "UPI"
     * }
     */
    @PostMapping("/join-demo")
    public ResponseEntity<Map<String, String>> joinDemo(
            @RequestBody JoinDemoRequest request) {

        // generate a shared orderId used by both events
        String orderId = "ORD-" + java.util.UUID.randomUUID()
                .toString().substring(0, 8).toUpperCase();

        // publish order first
        publisher.publishOrderWithId(
                orderId,
                request.customerId(),
                request.amount(),
                "PLACED"
        );

        // publish payment 1 second later — well within 30s window
        try { Thread.sleep(1000); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        publisher.publishPayment(
                orderId,
                request.paymentMethod(),
                "SUCCESS",
                request.amount()
        );

        return ResponseEntity.ok(Map.of(
                "status", "join demo triggered",
                "orderId", orderId,
                "note", "Both order and payment published with same orderId — watch logs for INNER JOIN match"
        ));
    }

    public record PaymentRequest(
            String orderId,
            String paymentMethod,
            String paymentStatus,
            Double amount
    ) {}

    public record JoinDemoRequest(
            String customerId,
            Double amount,
            String paymentMethod
    ) {}

}