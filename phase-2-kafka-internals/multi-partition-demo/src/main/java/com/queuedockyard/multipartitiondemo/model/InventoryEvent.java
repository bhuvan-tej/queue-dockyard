package com.queuedockyard.multipartitiondemo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents an inventory update event.
 *
 * We use a different event type from Phase 1 (OrderEvent) deliberately —
 * each topic should have its own event model representing its domain.
 * Mixing event types on the same topic is an anti-pattern.
 *
 * productId is used as the Kafka message KEY.
 * This guarantees all events for the same product always land
 * on the same partition — preserving ordering per product.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryEvent {

    /**
     * Unique identifier for the product.
     * Used as the Kafka message key — same productId → same partition.
     */
    private String productId;

    /** Human-readable product name. */
    private String productName;

    /** How many units are being added or removed. */
    private Integer quantity;

    /**
     * Type of inventory action.
     * Examples: RESTOCK, SALE, ADJUSTMENT, RETURN
     */
    private String eventType;

    /** Which warehouse this event originated from. */
    private String warehouseId;

    /** When this event was created — always set at the producer side. */
    private LocalDateTime createdAt;

}