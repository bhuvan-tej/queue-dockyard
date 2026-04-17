package com.queuedockyard.notificationconsumer.controller;

import com.queuedockyard.notificationconsumer.model.OrderEvent;
import com.queuedockyard.notificationconsumer.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoint to inspect what the consumer has received.
 *
 * This exists purely for learning purposes — it lets you:
 *   1. Send orders via order-producer (POST /api/orders)
 *   2. Then call this endpoint to confirm the consumer received them
 *
 * In a real system the consumer wouldn't expose a REST API —
 * it would just process events silently in the background.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Returns all order events received by this consumer so far.
     *
     * Example:
     * GET http://localhost:8082/api/notifications
     */
    @GetMapping
    public ResponseEntity<List<OrderEvent>> getReceivedNotifications() {
        return ResponseEntity.ok(notificationService.getReceivedEvents());
    }

}