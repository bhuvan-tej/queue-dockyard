package com.queuedockyard.exactlyoncedemo.controller;

import com.queuedockyard.exactlyoncedemo.service.PaymentProducerService;
import com.queuedockyard.exactlyoncedemo.store.ProcessedMessageStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

/**
 * REST endpoints to trigger payment events and inspect consumer state.
 *
 * Three endpoints:
 *   POST /api/payments          → publishes a single payment event
 *   POST /api/payments/duplicate → publishes the SAME event twice (simulates redelivery)
 *   GET  /api/payments/processed → shows which messageIds have been processed
 *
 * The duplicate endpoint is the key understanding tool here —
 * watch the logs from both consumers when you call it.
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentProducerService producerService;
    private final ProcessedMessageStore processedMessageStore;

    /**
     * Publishes a single payment event.
     *
     * POST http://localhost:8084/api/payments
     * {
     *   "orderId": "ORD-001",
     *   "customerId": "CUST-001",
     *   "amount": 1500.00,
     *   "paymentMethod": "UPI"
     * }
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> publishPayment(
            @RequestBody PaymentRequest request) {

        String messageId = producerService.publishPaymentEvent(
                request.orderId(),
                request.customerId(),
                request.amount(),
                request.paymentMethod()
        );

        return ResponseEntity.ok(Map.of(
                "status", "published",
                "messageId", messageId,
                "note", "Check logs — both consumers will process this once"
        ));
    }

    /**
     * Publishes the SAME payment event twice with the same messageId.
     * This simulates what happens when Kafka redelivers a message
     * after a consumer crash.
     *
     * Watch the logs:
     *   NonIdempotentConsumer → logs "CHARGING CUSTOMER" TWICE (bug)
     *   IdempotentConsumer    → logs "CHARGING CUSTOMER" ONCE, then "DUPLICATE DETECTED"
     *
     * POST http://localhost:8084/api/payments/duplicate
     * {
     *   "orderId": "ORD-002",
     *   "customerId": "CUST-002",
     *   "amount": 2500.00,
     *   "paymentMethod": "CREDIT_CARD"
     * }
     */
    @PostMapping("/duplicate")
    public ResponseEntity<Map<String, String>> publishDuplicate(
            @RequestBody PaymentRequest request) {

        String messageId = producerService.publishDuplicatePaymentEvent(
                request.orderId(),
                request.customerId(),
                request.amount(),
                request.paymentMethod()
        );

        return ResponseEntity.ok(Map.of(
                "status", "published duplicate",
                "messageId", messageId,
                "note", "Same messageId sent twice — watch logs to see the difference"
        ));
    }

    /**
     * Shows all messageIds processed by the IdempotentConsumer.
     * Use this to confirm the idempotent consumer only processed each message once.
     *
     * GET http://localhost:8084/api/payments/processed
     */
    @GetMapping("/processed")
    public ResponseEntity<Map<String, Object>> getProcessedMessages() {
        Set<String> processedIds = processedMessageStore.getAllProcessedIds();
        return ResponseEntity.ok(Map.of(
                "totalProcessed", processedMessageStore.getProcessedCount(),
                "processedMessageIds", processedIds
        ));
    }

    /** Request body for payment events. */
    public record PaymentRequest(
            String orderId,
            String customerId,
            Double amount,
            String paymentMethod
    ) {}

}