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
 * SMS Service — receives the same order events as EmailConsumer
 * but from a separate queue bound to the same fanout exchange.
 *
 * If EmailConsumer fails, SmsConsumer is unaffected — they are
 * completely independent queues, just bound to the same exchange.
 * This is the power of the fanout pattern.
 */
@Slf4j
@Component
public class SmsConsumer {

    @RabbitListener(queues = "${app.rabbitmq.queues.sms}")
    public void onOrderEvent(OrderEvent event,
                             Channel channel,
                             @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {

        log.info("SMS | received | messageId: {} | orderId: {} | to: {}",
                event.getMessageId(),
                event.getOrderId(),
                event.getCustomerPhone());

        try {
            // simulate sending SMS
            log.info("SMS | sending | to: {} | message: 'Order {} placed for ₹{}. Thank you!'",
                    event.getCustomerPhone(),
                    event.getOrderId(),
                    event.getAmount());

            channel.basicAck(deliveryTag, false);

            log.info("SMS | sent and ACKed | messageId: {}", event.getMessageId());

        } catch (Exception e) {
            log.error("SMS | failed | messageId: {} | reason: {}",
                    event.getMessageId(), e.getMessage());
            channel.basicNack(deliveryTag, false, true);
        }
    }

}