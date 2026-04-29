package com.queuedockyard.kafkadlt.controller;

import com.queuedockyard.kafkadlt.service.OrderPublisherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST endpoints to trigger the DLT pattern.
 *
 * POST /api/orders          → normal order — succeeds first attempt
 * POST /api/orders/failing  → poison message — retries then DLT
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderPublisherService publisherService;

    /**
     * Publishes a normal order.
     *
     * POST http://localhost:8091/api/orders
     * { "customerId": "CUST-001", "amount": 1500.00 }
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> publishOrder(
            @RequestBody OrderRequest request) {

        publisherService.publishOrder(request.customerId(), request.amount());

        return ResponseEntity.ok(Map.of(
                "status", "published",
                "note", "Watch logs — ORDER CONSUMER SUCCESS"
        ));
    }

    /**
     * Publishes a failing order — triggers full retry + DLT flow.
     * Total time to reach DLT: ~100 seconds (10 + 30 + 60)
     *
     * POST http://localhost:8091/api/orders/failing
     * { "customerId": "CUST-001", "amount": 999.00 }
     */
    @PostMapping("/failing")
    public ResponseEntity<Map<String, String>> publishFailingOrder(
            @RequestBody OrderRequest request) {

        publisherService.publishFailingOrder(request.customerId(), request.amount());

        return ResponseEntity.ok(Map.of(
                "status", "published",
                "note", "Watch logs — retries at 10s, 30s, 60s then DLT handler fires"
        ));
    }

    public record OrderRequest(String customerId, Double amount) {}

}