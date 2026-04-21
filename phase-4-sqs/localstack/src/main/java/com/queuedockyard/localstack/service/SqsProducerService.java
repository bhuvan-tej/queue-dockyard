package com.queuedockyard.localstack.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.queuedockyard.localstack.model.OrderMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Publishes order messages to SQS queues.
 *
 * Key difference from Kafka and RabbitMQ:
 *   SQS messages are plain strings — no built-in object serialization.
 *   We manually convert OrderMessage → JSON string before sending.
 *   The consumer manually converts JSON string → OrderMessage after receiving.
 *
 * Also notice: we need to fetch the queue URL first using the queue name.
 * SQS uses URLs to identify queues, not just names.
 * URL format: http://localhost:4566/000000000000/queue-name
 */
@Slf4j
@Service
public class SqsProducerService {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.standard-queue-name}")
    private String standardQueueName;

    @Value("${aws.sqs.fifo-queue-name}")
    private String fifoQueueName;

    public SqsProducerService(SqsClient sqsClient) {
        this.sqsClient = sqsClient;
        // register JavaTimeModule so LocalDateTime serializes correctly
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
    }

    /**
     * Sends an order message to the Standard SQS queue.
     *
     * Standard queue — use when:
     *   - Order of processing does not matter
     *   - You need maximum throughput
     *   - Occasional duplicates are acceptable (handle with idempotency)
     *
     * @return the SQS message ID assigned by the broker
     */
    public String sendToStandardQueue(String orderId,
                                      String customerId,
                                      Double amount,
                                      String paymentMethod) {

        OrderMessage message = buildMessage(orderId, customerId, amount, paymentMethod);

        try {
            String messageBody = objectMapper.writeValueAsString(message);
            String queueUrl = getQueueUrl(standardQueueName);

            SendMessageRequest request = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageBody)
                    // optional: group related messages together
                    // not required for standard queues but good practice
                    .build();

            SendMessageResponse response = sqsClient.sendMessage(request);

            log.info("STANDARD QUEUE | sent | sqsMessageId: {} | orderId: {} | queueUrl: {}",
                    response.messageId(), orderId, queueUrl);

            return response.messageId();

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize message | orderId: {} | reason: {}", orderId, e.getMessage());
            throw new RuntimeException("Message serialization failed", e);
        }
    }

    /**
     * Sends an order message to the FIFO SQS queue.
     *
     * FIFO queue — use when:
     *   - Order of processing matters (e.g. order lifecycle: PLACED → CONFIRMED → SHIPPED)
     *   - Exactly-once processing is required
     *   - Throughput is secondary to correctness
     *
     * Two extra required fields for FIFO:
     *
     * MessageGroupId:
     *   Groups related messages together for ordering.
     *   Messages with the same group ID are processed in order.
     *   Use customerId or orderId as the group ID — all events
     *   for the same customer/order are then ordered correctly.
     *
     * MessageDeduplicationId:
     *   Prevents duplicate processing within a 5-minute window.
     *   We use message.getMessageId() (our UUID) for this.
     *   Since we enabled ContentBasedDeduplication on the queue,
     *   this is technically optional — but explicit is better.
     *
     * @return the SQS message ID assigned by the broker
     */
    public String sendToFifoQueue(String orderId,
                                  String customerId,
                                  Double amount,
                                  String paymentMethod) {

        OrderMessage message = buildMessage(orderId, customerId, amount, paymentMethod);

        try {
            String messageBody = objectMapper.writeValueAsString(message);
            String queueUrl = getQueueUrl(fifoQueueName);

            SendMessageRequest request = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageBody)
                    // all messages for the same customer processed in order
                    .messageGroupId(customerId)
                    // deduplication ID — prevents duplicate processing
                    .messageDeduplicationId(message.getMessageId())
                    .build();

            SendMessageResponse response = sqsClient.sendMessage(request);

            log.info("FIFO QUEUE | sent | sqsMessageId: {} | orderId: {} | groupId: {}",
                    response.messageId(), orderId, customerId);

            return response.messageId();

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize message | orderId: {} | reason: {}",
                    orderId, e.getMessage());
            throw new RuntimeException("Message serialization failed", e);
        }
    }

    /**
     * Fetches the queue URL for a given queue name.
     * SQS uses URLs to identify queues in all API calls.
     */
    private String getQueueUrl(String queueName) {
        return sqsClient.getQueueUrl(
                GetQueueUrlRequest.builder()
                        .queueName(queueName)
                        .build()
        ).queueUrl();
    }

    private OrderMessage buildMessage(String orderId,
                                      String customerId,
                                      Double amount,
                                      String paymentMethod) {
        return OrderMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .orderId(orderId)
                .customerId(customerId)
                .amount(amount)
                .paymentMethod(paymentMethod)
                .status("PLACED")
                .createdAt(LocalDateTime.now())
                .build();
    }

}