package com.queuedockyard.notificationconsumer.service;

import com.queuedockyard.notificationconsumer.model.OrderEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Simulates sending notifications when an order event is received.
 *
 * In a real system this would:
 *   - Call an email provider (SendGrid, SES)
 *   - Call an SMS provider (Twilio)
 *   - Push a mobile notification (Firebase)
 *
 * For learning purposes we just log the action and store received
 * events in memory so we can inspect them via the REST endpoint.
 *
 * Why store in memory?
 * It lets you hit GET /api/notifications after sending orders
 * and see exactly what the consumer received — without needing
 * a database setup at this stage.
 */
@Slf4j
@Service
public class NotificationService {

    /**
     * In-memory store of all received order events.
     * Collections.synchronizedList makes it thread-safe —
     * Kafka listener runs on a separate thread from the HTTP server.
     */
    private final List<OrderEvent> receivedEvents =
            Collections.synchronizedList(new ArrayList<>());

    /**
     * Processes an order event and sends appropriate notifications.
     *
     * Notice this method has no Kafka annotations — it is pure business logic.
     * The @KafkaListener lives in the consumer class and delegates here.
     * This separation makes the business logic easy to unit test
     * without needing a Kafka broker running.
     *
     * @param event the order event received from Kafka
     */
    public void processOrderEvent(OrderEvent event) {

        log.info("Processing order event | orderId: {} | status: {} | customer: {} | amount: {}",
                event.getOrderId(),
                event.getStatus(),
                event.getCustomerId(),
                event.getAmount());

        // simulate sending email notification
        sendEmailNotification(event);

        // simulate sending SMS notification
        sendSmsNotification(event);

        // store for inspection via REST endpoint
        receivedEvents.add(event);
    }

    /**
     * Simulates sending an email to the customer.
     * In production: call SendGrid / AWS SES API here.
     */
    private void sendEmailNotification(OrderEvent event) {
        log.info("EMAIL sent → customer: {} | subject: 'Order {} {}' | amount: ₹{}",
                event.getCustomerId(),
                event.getOrderId(),
                event.getStatus(),
                event.getAmount());
    }

    /**
     * Simulates sending an SMS to the customer.
     * In production: call Twilio / AWS SNS API here.
     */
    private void sendSmsNotification(OrderEvent event) {
        log.info("SMS sent → customer: {} | message: 'Your order {} has been {}'",
                event.getCustomerId(),
                event.getOrderId(),
                event.getStatus());
    }

    /**
     * Returns all events received so far.
     * Used by the REST controller to expose received events.
     */
    public List<OrderEvent> getReceivedEvents() {
        return Collections.unmodifiableList(receivedEvents);
    }

}