package com.queuedockyard.jobqueue.consumer;

import com.queuedockyard.jobqueue.model.JobMessage;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Two listeners — one per topic queue.
 *
 * The routing key pattern determines which listener receives the message:
 *   Publish with "order.placed"    → onOrderEvent receives it
 *   Publish with "order.shipped"   → onOrderEvent receives it
 *   Publish with "payment.success" → onPaymentEvent receives it
 *   Publish with "payment.failed"  → onPaymentEvent receives it
 *   Publish with "inventory.low"   → NOBODY receives it (no matching pattern)
 *
 * This selective routing is the power of topic exchange —
 * services only receive the events they care about.
 */
@Slf4j
@Component
public class TopicConsumer {

    /**
     * Receives all messages matching "order.*"
     * order.placed, order.shipped, order.cancelled etc.
     */
    @RabbitListener(queues = "${app.rabbitmq.topic.queue-orders}")
    public void onOrderEvent(JobMessage message,
                             Channel channel,
                             @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {

        log.info("TOPIC ORDERS | received | routingKey: {} | messageId: {} | payload: {}",
                message.getRoutingKey(),
                message.getMessageId(),
                message.getPayload());

        log.info("TOPIC ORDERS | processing order event: {}", message.getRoutingKey());

        channel.basicAck(deliveryTag, false);
    }

    /**
     * Receives all messages matching "payment.*"
     * payment.success, payment.failed, payment.refunded etc.
     */
    @RabbitListener(queues = "${app.rabbitmq.topic.queue-payments}")
    public void onPaymentEvent(JobMessage message,
                               Channel channel,
                               @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {

        log.info("TOPIC PAYMENTS | received | routingKey: {} | messageId: {} | payload: {}",
                message.getRoutingKey(),
                message.getMessageId(),
                message.getPayload());

        log.info("TOPIC PAYMENTS | processing payment event: {}", message.getRoutingKey());

        channel.basicAck(deliveryTag, false);
    }

}