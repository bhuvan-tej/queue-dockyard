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
 * Consumes messages from the direct orders queue.
 *
 * This simulates a worker that processes order jobs one at a time.
 *
 * Manual ACK pattern:
 *   channel.basicAck(deliveryTag, false)  → success, delete message
 *   channel.basicNack(deliveryTag, false, true) → failure, requeue message
 *
 * The deliveryTag is RabbitMQ's handle for this specific delivery.
 * Think of it like a receipt number — you use it to tell RabbitMQ
 * which message you're acknowledging.
 *
 * prefetch=1 in application.yml means this consumer won't receive
 * a new message until it ACKs the current one — fair load distribution.
 */
@Slf4j
@Component
public class DirectOrderConsumer {

    /**
     * Listens to the direct orders queue.
     *
     * Channel and deliveryTag are injected by Spring AMQP
     * because we set acknowledge-mode: manual in application.yml.
     */
    @RabbitListener(queues = "${app.rabbitmq.direct.queue}")
    public void onOrderJob(JobMessage message,
                           Channel channel,
                           @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        log.info("DIRECT CONSUMER | received | messageId: {} | payload: {}",
                message.getMessageId(),
                message.getPayload());

        try {
            // simulate processing the order job
            processOrderJob(message);

            // ACK — tell RabbitMQ: processed successfully, delete this message
            channel.basicAck(deliveryTag, false);

            log.info("DIRECT CONSUMER | ACK sent | messageId: {}", message.getMessageId());

        } catch (Exception e) {

            // NACK with requeue=true — tell RabbitMQ: failed, put it back in the queue
            // in Phase 4 dead-letter-demo we'll improve this with retry limits
            channel.basicNack(deliveryTag, false, true);

            log.error("DIRECT CONSUMER | NACK sent | messageId: {} | reason: {}",
                    message.getMessageId(), e.getMessage());
        }
    }

    private void processOrderJob(JobMessage message) {
        log.info("DIRECT CONSUMER | processing order job | payload: {}", message.getPayload());
        // simulate work
    }

}