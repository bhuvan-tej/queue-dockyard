package com.queuedockyard.exactlyoncedemo.consumer;

import com.queuedockyard.exactlyoncedemo.model.PaymentEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * A naive consumer that processes every message it receives.
 *
 * This consumer has NO duplicate detection.
 * If Kafka redelivers the same message (at-least-once delivery),
 * this consumer will process it again — causing double charging.
 *
 * This is the WRONG approach — shown here deliberately so you
 * can compare its behavior with IdempotentConsumer side by side.
 *
 * Uses group-id: non-idempotent-group
 * This means it receives ALL messages independently from IdempotentConsumer.
 * Both consumers read the same topic but are in different groups.
 */
@Slf4j
@Component
public class NonIdempotentConsumer {

    /**
     * Processes every payment event without any duplicate check.
     *
     * Watch what happens when you call POST /api/payments/duplicate —
     * this consumer logs "CHARGING CUSTOMER" twice for the same messageId.
     * That is a real production bug.
     */
    @KafkaListener(
            topics = "${app.kafka.topic.payments}",
            groupId = "non-idempotent-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentEvent(ConsumerRecord<String, PaymentEvent> record,
                               Acknowledgment acknowledgment) {

        PaymentEvent event = record.value();

        // no duplicate check — blindly processes every message
        log.warn("NON-IDEMPOTENT | processing payment | messageId: {} | orderId: {} | amount: {}",
                event.getMessageId(),
                event.getOrderId(),
                event.getAmount());

        // simulate charging the customer
        log.warn("NON-IDEMPOTENT | CHARGING CUSTOMER: {} | amount: {} | messageId: {}",
                event.getCustomerId(),
                event.getAmount(),
                event.getMessageId());

        // commit the offset — message won't be redelivered by Kafka
        // but if this same messageId arrives again, we'll charge again
        acknowledgment.acknowledge();
    }

}