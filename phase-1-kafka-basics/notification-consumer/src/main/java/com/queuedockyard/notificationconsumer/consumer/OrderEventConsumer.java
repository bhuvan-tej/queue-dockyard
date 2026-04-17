package com.queuedockyard.notificationconsumer.consumer;

import com.queuedockyard.notificationconsumer.model.OrderEvent;
import com.queuedockyard.notificationconsumer.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer — listens to the order-events topic and
 * delegates processing to NotificationService.
 *
 * Responsibility of this class is ONLY:
 *   1. Listen to Kafka
 *   2. Extract the message
 *   3. Hand it off to the service layer
 *
 * No business logic lives here — that belongs in NotificationService.
 * This keeps the Kafka wiring separate from the business logic,
 * making both easier to test and change independently.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {


    private final NotificationService notificationService;

    /**
     * Listens to the order-events topic.
     *
     * @KafkaListener breakdown:
     *   topics        → which topic to listen to (matches producer's topic)
     *   groupId       → consumer group (matches application.yml group-id)
     *
     * ConsumerRecord gives us access to both the message AND its metadata:
     *   record.value()     → the actual OrderEvent object
     *   record.key()       → the orderId (message key)
     *   record.partition() → which partition this came from
     *   record.offset()    → position in the partition
     *
     * Why ConsumerRecord instead of just OrderEvent directly?
     * The metadata (partition, offset) is extremely useful while learning —
     * you can see exactly which partition each message landed in.
     * In production you'd often use just the value for cleaner code.
     *
     * @param record the Kafka record containing the event and its metadata
     */
    @KafkaListener(
            topics = "${app.kafka.topic.orders}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onOrderEvent(ConsumerRecord<String, OrderEvent> record) {

        log.info("Received order event | partition: {} | offset: {} | key: {}",
                record.partition(),
                record.offset(),
                record.key());

        // delegate to service — keep this method thin
        notificationService.processOrderEvent(record.value());
    }

}