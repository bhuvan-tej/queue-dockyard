package com.queuedockyard.multipartitiondemo.service;

import com.queuedockyard.multipartitiondemo.model.InventoryEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Publishes inventory events to Kafka.
 *
 * The key design here is using productId as the message key.
 * Kafka hashes the key to determine the partition.
 * Same productId → same hash → same partition every time.
 *
 * This means:
 *   - All events for PROD-001 always go to partition 0 (for example)
 *   - All events for PROD-002 always go to partition 1
 *   - Ordering is preserved per product across the system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryEventProducerService {

    private final KafkaTemplate<String, InventoryEvent> kafkaTemplate;

    @Value("${app.kafka.topic.inventory}")
    private String inventoryTopic;

    /**
     * Publishes a batch of inventory events spread explicitly across
     * all 3 partitions.
     *
     * Why explicit partitions instead of key-based routing?
     * Key hashing is deterministic but not predictable by humans —
     * "PROD-001" might hash to partition 0 on one setup and partition 2
     * on another depending on the number of partitions and hash algorithm.
     *
     * For this demo we send directly to partition 0, 1, and 2 explicitly
     * so you can clearly observe messages arriving on different partitions.
     *
     * In production you would always use key-based routing — never hardcode
     * partition numbers. Explicit partition targeting is only for demos/testing.
     */
    public void publishBatchEvents() {

        // fixed product IDs — watch these always land on the same partition
        List<String> productIds = List.of(
                "PROD-001", "PROD-002", "PROD-003",
                "PROD-001", "PROD-002", "PROD-003",
                "PROD-001", "PROD-002", "PROD-003"
        );

        List<String> eventTypes = List.of(
                "RESTOCK", "SALE", "RETURN",
                "SALE", "RESTOCK", "ADJUSTMENT",
                "RETURN", "SALE", "RESTOCK"
        );

        // explicitly map each product to a fixed partition for clear observation
        Map<String, Integer> productPartitionMap = Map.of(
                "PROD-001", 0,
                "PROD-002", 1,
                "PROD-003", 2
        );

        for (int i = 0; i < productIds.size(); i++) {

            String productId = productIds.get(i);
            int targetPartition = productPartitionMap.get(productId);

            InventoryEvent event = InventoryEvent.builder()
                    .productId(productId)
                    .productName("Product " + productId)
                    .quantity(10 + i)
                    .eventType(eventTypes.get(i))
                    .warehouseId("WH-" + (i % 3 + 1))
                    .createdAt(LocalDateTime.now())
                    .build();

            // send to explicit partition — new ProducerRecord allows partition targeting
            // ProducerRecord(topic, partition, key, value)
            kafkaTemplate.send(new org.apache.kafka.clients.producer.ProducerRecord<>(
                    inventoryTopic,
                    targetPartition,   // explicit partition number
                    productId,         // key
                    event              // value
            ));

            log.info("Published → productId: {} | targetPartition: {} | eventType: {}",
                    productId, targetPartition, event.getEventType());
        }

        log.info("Batch complete — published 9 events across 3 product IDs");
    }

    /**
     * Publishes a single inventory event.
     * Used by the REST endpoint for manual testing.
     */
    public void publishSingleEvent(String productId, String eventType, Integer quantity) {

        InventoryEvent event = InventoryEvent.builder()
                .productId(productId)
                .productName("Product " + productId)
                .quantity(quantity)
                .eventType(eventType)
                .warehouseId("WH-1")
                .createdAt(LocalDateTime.now())
                .build();

        kafkaTemplate.send(inventoryTopic, productId, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish | productId: {} | reason: {}",
                                productId, ex.getMessage());
                        return;
                    }
                    log.info("Published → productId: {} | partition: {} | offset: {}",
                            productId,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                });
    }

}