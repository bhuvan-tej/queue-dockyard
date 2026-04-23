package com.queuedockyard.ecommerce.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueNameExistsException;

import java.util.HashMap;
import java.util.Map;

/**
 * Creates the SQS invoice queue on startup if it doesn't exist.
 * Uses Map<QueueAttributeName, String> — correct type for AWS SDK v2.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqsQueueInitializer {

    private final SqsClient sqsClient;

    @Value("${aws.sqs.invoice-queue-name}")
    private String invoiceQueueName;

    @Value("${aws.sqs.visibility-timeout}")
    private String visibilityTimeout;

    @PostConstruct
    public void initQueues() {
        try {
            Map<QueueAttributeName, String> attributes = new HashMap<>();
            attributes.put(QueueAttributeName.VISIBILITY_TIMEOUT, visibilityTimeout);
            attributes.put(QueueAttributeName.MESSAGE_RETENTION_PERIOD, "86400");

            sqsClient.createQueue(
                    CreateQueueRequest.builder()
                            .queueName(invoiceQueueName)
                            .attributes(attributes)
                            .build()
            );

            log.info("Invoice queue ready | name: {}", invoiceQueueName);

        } catch (QueueNameExistsException e) {
            log.info("Invoice queue already exists | name: {}", invoiceQueueName);
        } catch (Exception e) {
            log.error("Failed to create invoice queue | reason: {}", e.getMessage());
            throw e;
        }
    }

}