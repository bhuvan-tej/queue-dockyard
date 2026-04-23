package com.queuedockyard.ecommerce.controller;

import com.queuedockyard.ecommerce.service.OrderEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Single REST endpoint — place an order.
 *
 * One POST triggers events across all three systems:
 *   Kafka    → Inventory + Analytics
 *   RabbitMQ → Email + SMS
 *   SQS      → Invoice
 *
 * Watch the logs after placing an order — you will see all
 * five consumers respond within seconds of each other.
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderEventPublisher publisher;

    /**
     * Places an order — triggers the full event pipeline.
     *
     * POST http://localhost:8088/api/orders
     * {
     *   "orderId": "ORD-001",
     *   "customerId": "CUST-001",
     *   "customerEmail": "customer@example.com",
     *   "customerPhone": "+91-9999999999",
     *   "amount": 2500.00,
     *   "items": "Laptop Stand, USB Hub, Mousepad"
     * }
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> placeOrder(
            @RequestBody OrderRequest request) {

        String messageId = publisher.publishOrderEvent(
                request.orderId(),
                request.customerId(),
                request.customerEmail(),
                request.customerPhone(),
                request.amount(),
                request.items()
        );

        return ResponseEntity.ok(Map.of(
                "status", "order placed",
                "orderId", request.orderId(),
                "messageId", messageId,
                "note", "Check logs — all 5 consumers will respond within seconds"
        ));
    }

    public record OrderRequest(
            String orderId,
            String customerId,
            String customerEmail,
            String customerPhone,
            Double amount,
            String items
    ) {}

}