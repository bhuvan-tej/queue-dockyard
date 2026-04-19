package com.queuedockyard.deadletter.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ topology for the DLQ pattern.
 *
 * Two exchanges, two queues, two bindings:
 *
 * NORMAL FLOW:
 *   orders.exchange → "order.process" → orders.queue → OrderConsumer
 *
 * FAILURE FLOW (after max retries):
 *   orders.queue → x-dead-letter-exchange → orders.dlq.exchange
 *                                                   ↓
 *                                           orders.dlq.queue → DlqConsumer
 *
 * The magic is in the queue arguments:
 *   x-dead-letter-exchange  — where to route rejected messages
 *   x-dead-letter-routing-key — routing key to use when dead-lettering
 *
 * RabbitMQ moves a message to the DLQ when:
 *   1. Consumer sends basicReject or basicNack with requeue=false
 *   2. Message TTL expires (not used here)
 *   3. Queue length limit exceeded (not used here)
 */
@Configuration
public class RabbitMQConfig {

    // ── Main queue properties ──────────────────────────────────
    @Value("${app.rabbitmq.main.exchange}")
    private String mainExchange;

    @Value("${app.rabbitmq.main.queue}")
    private String mainQueue;

    @Value("${app.rabbitmq.main.routing-key}")
    private String mainRoutingKey;

    // ── DLQ properties ─────────────────────────────────────────
    @Value("${app.rabbitmq.dlq.exchange}")
    private String dlqExchange;

    @Value("${app.rabbitmq.dlq.queue}")
    private String dlqQueue;

    @Value("${app.rabbitmq.dlq.routing-key}")
    private String dlqRoutingKey;

    // ══════════════════════════════════════════════════════════
    // MESSAGE CONVERTER
    // ══════════════════════════════════════════════════════════

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    // ══════════════════════════════════════════════════════════
    // MAIN EXCHANGE AND QUEUE
    // ══════════════════════════════════════════════════════════

    @Bean
    public DirectExchange mainExchange() {
        return ExchangeBuilder
                .directExchange(mainExchange)
                .durable(true)
                .build();
    }

    /**
     * Main queue with DLQ configuration.
     *
     * The two queue arguments are what makes DLQ work:
     *
     * x-dead-letter-exchange:
     *   When a message is rejected (nack with requeue=false),
     *   RabbitMQ forwards it to this exchange instead of dropping it.
     *
     * x-dead-letter-routing-key:
     *   The routing key used when forwarding to the DLQ exchange.
     *   Must match the DLQ exchange binding to reach the DLQ queue.
     *
     * Without these arguments, rejected messages are simply dropped.
     */
    @Bean
    public Queue mainQueue() {
        Map<String, Object> args = new HashMap<>();

        // forward rejected messages to the DLQ exchange
        args.put("x-dead-letter-exchange", dlqExchange);

        // use this routing key when forwarding to DLQ exchange
        args.put("x-dead-letter-routing-key", dlqRoutingKey);

        return QueueBuilder
                .durable(mainQueue)
                .withArguments(args)
                .build();
    }

    @Bean
    public Binding mainBinding() {
        return BindingBuilder
                .bind(mainQueue())
                .to(mainExchange())
                .with(mainRoutingKey);
    }

    // ══════════════════════════════════════════════════════════
    // DLQ EXCHANGE AND QUEUE
    // ══════════════════════════════════════════════════════════

    /**
     * Dead Letter Exchange — receives messages rejected from the main queue.
     * Completely separate from the main exchange.
     */
    @Bean
    public DirectExchange dlqExchange() {
        return ExchangeBuilder
                .directExchange(dlqExchange)
                .durable(true)
                .build();
    }

    /**
     * Dead Letter Queue — stores failed messages for inspection.
     *
     * Messages here are NOT automatically retried.
     * They sit here until:
     *   - An ops team member inspects and fixes the root cause
     *   - The DlqConsumer replays them after a fix
     *   - They are manually discarded
     */
    @Bean
    public Queue dlqQueue() {
        return QueueBuilder
                .durable(dlqQueue)
                .build();
    }

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder
                .bind(dlqQueue())
                .to(dlqExchange())
                .with(dlqRoutingKey);
    }

}