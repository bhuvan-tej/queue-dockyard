package com.queuedockyard.deadletter.service;

import com.queuedockyard.deadletter.model.OrderMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Publishes order messages to the main RabbitMQ queue.
 *
 * Two publish methods:
 *   publishOrder        — normal message, consumer processes successfully
 *   publishFailingOrder — message with shouldFail=true, triggers retry + DLQ flow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPublisherService {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.main.exchange}")
    private String mainExchange;

    @Value("${app.rabbitmq.main.routing-key}")
    private String mainRoutingKey;

    /**
     * Publishes a normal order message.
     * Consumer will process this successfully on first attempt.
     */
    public void publishOrder(String orderId, String customerId, Double amount) {

        OrderMessage message = OrderMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .orderId(orderId)
                .customerId(customerId)
                .amount(amount)
                .shouldFail(false)      // normal message — will succeed
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .build();

        rabbitTemplate.convertAndSend(mainExchange, mainRoutingKey, message);

        log.info("Published normal order | messageId: {} | orderId: {}",
                message.getMessageId(), orderId);
    }

    /**
     * Publishes an order message that will always fail processing.
     * This triggers the retry logic and eventually the DLQ.
     *
     * Use this to observe:
     *   Attempt 1 → fails → NACK with requeue
     *   Attempt 2 → fails → NACK with requeue
     *   Attempt 3 → fails → NACK with requeue=false → DLQ
     */
    public void publishFailingOrder(String orderId, String customerId, Double amount) {

        OrderMessage message = OrderMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .orderId(orderId)
                .customerId(customerId)
                .amount(amount)
                .shouldFail(true)       // will fail on every attempt
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .build();

        rabbitTemplate.convertAndSend(mainExchange, mainRoutingKey, message);

        log.info("Published failing order | messageId: {} | orderId: {} | will retry then DLQ",
                message.getMessageId(), orderId);
    }

}