package com.queuedockyard.orderproducer.controller;

import com.queuedockyard.orderproducer.model.OrderEvent;
import com.queuedockyard.orderproducer.service.OrderProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * REST endpoint to trigger order events.
 *
 * In a real system, this would be called when a customer places an order.
 * Here we use it to manually trigger events while learning —
 * so you can fire a request and immediately watch it appear in Kafka UI.
 */
@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderProducerService orderProducerService;

    /**
     * Place a new order — publishes an order event to Kafka.
     *
     * Example request:
     * POST http://localhost:8081/api/orders
     * {
     *   "customerId": "CUST-001",
     *   "amount": 1500.00
     * }
     *
     * The orderId and createdAt are generated here — never trust the client to provide these.
     */
    @PostMapping
    public ResponseEntity<String> placeOrder(@RequestBody OrderRequest request) {

        // build the event — orderId generated here, not by the client
        OrderEvent event = OrderEvent.builder()
                .orderId(UUID.randomUUID().toString())   // guaranteed unique
                .customerId(request.customerId())
                .amount(request.amount())
                .status("PLACED")
                .createdAt(LocalDateTime.now())
                .build();

        orderProducerService.publishOrderEvent(event);

        log.info("Order placed | orderId: {} | customerId: {}",
                event.getOrderId(), event.getCustomerId());

        return ResponseEntity.ok("Order placed successfully | orderId: " + event.getOrderId());
    }

    /**
     * Simple record to represent the incoming HTTP request body.
     * Using a Java record here because it's immutable and concise —
     * we only need customerId and amount from the client.
     */
    public record OrderRequest(String customerId, Double amount) {}

}