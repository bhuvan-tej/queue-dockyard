package com.queuedockyard.exactlyoncedemo.consumer;

import com.queuedockyard.exactlyoncedemo.model.PaymentEvent;
import com.queuedockyard.exactlyoncedemo.store.ProcessedMessageStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * An idempotent consumer that safely handles duplicate messages.
 *
 * This is the CORRECT approach for at-least-once delivery systems.
 *
 * Processing flow:
 *   1. Receive message from Kafka
 *   2. Check if messageId already exists in ProcessedMessageStore
 *   3. YES → skip processing, still commit offset (message handled)
 *   4. NO  → process the payment, record messageId, commit offset
 *
 * Result: no matter how many times Kafka delivers the same message,
 * the customer is charged exactly once.
 *
 * Uses group-id: idempotent-group
 * Completely independent from NonIdempotentConsumer —
 * both receive all messages from the same topic.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotentConsumer {

    private final ProcessedMessageStore processedMessageStore;

    /**
     * Processes payment events with full duplicate protection.
     *
     * The Acknowledgment parameter is injected by Spring Kafka
     * because we set ack-mode: MANUAL_IMMEDIATE in application.yml.
     * We must call acknowledgment.acknowledge() ourselves —
     * Kafka will not commit the offset automatically.
     */
    @KafkaListener(
            topics = "${app.kafka.topic.payments}",
            groupId = "idempotent-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentEvent(ConsumerRecord<String, PaymentEvent> record,
                               Acknowledgment acknowledgment) {

        PaymentEvent event = record.value();

        log.info("IDEMPOTENT | received | messageId: {} | orderId: {} | partition: {} | offset: {}",
                event.getMessageId(),
                event.getOrderId(),
                record.partition(),
                record.offset());

        // ── Step 1: duplicate check ────────────────────────────
        if (processedMessageStore.isAlreadyProcessed(event.getMessageId())) {

            // this message was already processed successfully before
            // skip processing but still commit offset — we've handled it
            log.warn("IDEMPOTENT | DUPLICATE DETECTED — skipping | messageId: {}",
                    event.getMessageId());

            // still acknowledge — so Kafka doesn't redeliver it again
            acknowledgment.acknowledge();
            return;
        }

        // ── Step 2: process the payment ────────────────────────
        processPayment(event);

        // ── Step 3: mark as processed AFTER successful processing
        // order matters here — if we crash between step 2 and step 3,
        // the message will be redelivered and processed again.
        // this is acceptable (better than losing the payment).
        // in production, steps 2 and 3 would be wrapped in a DB transaction.
        processedMessageStore.markAsProcessed(event.getMessageId());

        // ── Step 4: commit offset — message fully handled ──────
        acknowledgment.acknowledge();

        log.info("IDEMPOTENT | processed and committed | messageId: {}",
                event.getMessageId());
    }

    /**
     * Simulates processing the actual payment.
     * In production: call payment gateway, update DB, etc.
     */
    private void processPayment(PaymentEvent event) {
        log.info("IDEMPOTENT | CHARGING CUSTOMER: {} | amount: {} | method: {} | messageId: {}",
                event.getCustomerId(),
                event.getAmount(),
                event.getPaymentMethod(),
                event.getMessageId());
    }

}