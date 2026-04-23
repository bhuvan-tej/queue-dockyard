package com.queuedockyard.ecommerce.consumer;

import com.queuedockyard.ecommerce.model.OrderEvent;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Email Service — receives order events from RabbitMQ fanout exchange.
 *
 * Sends a confirmation email to the customer when an order is placed.
 * Completely independent from SmsConsumer — both receive the same
 * event from the fanout exchange but process it separately.
 *
 * Uses manual ACK — message deleted from queue only after
 * email is successfully sent (simulated here).
 */
@Slf4j
@Component
public class EmailConsumer {

    @RabbitListener(queues = "${app.rabbitmq.queues.email}")
    public void onOrderEvent(OrderEvent event,
                             Channel channel,
                             @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {

        log.info("EMAIL | received | messageId: {} | orderId: {} | to: {}",
                event.getMessageId(),
                event.getOrderId(),
                event.getCustomerEmail());

        try {
            // simulate sending confirmation email
            log.info("EMAIL | sending confirmation | to: {} | subject: 'Order {} Confirmed' | amount: {}",
                    event.getCustomerEmail(),
                    event.getOrderId(),
                    event.getAmount());

            channel.basicAck(deliveryTag, false);

            log.info("EMAIL | sent and ACKed | messageId: {}", event.getMessageId());

        } catch (Exception e) {
            log.error("EMAIL | failed | messageId: {} | reason: {}",
                    event.getMessageId(), e.getMessage());
            channel.basicNack(deliveryTag, false, true);
        }
    }

}