package com.queuedockyard.deadletter.consumer;

import com.queuedockyard.deadletter.model.OrderMessage;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Consumes messages from the Dead Letter Queue.
 *
 * Acts as the retry coordinator:
 *   - Receives failed messages from OrderConsumer
 *   - Checks retryCount in the message body
 *   - If retries remaining: increments count, republishes to main queue
 *   - If max retries exceeded: stores permanently, alerts, stops
 *
 * Why track count in message body instead of x-death headers?
 *   x-death count resets when we republish via RabbitTemplate because
 *   it creates a fresh message with no x-death history.
 *   Message body fields survive republishing — they are part of our
 *   payload, not RabbitMQ metadata.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DlqConsumer {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.main.exchange}")
    private String mainExchange;

    @Value("${app.rabbitmq.main.routing-key}")
    private String mainRoutingKey;

    @Value("${app.rabbitmq.main.max-retries}")
    private int maxRetries;

    @Value("${app.rabbitmq.main.retry-delay-ms}")
    private long retryDelayMs;

    /** Permanently failed messages — stored for ops team inspection. */
    private final List<OrderMessage> deadLetters =
            Collections.synchronizedList(new ArrayList<>());

    @RabbitListener(queues = "${app.rabbitmq.dlq.queue}")
    public void onDeadLetter(OrderMessage message,
                             Channel channel,
                             @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException, InterruptedException {

        // retryCount in message body — survives republishing correctly
        int attemptNumber = message.getRetryCount() + 1;

        log.warn("DLQ CONSUMER | received | attempt {} of {} | messageId: {} | orderId: {}",
                attemptNumber, maxRetries,
                message.getMessageId(), message.getOrderId());

        if (attemptNumber < maxRetries) {

            // retries remaining — increment count and republish to main queue
            message.setRetryCount(attemptNumber);  // increment BEFORE republishing

            log.warn("DLQ CONSUMER | retrying in {}ms | next attempt will be {} of {} | messageId: {}",
                    retryDelayMs, attemptNumber + 1, maxRetries, message.getMessageId());

            Thread.sleep(retryDelayMs);

            // republish with incremented retryCount in the body
            // OrderConsumer will see the correct attempt number this time
            rabbitTemplate.convertAndSend(mainExchange, mainRoutingKey, message);

            channel.basicAck(deliveryTag, false);

            log.warn("DLQ CONSUMER | republished | messageId: {} | retryCount now: {}",
                    message.getMessageId(), message.getRetryCount());

        } else {

            // max retries exceeded — permanently dead
            log.error("DLQ CONSUMER | MAX RETRIES EXCEEDED | storing permanently | messageId: {} | orderId: {} | totalAttempts: {}",
                    message.getMessageId(), message.getOrderId(), attemptNumber);

            log.error("DLQ CONSUMER | ALERT — manual investigation required | orderId: {}",
                    message.getOrderId());

            deadLetters.add(message);

            channel.basicAck(deliveryTag, false);

            log.error("DLQ CONSUMER | total permanently failed: {}", deadLetters.size());
        }
    }

    public List<OrderMessage> getDeadLetters() {
        return Collections.unmodifiableList(deadLetters);
    }

}