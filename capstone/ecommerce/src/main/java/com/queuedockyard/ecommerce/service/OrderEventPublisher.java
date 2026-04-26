package com.queuedockyard.ecommerce.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.queuedockyard.ecommerce.metrics.MetricsService;
import com.queuedockyard.ecommerce.model.OrderEvent;
import com.queuedockyard.ecommerce.store.RedisIdempotencyStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The heart of the capstone — publishes a single OrderEvent
 * to all three messaging systems simultaneously.
 *
 * Order of publishing:
 *   1. Kafka    → Inventory and Analytics consume independently
 *   2. RabbitMQ → Email and SMS receive via fanout exchange
 *   3. SQS      → Invoice generator picks up via polling
 *
 * All three use the same messageId (idempotency key) —
 * generated once here and stamped on every message.
 * Any downstream consumer that sees the same messageId
 * twice can safely skip processing.
 *
 * This class intentionally has no @Transactional —
 * true distributed transactions across three different
 * messaging systems are not practically achievable.
 * We rely on idempotency keys in each consumer instead.
 *
 * Redis idempotency check — prevents duplicate publishing
 * Metrics recording — tracks publish count and duration
 */
@Slf4j
@Service
public class OrderEventPublisher {

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;
    private final RedisIdempotencyStore idempotencyStore;

    @Value("${app.kafka.topics.orders}")
    private String kafkaOrdersTopic;

    @Value("${app.rabbitmq.exchange}")
    private String rabbitExchange;

    @Value("${aws.sqs.invoice-queue-name}")
    private String invoiceQueueName;

    public OrderEventPublisher(KafkaTemplate<String, OrderEvent> kafkaTemplate,
                               RabbitTemplate rabbitTemplate,
                               SqsClient sqsClient, MetricsService metricsService, RedisIdempotencyStore idempotencyStore) {
        this.kafkaTemplate = kafkaTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.sqsClient = sqsClient;
        this.metricsService = metricsService;
        this.idempotencyStore = idempotencyStore;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    /**
     * Publishes an order event to all three messaging systems.
     *
     * @param orderId       unique order identifier
     * @param customerId    customer who placed the order
     * @param customerEmail for email notification
     * @param customerPhone for SMS notification
     * @param amount        order value
     * @param items         comma-separated list of ordered items
     * @return the generated messageId (idempotency key)
     */
    public String publishOrderEvent(String orderId,
                                    String customerId,
                                    String customerEmail,
                                    String customerPhone,
                                    Double amount,
                                    String items) {

        // generate the idempotency key — used across all three systems
        String messageId = UUID.randomUUID().toString();

        if (idempotencyStore.isAlreadyProcessed(messageId)) {
            log.warn("Duplicate publish attempt detected | messageId: {}", messageId);
            metricsService.recordDuplicateDetected();
            return messageId;
        }

        OrderEvent event = OrderEvent.builder()
                .messageId(messageId)
                .orderId(orderId)
                .customerId(customerId)
                .customerEmail(customerEmail)
                .customerPhone(customerPhone)
                .amount(amount)
                .items(items)
                .status("PLACED")
                .createdAt(LocalDateTime.now())
                .build();

        log.info("Publishing order event | messageId: {} | orderId: {} | customerId: {}", messageId, orderId, customerId);

        // record publish duration across all three systems
        long start = System.nanoTime();

        // publish to all three — independent failures don't stop the others
        publishToKafka(event);
        publishToRabbitMQ(event);
        publishToSqs(event);

        metricsService.recordPublishDuration(System.nanoTime() - start);

        // mark as published — prevents duplicate publishing on retry
        idempotencyStore.markAsProcessed(messageId);

        // record business metric
        metricsService.recordOrderPlaced();

        log.info("Order event published to all systems | messageId: {} | orderId: {}", messageId, orderId);

        return messageId;
    }

    /**
     * Publishes to Kafka.
     * Inventory and Analytics services consume from this topic
     * via separate consumer groups — both get every event.
     */
    private void publishToKafka(OrderEvent event) {
        kafkaTemplate.send(kafkaOrdersTopic, event.getOrderId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("KAFKA | publish failed | messageId: {} | reason: {}",
                                event.getMessageId(), ex.getMessage());
                        return;
                    }
                    log.info("KAFKA | published | messageId: {} | partition: {} | offset: {}",
                            event.getMessageId(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                });
    }

    /**
     * Publishes to RabbitMQ fanout exchange.
     * Email and SMS queues both receive this event independently.
     * Empty routing key — fanout exchange ignores it.
     */
    private void publishToRabbitMQ(OrderEvent event) {
        try {
            rabbitTemplate.convertAndSend(rabbitExchange, "", event);
            log.info("RABBITMQ | published to fanout exchange | messageId: {}",
                    event.getMessageId());
        } catch (Exception e) {
            log.error("RABBITMQ | publish failed | messageId: {} | reason: {}",
                    event.getMessageId(), e.getMessage());
        }
    }

    /**
     * Publishes to SQS invoice queue.
     * SQS messages are plain strings — manually serialize to JSON.
     * Invoice generator polls this queue and processes invoices.
     */
    private void publishToSqs(OrderEvent event) {
        try {
            String messageBody = objectMapper.writeValueAsString(event);
            String queueUrl = sqsClient.getQueueUrl(
                    GetQueueUrlRequest.builder()
                            .queueName(invoiceQueueName)
                            .build()
            ).queueUrl();

            sqsClient.sendMessage(
                    SendMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .messageBody(messageBody)
                            .build()
            );

            log.info("SQS | published to invoice queue | messageId: {}",
                    event.getMessageId());

        } catch (JsonProcessingException e) {
            log.error("SQS | serialization failed | messageId: {} | reason: {}",
                    event.getMessageId(), e.getMessage());
        } catch (Exception e) {
            log.error("SQS | publish failed | messageId: {} | reason: {}",
                    event.getMessageId(), e.getMessage());
        }
    }

}