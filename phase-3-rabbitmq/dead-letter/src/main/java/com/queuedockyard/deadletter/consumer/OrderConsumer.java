package com.queuedockyard.deadletter.consumer;

import com.queuedockyard.deadletter.model.OrderMessage;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Consumes messages from the main orders queue.
 *
 * Retry count is tracked in the message body (retryCount field).
 * When DlqConsumer republishes for a retry, it increments
 * retryCount before sending — so the count correctly travels
 * with the message across redeliveries.
 *
 * Flow:
 *   Main queue → fails → NACK requeue=false → DLQ exchange → DLQ queue
 *   DlqConsumer checks retryCount:
 *     retryCount < maxRetries → increment count, republish to main queue
 *     retryCount >= maxRetries → store permanently, do not republish
 */
@Slf4j
@Component
public class OrderConsumer {

    @Value("${app.rabbitmq.main.max-retries}")
    private int maxRetries;

    @RabbitListener(queues = "${app.rabbitmq.main.queue}")
    public void onOrderMessage(OrderMessage message,
                               Channel channel,
                               @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {

        // retryCount=0 means first attempt, retryCount=1 means second, etc.
        int attemptNumber = message.getRetryCount() + 1;

        log.info("ORDER CONSUMER | attempt {} of {} | messageId: {} | orderId: {}",
                attemptNumber,
                maxRetries,
                message.getMessageId(),
                message.getOrderId());

        if (message.isShouldFail()) {

            log.warn("ORDER CONSUMER | FAILED | attempt {} of {} | routing to DLQ | messageId: {}",
                    attemptNumber, maxRetries, message.getMessageId());

            // requeue=false → triggers x-dead-letter-exchange routing to DLQ
            // DlqConsumer will decide whether to retry or permanently store
            channel.basicNack(deliveryTag, false, false);
            return;
        }

        try {
            processOrder(message);
            channel.basicAck(deliveryTag, false);
            log.info("ORDER CONSUMER | SUCCESS | ACK sent | messageId: {}",
                    message.getMessageId());

        } catch (Exception e) {
            log.warn("ORDER CONSUMER | EXCEPTION | attempt {} of {} | messageId: {} | reason: {}",
                    attemptNumber, maxRetries, message.getMessageId(), e.getMessage());
            channel.basicNack(deliveryTag, false, false);
        }
    }

    private void processOrder(OrderMessage message) {
        log.info("ORDER CONSUMER | processing | orderId: {} | amount: {}",
                message.getOrderId(), message.getAmount());
    }

}