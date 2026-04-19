package com.queuedockyard.deadletter.controller;

import com.queuedockyard.deadletter.consumer.DlqConsumer;
import com.queuedockyard.deadletter.model.OrderMessage;
import com.queuedockyard.deadletter.service.OrderPublisherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST endpoints to trigger and inspect the DLQ pattern.
 *
 * POST /api/orders          → normal order — succeeds first attempt
 * POST /api/orders/failing  → poison message — retries then goes to DLQ
 * GET  /api/orders/dlq      → inspect messages currently in the DLQ store
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderPublisherService publisherService;
    private final DlqConsumer dlqConsumer;

    /**
     * Publishes a normal order — processes successfully.
     *
     * POST http://localhost:8086/api/orders
     * { "orderId": "ORD-001", "customerId": "CUST-001", "amount": 1500.00 }
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> publishOrder(
            @RequestBody OrderRequest request) {

        publisherService.publishOrder(
                request.orderId(),
                request.customerId(),
                request.amount()
        );

        return ResponseEntity.ok(Map.of(
                "status", "published",
                "note", "Watch logs — OrderConsumer processes this successfully"
        ));
    }

    /**
     * Publishes a failing order — retries 3 times then goes to DLQ.
     *
     * Watch the logs carefully after calling this:
     *   1. ORDER CONSUMER | attempt 1 of 3 | FAILED | will retry
     *   2. ORDER CONSUMER | attempt 2 of 3 | FAILED | will retry
     *   3. ORDER CONSUMER | attempt 3 of 3 | MAX RETRIES EXCEEDED | sending to DLQ
     *   4. DLQ CONSUMER   | message arrived in DLQ | ALERT
     *
     * POST http://localhost:8086/api/orders/failing
     * { "orderId": "ORD-BAD-001", "customerId": "CUST-001", "amount": 999.00 }
     */
    @PostMapping("/failing")
    public ResponseEntity<Map<String, String>> publishFailingOrder(
            @RequestBody OrderRequest request) {

        publisherService.publishFailingOrder(
                request.orderId(),
                request.customerId(),
                request.amount()
        );

        return ResponseEntity.ok(Map.of(
                "status", "published",
                "note", "Watch logs — will retry 3 times then route to DLQ"
        ));
    }

    /**
     * Returns all messages currently in the DLQ store.
     * Call this after publishing a failing order to confirm it arrived in DLQ.
     *
     * GET http://localhost:8086/api/orders/dlq
     */
    @GetMapping("/dlq")
    public ResponseEntity<List<OrderMessage>> getDeadLetters() {
        return ResponseEntity.ok(dlqConsumer.getDeadLetters());
    }

    public record OrderRequest(String orderId, String customerId, Double amount) {}

}