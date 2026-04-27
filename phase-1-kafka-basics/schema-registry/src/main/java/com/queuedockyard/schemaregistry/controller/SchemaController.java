package com.queuedockyard.schemaregistry.controller;

import com.queuedockyard.schemaregistry.service.OrderProducerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * REST endpoints to publish events and inspect Schema Registry.
 *
 * POST /api/schema/v1          → publish V1 order (original schema)
 * POST /api/schema/v2          → publish V2 order (evolved schema)
 * GET  /api/schema/subjects    → list all registered schemas
 * GET  /api/schema/versions/{subject} → list versions of a schema
 */
@RestController
@RequestMapping("/api/schema")
@RequiredArgsConstructor
public class SchemaController {

    private final OrderProducerService producerService;
    private final RestTemplate restTemplate;

    /**
     * Publishes a V1 order event.
     * First call registers the schema with Schema Registry.
     *
     * POST http://localhost:8090/api/schema/v1
     * {
     *   "customerId": "CUST-001",
     *   "amount": 1500.00,
     *   "status": "PLACED"
     * }
     */
    @PostMapping("/v1")
    public ResponseEntity<Map<String, String>> publishV1(
            @RequestBody V1Request request) {

        producerService.publishV1Order(
                request.customerId(),
                request.amount(),
                request.status()
        );

        return ResponseEntity.ok(Map.of(
                "status", "V1 event published",
                "schema", "OrderEvent — original schema",
                "registeredAt", "http://localhost:8081/subjects/schema-demo-orders-v1-value/versions",
                "note", "Check Schema Registry — schema auto-registered on first publish"
        ));
    }

    /**
     * Publishes a V2 order event with the evolved schema.
     *
     * POST http://localhost:8090/api/schema/v2
     * {
     *   "customerId": "CUST-001",
     *   "amount": 2500.00,
     *   "status": "PLACED",
     *   "region": "SOUTH",
     *   "customerEmail": "customer@example.com"
     * }
     */
    @PostMapping("/v2")
    public ResponseEntity<Map<String, String>> publishV2(
            @RequestBody V2Request request) {

        producerService.publishV2Order(
                request.customerId(),
                request.amount(),
                request.status(),
                request.region(),
                request.customerEmail()
        );

        return ResponseEntity.ok(Map.of(
                "status", "V2 event published",
                "schema", "OrderEventV2 — evolved schema with region + customerEmail",
                "note", "V2 is backward compatible — V1 consumers can still read these messages"
        ));
    }

    /**
     * Lists all schemas registered in Schema Registry.
     * After publishing V1 and V2, you should see two subjects here.
     *
     * GET http://localhost:8090/api/schema/subjects
     */
    @GetMapping("/subjects")
    public ResponseEntity<Object> getSubjects() {
        Object subjects = restTemplate.getForObject(
                "http://localhost:8081/subjects",
                Object.class
        );
        return ResponseEntity.ok(subjects);
    }

    /**
     * Lists all versions of a specific schema subject.
     *
     * GET http://localhost:8090/api/schema/versions/schema-demo-orders-v1-value
     */
    @GetMapping("/versions/{subject}")
    public ResponseEntity<Object> getVersions(@PathVariable String subject) {
        Object versions = restTemplate.getForObject(
                "http://localhost:8081/subjects/" + subject + "/versions",
                Object.class
        );
        return ResponseEntity.ok(versions);
    }

    public record V1Request(String customerId, Double amount, String status) {}
    public record V2Request(String customerId, Double amount, String status,
                            String region, String customerEmail) {}

}