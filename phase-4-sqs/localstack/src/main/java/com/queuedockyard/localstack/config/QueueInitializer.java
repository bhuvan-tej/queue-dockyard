package com.queuedockyard.localstack.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Creates SQS queues on application startup if they don't exist.
 *
 * Unlike Kafka (NewTopic bean) or RabbitMQ (Queue bean),
 * SQS queues must be created via explicit API calls.
 *
 * @PostConstruct runs after Spring has fully initialized the bean —
 * guaranteed that SqsClient is ready before we try to use it.
 *
 * Idempotent — calling CreateQueue on an existing queue with the
 * same attributes simply returns the existing queue URL.
 * Safe to run on every startup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueueInitializer {

    private final SqsClient sqsClient;

    @Value("${aws.sqs.standard-queue-name}")
    private String standardQueueName;

    @Value("${aws.sqs.fifo-queue-name}")
    private String fifoQueueName;

    @Value("${aws.sqs.visibility-timeout}")
    private String visibilityTimeout;

    @PostConstruct
    public void initQueues() {
        createStandardQueue();
        createFifoQueue();
    }

    /**
     * Creates a Standard SQS queue.
     *
     * Standard queue characteristics:
     *   - At-least-once delivery (duplicates possible)
     *   - Best-effort ordering (not guaranteed)
     *   - Nearly unlimited throughput
     *   - Cheaper than FIFO
     */
    private void createStandardQueue() {
        try {
            Map<QueueAttributeName, String> attributes = new HashMap<>();

            attributes.put(QueueAttributeName.VISIBILITY_TIMEOUT, visibilityTimeout);
            attributes.put(QueueAttributeName.MESSAGE_RETENTION_PERIOD, "86400");

            CreateQueueRequest request = CreateQueueRequest.builder()
                    .queueName(standardQueueName)
                    .attributes(attributes)
                    .build();

            CreateQueueResponse response = sqsClient.createQueue(request);

            log.info("Standard queue ready | name: {} | url: {}",
                    standardQueueName, response.queueUrl());

        } catch (QueueNameExistsException e) {
            log.info("Standard queue already exists | name: {}", standardQueueName);
        } catch (Exception e) {
            log.error("Failed to create standard queue | name: {} | reason: {}",
                    standardQueueName, e.getMessage());
            throw e;
        }
    }

    /**
     * Creates a FIFO SQS queue.
     *
     * FIFO queue characteristics:
     *   - Exactly-once processing (deduplication built in)
     *   - Strict ordering within a message group
     *   - Up to 3,000 messages/sec with batching
     *   - Name MUST end with .fifo — AWS enforces this strictly
     *
     * Two extra required attributes:
     *   FifoQueue=true           — marks this as a FIFO queue
     *   ContentBasedDeduplication=true — SQS deduplicates based on
     *     message content hash, so you don't need to provide a
     *     MessageDeduplicationId manually on every send
     */
    private void createFifoQueue() {
        try {
            Map<QueueAttributeName, String> attributes = new HashMap<>();

            attributes.put(QueueAttributeName.VISIBILITY_TIMEOUT, visibilityTimeout);
            attributes.put(QueueAttributeName.MESSAGE_RETENTION_PERIOD, "86400");
            attributes.put(QueueAttributeName.FIFO_QUEUE, "true");
            attributes.put(QueueAttributeName.CONTENT_BASED_DEDUPLICATION, "true");

            CreateQueueRequest request = CreateQueueRequest.builder()
                    .queueName(fifoQueueName)
                    .attributes(attributes)
                    .build();

            CreateQueueResponse response = sqsClient.createQueue(request);

            log.info("FIFO queue ready | name: {} | url: {}",
                    fifoQueueName, response.queueUrl());

        } catch (QueueNameExistsException e) {
            log.info("FIFO queue already exists | name: {}", fifoQueueName);
        } catch (Exception e) {
            log.error("Failed to create FIFO queue | name: {} | reason: {}",
                    fifoQueueName, e.getMessage());
            throw e;
        }
    }

}