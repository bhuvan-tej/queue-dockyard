package com.queuedockyard.exactlyoncedemo.service;

import com.queuedockyard.exactlyoncedemo.model.PaymentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Publishes payment events to Kafka.
 *
 * Key responsibility: stamp every message with a unique messageId.
 * This messageId is the idempotency key — it travels with the message
 * and allows consumers to detect and skip duplicates.
 *
 * The messageId is generated here at publish time using UUID.
 * It must never change on retry — if you retry a failed send,
 * use the SAME messageId so the consumer recognizes it as a duplicate.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentProducerService {

    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    @Value("${app.kafka.topic.payments}")
    private String paymentsTopic;

    /**
     * Publishes a payment event with a unique messageId.
     *
     * @param orderId       the order being paid for
     * @param customerId    the customer making the payment
     * @param amount        payment amount
     * @param paymentMethod how the customer is paying
     */
    public String publishPaymentEvent(String orderId,
                                      String customerId,
                                      Double amount,
                                      String paymentMethod) {

        // generate the idempotency key — must be unique per logical event
        String messageId = UUID.randomUUID().toString();

        PaymentEvent event = PaymentEvent.builder()
                .messageId(messageId)       // idempotency key
                .orderId(orderId)
                .customerId(customerId)
                .amount(amount)
                .paymentMethod(paymentMethod)
                .status("INITIATED")
                .createdAt(LocalDateTime.now())
                .build();

        kafkaTemplate.send(paymentsTopic, orderId, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish payment | messageId: {} | reason: {}",
                                messageId, ex.getMessage());
                        return;
                    }
                    log.info("Payment event published | messageId: {} | orderId: {} | partition: {} | offset: {}",
                            messageId,
                            orderId,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                });

        return messageId;
    }

    /**
     * Simulates duplicate delivery — publishes the SAME event twice
     * with the SAME messageId.
     *
     * Use this to demonstrate:
     *   - NonIdempotentConsumer processes it twice (bug)
     *   - IdempotentConsumer processes it once and skips the duplicate (correct)
     *
     * @param orderId       the order being paid for
     * @param customerId    the customer making the payment
     * @param amount        payment amount
     * @param paymentMethod how the customer is paying
     */
    public String publishDuplicatePaymentEvent(String orderId,
                                               String customerId,
                                               Double amount,
                                               String paymentMethod) {

        // same messageId for both sends — simulates at-least-once redelivery
        String messageId = UUID.randomUUID().toString();

        PaymentEvent event = PaymentEvent.builder()
                .messageId(messageId)
                .orderId(orderId)
                .customerId(customerId)
                .amount(amount)
                .paymentMethod(paymentMethod)
                .status("INITIATED")
                .createdAt(LocalDateTime.now())
                .build();

        // first delivery
        kafkaTemplate.send(paymentsTopic, orderId, event);
        log.info("Duplicate demo — FIRST delivery | messageId: {} | orderId: {}",
                messageId, orderId);

        // second delivery — same messageId, simulating Kafka redelivery after crash
        kafkaTemplate.send(paymentsTopic, orderId, event);
        log.info("Duplicate demo — SECOND delivery (duplicate) | messageId: {} | orderId: {}",
                messageId, orderId);

        return messageId;
    }

}