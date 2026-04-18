package com.queuedockyard.multipartitiondemo.controller;

import com.queuedockyard.multipartitiondemo.service.InventoryEventProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints to trigger inventory events.
 *
 * Two endpoints:
 *   POST /api/inventory/batch  → publishes 9 events across 3 products
 *   POST /api/inventory        → publishes a single custom event
 *
 * Use the batch endpoint to observe partition distribution —
 * send it once and watch where each message lands in the logs.
 */
@Slf4j
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryEventProducerService producerService;

    /**
     * Publishes 9 events across 3 fixed product IDs.
     * Watch the logs — PROD-001 always lands on the same partition,
     * PROD-002 always on another, PROD-003 always on the third.
     *
     * POST http://localhost:8083/api/inventory/batch
     */
    @PostMapping("/batch")
    public ResponseEntity<String> publishBatch() {
        producerService.publishBatchEvents();
        return ResponseEntity.ok("Published 9 inventory events — check logs to see partition distribution");
    }

    /**
     * Publishes a single inventory event with custom values.
     *
     * Example:
     * POST http://localhost:8083/api/inventory
     * {
     *   "productId": "PROD-001",
     *   "eventType": "RESTOCK",
     *   "quantity": 50
     * }
     */
    @PostMapping
    public ResponseEntity<String> publishSingle(@RequestBody InventoryRequest request) {
        producerService.publishSingleEvent(
                request.productId(),
                request.eventType(),
                request.quantity()
        );
        return ResponseEntity.ok("Published inventory event for productId: " + request.productId());
    }

    /** Request body for single event publishing. */
    public record InventoryRequest(String productId, String eventType, Integer quantity) {}

}