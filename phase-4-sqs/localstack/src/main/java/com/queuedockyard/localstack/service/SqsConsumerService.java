package com.queuedockyard.localstack.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.queuedockyard.localstack.model.OrderMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Polls SQS queues and processes incoming messages.
 *
 * Key difference from Kafka (@KafkaListener) and RabbitMQ (@RabbitListener):
 *   SQS has no push-based listener mechanism in the AWS SDK.
 *   You must POLL — repeatedly ask SQS "any new messages?".
 *
 * This is called the consumer polling loop pattern.
 * Spring Cloud AWS provides an @SqsListener annotation that hides
 * this complexity, but we implement it manually here so you
 * understand exactly what happens under the hood.
 *
 * Three-step SQS consume pattern:
 *   1. ReceiveMessage  — fetch messages (they become invisible)
 *   2. Process         — do your work
 *   3. DeleteMessage   — explicitly delete after successful processing
 *      (if you crash before deleting, visibility timeout expires
 *       and the message becomes visible again — at-least-once delivery)
 */
@Slf4j
@Service
public class SqsConsumerService {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.standard-queue-name}")
    private String standardQueueName;

    @Value("${aws.sqs.fifo-queue-name}")
    private String fifoQueueName;

    @Value("${aws.sqs.wait-time-seconds}")
    private int waitTimeSeconds;

    /** Controls the polling loop — set to false on shutdown */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** Background thread pool for the polling loops */
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    /** In-memory store of processed messages for REST inspection */
    private final List<OrderMessage> processedMessages = Collections.synchronizedList(new ArrayList<>());

    public SqsConsumerService(SqsClient sqsClient) {
        this.sqsClient = sqsClient;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
    }

    /**
     * Starts the polling loops after Spring finishes initialization.
     * Two separate pollers — one for each queue.
     */
    @PostConstruct
    public void startPolling() {
        running.set(true);

        // poll standard queue every 2 seconds
        scheduler.scheduleWithFixedDelay(
                () -> pollQueue(standardQueueName, "STANDARD"),
                3, 2, TimeUnit.SECONDS
        );

        // poll FIFO queue every 2 seconds
        scheduler.scheduleWithFixedDelay(
                () -> pollQueue(fifoQueueName, "FIFO"),
                3, 2, TimeUnit.SECONDS
        );

        log.info("SQS polling started | standard: {} | fifo: {}",
                standardQueueName, fifoQueueName);
    }

    /**
     * Gracefully stops polling on application shutdown.
     * Waits up to 5 seconds for in-flight polls to complete.
     */
    @PreDestroy
    public void stopPolling() {
        running.set(false);
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("SQS polling stopped");
    }

    /**
     * The polling loop — runs repeatedly in the background.
     *
     * Long polling (WaitTimeSeconds > 0):
     *   SQS waits up to N seconds for a message before returning empty.
     *   Much more efficient than short polling (WaitTimeSeconds=0) which
     *   returns immediately even if the queue is empty — wasting API calls.
     */
    private void pollQueue(String queueName, String queueType) {

        if (!running.get()) return;

        try {
            String queueUrl = getQueueUrl(queueName);

            ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    // fetch up to 10 messages per poll (SQS maximum)
                    .maxNumberOfMessages(10)
                    // long polling — wait up to N seconds for messages
                    .waitTimeSeconds(waitTimeSeconds)
                    .build();

            List<Message> messages = sqsClient.receiveMessage(request).messages();

            if (!messages.isEmpty()) {
                log.info("{} QUEUE | polled {} message(s)", queueType, messages.size());
            }

            for (Message sqsMessage : messages) {
                processMessage(sqsMessage, queueUrl, queueType);
            }

        } catch (Exception e) {
            // log but don't crash the polling loop
            if (running.get()) {
                log.error("{} QUEUE | polling error | reason: {}", queueType, e.getMessage());
            }
        }
    }

    /**
     * Processes a single SQS message.
     *
     * The three-step pattern:
     *   1. Deserialize the JSON body back to OrderMessage
     *   2. Process the order
     *   3. Delete the message from SQS — CRITICAL step
     *
     * If step 3 is skipped:
     *   The visibility timeout expires (30 seconds in our config).
     *   The message becomes visible again in the queue.
     *   Another consumer (or this one) will receive and process it again.
     *   This is SQS's at-least-once delivery guarantee in action.
     */
    private void processMessage(Message sqsMessage,
                                String queueUrl,
                                String queueType) {
        try {
            // step 1 — deserialize
            OrderMessage orderMessage = objectMapper.readValue(
                    sqsMessage.body(), OrderMessage.class
            );

            log.info("{} QUEUE | received | sqsId: {} | orderId: {} | customerId: {} | amount: {}",
                    queueType,
                    sqsMessage.messageId(),
                    orderMessage.getOrderId(),
                    orderMessage.getCustomerId(),
                    orderMessage.getAmount());

            // step 2 — process
            processOrder(orderMessage, queueType);

            // step 3 — delete (MUST happen after successful processing)
            // the receiptHandle is SQS's token for this specific delivery
            // you must use this exact handle to delete — not the messageId
            deleteMessage(queueUrl, sqsMessage.receiptHandle(), queueType);

            // store for REST inspection
            processedMessages.add(orderMessage);

        } catch (Exception e) {
            // do NOT delete on failure — let visibility timeout expire
            // message will be redelivered automatically
            log.error("{} QUEUE | processing failed | sqsId: {} | reason: {}",
                    queueType, sqsMessage.messageId(), e.getMessage());
        }
    }

    /**
     * Deletes a message from SQS after successful processing.
     *
     * Uses receiptHandle — NOT messageId.
     * receiptHandle is unique per delivery — the same message
     * redelivered after visibility timeout has a different receiptHandle.
     */
    private void deleteMessage(String queueUrl,
                               String receiptHandle,
                               String queueType) {

        sqsClient.deleteMessage(
                DeleteMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(receiptHandle)
                        .build()
        );

        log.info("{} QUEUE | message deleted successfully", queueType);
    }

    private void processOrder(OrderMessage message, String queueType) {
        log.info("{} QUEUE | processing order | orderId: {} | amount: {} | method: {}",
                queueType,
                message.getOrderId(),
                message.getAmount(),
                message.getPaymentMethod());
    }

    private String getQueueUrl(String queueName) {
        return sqsClient.getQueueUrl(
                GetQueueUrlRequest.builder()
                        .queueName(queueName)
                        .build()
        ).queueUrl();
    }

    public List<OrderMessage> getProcessedMessages() {
        return Collections.unmodifiableList(processedMessages);
    }

}