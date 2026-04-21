package com.queuedockyard.localstack.controller;

import com.queuedockyard.localstack.model.OrderMessage;
import com.queuedockyard.localstack.service.SqsConsumerService;
import com.queuedockyard.localstack.service.SqsProducerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST endpoints to send and inspect SQS messages.
 *
 * POST /api/orders/standard  → send to Standard queue
 * POST /api/orders/fifo      → send to FIFO queue
 * GET  /api/orders/processed → inspect all processed messages
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final SqsProducerService producerService;
    private final SqsConsumerService consumerService;

    /**
     * Sends an order to the Standard SQS queue.
     *
     * POST http://localhost:8087/api/orders/standard
     * {
     *   "orderId": "ORD-001",
     *   "customerId": "CUST-001",
     *   "amount": 1500.00,
     *   "paymentMethod": "UPI"
     * }
     */
    @PostMapping("/standard")
    public ResponseEntity<Map<String, String>> sendToStandard(
            @RequestBody OrderRequest request) {

        String sqsMessageId = producerService.sendToStandardQueue(
                request.orderId(),
                request.customerId(),
                request.amount(),
                request.paymentMethod()
        );

        return ResponseEntity.ok(Map.of(
                "status", "sent to standard queue",
                "sqsMessageId", sqsMessageId,
                "note", "Consumer will pick this up within 2 seconds — check logs"
        ));
    }

    /**
     * Sends an order to the FIFO SQS queue.
     * Messages for the same customerId are processed in order.
     *
     * POST http://localhost:8087/api/orders/fifo
     * {
     *   "orderId": "ORD-002",
     *   "customerId": "CUST-001",
     *   "amount": 2500.00,
     *   "paymentMethod": "CREDIT_CARD"
     * }
     */
    @PostMapping("/fifo")
    public ResponseEntity<Map<String, String>> sendToFifo(
            @RequestBody OrderRequest request) {

        String sqsMessageId = producerService.sendToFifoQueue(
                request.orderId(),
                request.customerId(),
                request.amount(),
                request.paymentMethod()
        );

        return ResponseEntity.ok(Map.of(
                "status", "sent to FIFO queue",
                "sqsMessageId", sqsMessageId,
                "note", "Messages for same customerId processed in order — check logs"
        ));
    }

    /**
     * Returns all messages processed so far by the consumer.
     *
     * GET http://localhost:8087/api/orders/processed
     */
    @GetMapping("/processed")
    public ResponseEntity<List<OrderMessage>> getProcessed() {
        return ResponseEntity.ok(consumerService.getProcessedMessages());
    }

    public record OrderRequest(
            String orderId,
            String customerId,
            Double amount,
            String paymentMethod
    ) {}

}