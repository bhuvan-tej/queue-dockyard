package com.queuedockyard.jobqueue.controller;

import com.queuedockyard.jobqueue.service.JobPublisherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST endpoints to trigger messages through each exchange type.
 *
 * Use these to observe the routing behavior:
 *
 *   POST /api/jobs/direct          → one queue receives it
 *   POST /api/jobs/fanout          → all three queues receive it
 *   POST /api/jobs/topic           → routed by pattern match
 */
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobPublisherService publisherService;

    /**
     * Publishes to DIRECT exchange.
     * Only the queue bound to "order.process" receives it.
     *
     * POST http://localhost:8085/api/jobs/direct
     * { "payload": "Process order ORD-001" }
     */
    @PostMapping("/direct")
    public ResponseEntity<Map<String, String>> publishDirect(
            @RequestBody JobRequest request) {

        publisherService.publishDirectMessage(request.payload());
        return ResponseEntity.ok(Map.of(
                "exchange", "DIRECT",
                "note", "Only direct.orders.queue receives this — watch DirectOrderConsumer logs"
        ));
    }

    /**
     * Publishes to FANOUT exchange.
     * ALL bound queues receive it — email, sms, and push.
     *
     * POST http://localhost:8085/api/jobs/fanout
     * { "payload": "User registered: user@example.com" }
     */
    @PostMapping("/fanout")
    public ResponseEntity<Map<String, String>> publishFanout(
            @RequestBody JobRequest request) {

        publisherService.publishFanoutMessage(request.payload());
        return ResponseEntity.ok(Map.of(
                "exchange", "FANOUT",
                "note", "All 3 queues receive this — watch FanoutConsumer logs for all three"
        ));
    }

    /**
     * Publishes to TOPIC exchange with a custom routing key.
     * The routing key determines which queue receives it.
     *
     * Try these routing keys:
     *   "order.placed"    → TopicConsumer.onOrderEvent receives it
     *   "order.shipped"   → TopicConsumer.onOrderEvent receives it
     *   "payment.success" → TopicConsumer.onPaymentEvent receives it
     *   "inventory.low"   → NOBODY receives it (no matching pattern)
     *
     * POST http://localhost:8085/api/jobs/topic
     * { "routingKey": "order.placed", "payload": "New order placed" }
     */
    @PostMapping("/topic")
    public ResponseEntity<Map<String, String>> publishTopic(
            @RequestBody TopicJobRequest request) {

        publisherService.publishTopicMessage(request.routingKey(), request.payload());
        return ResponseEntity.ok(Map.of(
                "exchange", "TOPIC",
                "routingKey", request.routingKey(),
                "note", "Check which consumer received this based on the routing key pattern"
        ));
    }

    /** Request body for direct and fanout messages. */
    public record JobRequest(String payload) {}

    /** Request body for topic messages — includes routing key. */
    public record TopicJobRequest(String routingKey, String payload) {}

}