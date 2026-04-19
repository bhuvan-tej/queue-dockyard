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
 * Three separate listeners — one per fanout queue.
 *
 * This is the key thing to observe with fanout:
 * When you publish ONE message to the fanout exchange,
 * ALL THREE methods below receive it independently.
 *
 * Each represents a different service reacting to the same event:
 *   - Email service sends a confirmation email
 *   - SMS service sends a text message
 *   - Push service sends a mobile notification
 *
 * They are completely independent — one failing does not affect the others.
 */
@Slf4j
@Component
public class FanoutConsumer {

    /**
     * Email service — receives every fanout message.
     */
    @RabbitListener(queues = "${app.rabbitmq.fanout.queue-email}")
    public void onEmailNotification(JobMessage message,
                                    Channel channel,
                                    @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {

        log.info("FANOUT EMAIL | received | messageId: {} | payload: {}",
                message.getMessageId(), message.getPayload());

        // simulate sending email
        log.info("FANOUT EMAIL | sending email for: {}", message.getPayload());

        channel.basicAck(deliveryTag, false);
    }

    /**
     * SMS service — receives every fanout message independently.
     * Notice same messageId as email — same message, different queue.
     */
    @RabbitListener(queues = "${app.rabbitmq.fanout.queue-sms}")
    public void onSmsNotification(JobMessage message,
                                  Channel channel,
                                  @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {

        log.info("FANOUT SMS | received | messageId: {} | payload: {}",
                message.getMessageId(), message.getPayload());

        // simulate sending SMS
        log.info("FANOUT SMS | sending SMS for: {}", message.getPayload());

        channel.basicAck(deliveryTag, false);
    }

    /**
     * Push notification service — receives every fanout message independently.
     */
    @RabbitListener(queues = "${app.rabbitmq.fanout.queue-push}")
    public void onPushNotification(JobMessage message,
                                   Channel channel,
                                   @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {

        log.info("FANOUT PUSH | received | messageId: {} | payload: {}",
                message.getMessageId(), message.getPayload());

        // simulate sending push notification
        log.info("FANOUT PUSH | sending push notification for: {}", message.getPayload());

        channel.basicAck(deliveryTag, false);
    }

}