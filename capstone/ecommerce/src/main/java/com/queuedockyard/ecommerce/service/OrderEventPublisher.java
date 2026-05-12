package com.queuedockyard.ecommerce.service;

import com.queuedockyard.ecommerce.avro.OrderEvent;
import com.queuedockyard.ecommerce.avro.OrderStatus;
import com.queuedockyard.ecommerce.metrics.MetricsService;
import com.queuedockyard.ecommerce.store.RedisIdempotencyStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
 * Publishes order events to all three messaging systems.
 *
 * Kafka now uses Avro serialization via Schema Registry.
 * RabbitMQ and SQS still use JSON — they don't go through
 * Schema Registry since they are not Kafka-based.
 *
 * The OrderEvent used for Kafka is the Avro-generated class
 * from src/main/avro/OrderEvent.avsc — not a hand-written model.
 * The RabbitMQ and SQS models remain as hand-written Java classes
 * since they use Jackson JSON serialization.
 */
@Slf4j
@Service
public class OrderEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
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

    public OrderEventPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                               RabbitTemplate rabbitTemplate,
                               SqsClient sqsClient,
                               MetricsService metricsService,
                               RedisIdempotencyStore idempotencyStore) {
        this.kafkaTemplate = kafkaTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.sqsClient = sqsClient;
        this.metricsService = metricsService;
        this.idempotencyStore = idempotencyStore;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
    }

    public String publishOrderEvent(String orderId,
                                    String customerId,
                                    String customerEmail,
                                    String customerPhone,
                                    Double amount,
                                    String items) {

        String messageId = UUID.randomUUID().toString();

        if (idempotencyStore.isAlreadyProcessed(messageId)) {
            log.warn("Duplicate publish attempt | messageId: {}", messageId);
            metricsService.recordDuplicateDetected();
            return messageId;
        }

        // ── Kafka — Avro serialized ───────────────────────────
        // OrderEvent here is the Avro-generated class
        // Status is an Avro enum — compile-time type safety
        OrderEvent kafkaEvent = OrderEvent.newBuilder()
                .setMessageId(messageId)
                .setOrderId(orderId)
                .setCustomerId(customerId)
                .setCustomerEmail(customerEmail)
                .setCustomerPhone(customerPhone)
                .setAmount(amount)
                .setItems(items)
                .setStatus(OrderStatus.PLACED)   // enum — not a String
                .setCreatedAt(LocalDateTime.now().toString())
                .build();

        long start = System.nanoTime();
        publishToKafka(kafkaEvent);

        // ── RabbitMQ — JSON serialized ────────────────────────
        // RabbitMQ uses a separate model since it uses Jackson
        com.queuedockyard.ecommerce.model.OrderEvent rabbitEvent =
                com.queuedockyard.ecommerce.model.OrderEvent.builder()
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

        publishToRabbitMQ(rabbitEvent);
        publishToSqs(rabbitEvent);

        metricsService.recordPublishDuration(System.nanoTime() - start);
        idempotencyStore.markAsProcessed(messageId);
        metricsService.recordOrderPlaced();

        log.info("Order event published to all systems | messageId: {} | orderId: {}",
                messageId, orderId);

        return messageId;
    }

    private void publishToKafka(OrderEvent event) {
        kafkaTemplate.send(kafkaOrdersTopic, event.getOrderId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("KAFKA | publish failed | messageId: {} | reason: {}",
                                event.getMessageId(), ex.getMessage());
                        return;
                    }
                    log.info("KAFKA | Avro published | messageId: {} | partition: {} | offset: {} | schemaRegistry: http://localhost:8081/subjects/{}-value/versions",
                            event.getMessageId(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset(),
                            kafkaOrdersTopic);
                });
    }

    private void publishToRabbitMQ(
            com.queuedockyard.ecommerce.model.OrderEvent event) {
        try {
            rabbitTemplate.convertAndSend(rabbitExchange, "", event);
            log.info("RABBITMQ | published | messageId: {}", event.getMessageId());
        } catch (Exception e) {
            log.error("RABBITMQ | publish failed | messageId: {} | reason: {}",
                    event.getMessageId(), e.getMessage());
        }
    }

    private void publishToSqs(
            com.queuedockyard.ecommerce.model.OrderEvent event) {
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

            log.info("SQS | published | messageId: {}", event.getMessageId());

        } catch (JsonProcessingException e) {
            log.error("SQS | serialization failed | messageId: {} | reason: {}",
                    event.getMessageId(), e.getMessage());
        } catch (Exception e) {
            log.error("SQS | publish failed | messageId: {} | reason: {}",
                    event.getMessageId(), e.getMessage());
        }
    }

}