package com.queuedockyard.ecommerce.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Custom business metrics for the ecommerce pipeline.
 *
 * Micrometer auto-instruments many things (JVM memory, HTTP requests,
 * Kafka consumer lag etc.) — but business metrics like
 * "how many orders were processed" need to be recorded manually.
 *
 * These metrics appear in Prometheus at /actuator/prometheus
 * and can be visualized in Grafana dashboards.
 *
 * Metric naming convention: use dots as separators.
 * Prometheus converts dots to underscores automatically.
 * So "orders.placed" becomes "orders_placed_total" in Prometheus.
 */
@Slf4j
@Service
public class MetricsService {

    // ── Counters — track how many times something happened ─────

    /** Total orders placed via REST endpoint */
    private final Counter ordersPlacedCounter;

    /** Total orders processed by Inventory consumer */
    private final Counter inventoryProcessedCounter;

    /** Total orders processed by Analytics consumer */
    private final Counter analyticsProcessedCounter;

    /** Total email notifications sent */
    private final Counter emailsSentCounter;

    /** Total SMS notifications sent */
    private final Counter smsSentCounter;

    /** Total invoices generated */
    private final Counter invoicesGeneratedCounter;

    /** Total duplicate messages detected across all consumers */
    private final Counter duplicatesDetectedCounter;

    // ── Timers — track how long something took ─────────────────

    /** Time taken to publish an event to all three systems */
    private final Timer orderPublishTimer;

    public MetricsService(MeterRegistry registry) {

        // counters — increment with .increment()
        this.ordersPlacedCounter = Counter.builder("orders.placed")
                .description("Total number of orders placed")
                .register(registry);

        this.inventoryProcessedCounter = Counter.builder("inventory.processed")
                .description("Total orders processed by inventory service")
                .register(registry);

        this.analyticsProcessedCounter = Counter.builder("analytics.processed")
                .description("Total orders processed by analytics service")
                .register(registry);

        this.emailsSentCounter = Counter.builder("notifications.email.sent")
                .description("Total email notifications sent")
                .register(registry);

        this.smsSentCounter = Counter.builder("notifications.sms.sent")
                .description("Total SMS notifications sent")
                .register(registry);

        this.invoicesGeneratedCounter = Counter.builder("invoices.generated")
                .description("Total invoices generated")
                .register(registry);

        this.duplicatesDetectedCounter = Counter.builder("messages.duplicates.detected")
                .description("Total duplicate messages detected and skipped")
                .register(registry);

        // timers — record with .record()
        this.orderPublishTimer = Timer.builder("order.publish.duration")
                .description("Time taken to publish order event to all three systems")
                .register(registry);
    }

    // ── Public methods called by consumers and publisher ───────

    public void recordOrderPlaced() {
        ordersPlacedCounter.increment();
    }

    public void recordInventoryProcessed() {
        inventoryProcessedCounter.increment();
    }

    public void recordAnalyticsProcessed() {
        analyticsProcessedCounter.increment();
    }

    public void recordEmailSent() {
        emailsSentCounter.increment();
    }

    public void recordSmsSent() {
        smsSentCounter.increment();
    }

    public void recordInvoiceGenerated() {
        invoicesGeneratedCounter.increment();
    }

    public void recordDuplicateDetected() {
        duplicatesDetectedCounter.increment();
    }

    /**
     * Records how long publishing to all three systems took.
     * Call this wrapping the entire publish operation.
     *
     * Usage:
     *   long start = System.nanoTime();
     *   // ... do publishing ...
     *   metricsService.recordPublishDuration(System.nanoTime() - start);
     */
    public void recordPublishDuration(long durationNanos) {
        orderPublishTimer.record(durationNanos, TimeUnit.NANOSECONDS);
    }

}