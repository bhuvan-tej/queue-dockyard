package com.queuedockyard.kafkastreams.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Output model for Pipeline 3 — Revenue per Minute.
 *
 * Represents the total revenue accumulated within a
 * specific 1-minute time window.
 *
 * Example:
 *   windowStart: "14:00:00"
 *   windowEnd:   "14:01:00"
 *   totalRevenue: 7500.00
 *   orderCount:   3
 *
 * This tells you: between 2pm and 2:01pm,
 * 3 orders worth ₹7500 were placed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueWindow {

    /** Start of the 1-minute window (epoch milliseconds) */
    private long windowStart;

    /** End of the 1-minute window (epoch milliseconds) */
    private long windowEnd;

    /** Total revenue within this window */
    private Double totalRevenue;

    /** Number of orders within this window */
    private long orderCount;

}