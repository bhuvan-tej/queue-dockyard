package com.queuedockyard.jobqueue.service;

import com.queuedockyard.jobqueue.model.JobMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Publishes messages to RabbitMQ exchanges.
 *
 * Notice the pattern: rabbitTemplate.convertAndSend(exchange, routingKey, message)
 * Compare this to Kafka:  kafkaTemplate.send(topic, key, value)
 *
 * Key difference:
 *   Kafka  → you send to a TOPIC directly
 *   Rabbit → you send to an EXCHANGE with a ROUTING KEY
 *             the exchange decides which queue(s) receive it
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobPublisherService {

    private final RabbitTemplate rabbitTemplate;

    // ── Direct exchange ────────────────────────────────────────
    @Value("${app.rabbitmq.direct.exchange}")
    private String directExchange;

    @Value("${app.rabbitmq.direct.routing-key}")
    private String directRoutingKey;

    // ── Fanout exchange ────────────────────────────────────────
    @Value("${app.rabbitmq.fanout.exchange}")
    private String fanoutExchange;

    // ── Topic exchange ─────────────────────────────────────────
    @Value("${app.rabbitmq.topic.exchange}")
    private String topicExchange;

    /**
     * Publishes to the DIRECT exchange.
     *
     * Uses routing key "order.process" — only the queue bound
     * to this exact key receives the message.
     * One message → one queue → one worker processes it.
     *
     * @param payload the job description
     */
    public void publishDirectMessage(String payload) {

        JobMessage message = JobMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .routingKey(directRoutingKey)
                .payload(payload)
                .exchangeType("DIRECT")
                .createdAt(LocalDateTime.now())
                .build();

        rabbitTemplate.convertAndSend(directExchange, directRoutingKey, message);

        log.info("DIRECT published | exchange: {} | routingKey: {} | payload: {}",
                directExchange, directRoutingKey, payload);
    }

    /**
     * Publishes to the FANOUT exchange.
     *
     * Routing key is ignored by fanout — all bound queues receive it.
     * One message → email queue + sms queue + push queue.
     * All three consumers receive the same message independently.
     *
     * @param payload the notification content
     */
    public void publishFanoutMessage(String payload) {

        JobMessage message = JobMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .routingKey("ignored-by-fanout")
                .payload(payload)
                .exchangeType("FANOUT")
                .createdAt(LocalDateTime.now())
                .build();

        // empty string routing key — fanout ignores it anyway
        rabbitTemplate.convertAndSend(fanoutExchange, "", message);

        log.info("FANOUT published | exchange: {} | payload: {} | note: all queues receive this",
                fanoutExchange, payload);
    }

    /**
     * Publishes to the TOPIC exchange with a custom routing key.
     *
     * The routing key determines which queue(s) receive the message:
     *   "order.placed"    → matches "order.*" → topicOrdersQueue only
     *   "payment.success" → matches "payment.*" → topicPaymentsQueue only
     *   "order.payment"   → would match both if patterns overlapped
     *
     * @param routingKey the routing key e.g. "order.placed", "payment.success"
     * @param payload    the event content
     */
    public void publishTopicMessage(String routingKey, String payload) {

        JobMessage message = JobMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .routingKey(routingKey)
                .payload(payload)
                .exchangeType("TOPIC")
                .createdAt(LocalDateTime.now())
                .build();

        rabbitTemplate.convertAndSend(topicExchange, routingKey, message);

        log.info("TOPIC published | exchange: {} | routingKey: {} | payload: {}",
                topicExchange, routingKey, payload);
    }

}