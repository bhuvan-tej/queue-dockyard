package com.queuedockyard.ecommerce.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.queuedockyard.ecommerce.model.OrderEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Invoice Service — polls SQS invoice queue and generates invoices.
 *
 * Uses the same polling loop pattern from Phase 4:
 *   1. Poll SQS for messages
 *   2. Deserialize JSON → OrderEvent
 *   3. Generate invoice (simulated)
 *   4. Delete message from SQS — critical step
 *
 * In production this would be an AWS Lambda triggered by SQS —
 * serverless, auto-scaling, zero infra management.
 * Here we simulate that with a polling loop against LocalStack.
 */
@Slf4j
@Component
public class InvoiceConsumer {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Value("${aws.sqs.invoice-queue-name}")
    private String invoiceQueueName;

    @Value("${aws.sqs.wait-time-seconds}")
    private int waitTimeSeconds;

    public InvoiceConsumer(SqsClient sqsClient) {
        this.sqsClient = sqsClient;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
    }

    @PostConstruct
    public void startPolling() {
        running.set(true);
        scheduler.scheduleWithFixedDelay(this::pollQueue, 5, 3, TimeUnit.SECONDS);
        log.info("INVOICE | SQS polling started | queue: {}", invoiceQueueName);
    }

    @PreDestroy
    public void stopPolling() {
        running.set(false);
        scheduler.shutdown();
        log.info("INVOICE | SQS polling stopped");
    }

    private void pollQueue() {
        if (!running.get()) return;

        try {
            String queueUrl = getQueueUrl();

            List<Message> messages = sqsClient.receiveMessage(
                    ReceiveMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .maxNumberOfMessages(10)
                            .waitTimeSeconds(waitTimeSeconds)
                            .build()
            ).messages();

            for (Message sqsMessage : messages) {
                processMessage(sqsMessage, queueUrl);
            }

        } catch (Exception e) {
            if (running.get()) {
                log.error("INVOICE | polling error | reason: {}", e.getMessage());
            }
        }
    }

    private void processMessage(Message sqsMessage, String queueUrl) {
        try {
            OrderEvent event = objectMapper.readValue(
                    sqsMessage.body(), OrderEvent.class
            );

            log.info("INVOICE | received | messageId: {} | orderId: {} | amount: {}",
                    event.getMessageId(),
                    event.getOrderId(),
                    event.getAmount());

            // simulate generating invoice
            log.info("INVOICE | generating | invoiceNo: INV-{} | customer: {} | amount: ₹{}",
                    event.getOrderId(),
                    event.getCustomerId(),
                    event.getAmount());

            // delete after successful processing — critical step
            sqsClient.deleteMessage(
                    DeleteMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .receiptHandle(sqsMessage.receiptHandle())
                            .build()
            );

            log.info("INVOICE | generated and deleted from SQS | messageId: {}",
                    event.getMessageId());

        } catch (Exception e) {
            log.error("INVOICE | processing failed | sqsId: {} | reason: {}",
                    sqsMessage.messageId(), e.getMessage());
        }
    }

    private String getQueueUrl() {
        return sqsClient.getQueueUrl(
                GetQueueUrlRequest.builder()
                        .queueName(invoiceQueueName)
                        .build()
        ).queueUrl();
    }

}