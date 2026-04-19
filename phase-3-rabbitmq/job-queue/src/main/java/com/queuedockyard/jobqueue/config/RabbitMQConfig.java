package com.queuedockyard.jobqueue.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology configuration.
 *
 * This is where we declare all exchanges, queues, and bindings.
 * Spring AMQP creates these on startup if they don't already exist.
 *
 * Topology overview:
 *
 * DIRECT EXCHANGE
 *   direct.orders.exchange
 *     → binding key "order.process"
 *     → direct.orders.queue
 *
 * FANOUT EXCHANGE
 *   fanout.notifications.exchange (ignores routing key)
 *     → fanout.notifications.email.queue
 *     → fanout.notifications.sms.queue
 *     → fanout.notifications.push.queue
 *
 * TOPIC EXCHANGE
 *   topic.events.exchange
 *     → pattern "order.*"    → topic.orders.queue
 *     → pattern "payment.*"  → topic.payments.queue
 */
@Configuration
public class RabbitMQConfig {

    // ── Direct exchange properties ─────────────────────────────
    @Value("${app.rabbitmq.direct.exchange}")
    private String directExchange;

    @Value("${app.rabbitmq.direct.queue}")
    private String directQueue;

    @Value("${app.rabbitmq.direct.routing-key}")
    private String directRoutingKey;

    // ── Fanout exchange properties ─────────────────────────────
    @Value("${app.rabbitmq.fanout.exchange}")
    private String fanoutExchange;

    @Value("${app.rabbitmq.fanout.queue-email}")
    private String fanoutEmailQueue;

    @Value("${app.rabbitmq.fanout.queue-sms}")
    private String fanoutSmsQueue;

    @Value("${app.rabbitmq.fanout.queue-push}")
    private String fanoutPushQueue;

    // ── Topic exchange properties ──────────────────────────────
    @Value("${app.rabbitmq.topic.exchange}")
    private String topicExchange;

    @Value("${app.rabbitmq.topic.queue-orders}")
    private String topicOrdersQueue;

    @Value("${app.rabbitmq.topic.queue-payments}")
    private String topicPaymentsQueue;

    @Value("${app.rabbitmq.topic.routing-key-orders}")
    private String topicOrdersRoutingKey;

    @Value("${app.rabbitmq.topic.routing-key-payments}")
    private String topicPaymentsRoutingKey;

    // ══════════════════════════════════════════════════════════
    // MESSAGE CONVERTER
    // ══════════════════════════════════════════════════════════

    /**
     * Converts Java objects to JSON when publishing,
     * and JSON back to Java objects when consuming.
     *
     * Without this, Spring AMQP uses Java serialization by default —
     * which is slow, fragile, and not interoperable with other languages.
     * Always use Jackson JSON converter in real projects.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * Configures RabbitTemplate to use our JSON converter.
     * RabbitTemplate is Spring's wrapper for publishing messages —
     * equivalent to KafkaTemplate in the Kafka world.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    // ══════════════════════════════════════════════════════════
    // DIRECT EXCHANGE — exact routing key match
    // ══════════════════════════════════════════════════════════

    /**
     * Direct exchange routes messages to queues whose binding key
     * exactly matches the routing key on the message.
     *
     * durable(true) — exchange survives RabbitMQ restart.
     * Always use durable in production.
     */
    @Bean
    public DirectExchange directOrdersExchange() {
        return ExchangeBuilder
                .directExchange(directExchange)
                .durable(true)
                .build();
    }

    /**
     * Queue that receives direct exchange messages.
     *
     * durable(true) — queue survives RabbitMQ restart.
     * Without this, the queue (and all its messages) disappear on restart.
     */
    @Bean
    public Queue directOrdersQueue() {
        return QueueBuilder
                .durable(directQueue)
                .build();
    }

    /**
     * Binding — connects the exchange to the queue with a routing key.
     * Messages published to directOrdersExchange with routing key
     * "order.process" will be delivered to directOrdersQueue.
     */
    @Bean
    public Binding directOrdersBinding() {
        return BindingBuilder
                .bind(directOrdersQueue())
                .to(directOrdersExchange())
                .with(directRoutingKey);
    }

    // ══════════════════════════════════════════════════════════
    // FANOUT EXCHANGE — broadcast to all queues
    // ══════════════════════════════════════════════════════════

    /**
     * Fanout exchange ignores routing keys entirely.
     * Every message is delivered to ALL queues bound to this exchange.
     * Perfect for broadcasting events to multiple independent services.
     */
    @Bean
    public FanoutExchange fanoutNotificationsExchange() {
        return ExchangeBuilder
                .fanoutExchange(fanoutExchange)
                .durable(true)
                .build();
    }

    @Bean
    public Queue fanoutEmailQueue() {
        return QueueBuilder.durable(fanoutEmailQueue).build();
    }

    @Bean
    public Queue fanoutSmsQueue() {
        return QueueBuilder.durable(fanoutSmsQueue).build();
    }

    @Bean
    public Queue fanoutPushQueue() {
        return QueueBuilder.durable(fanoutPushQueue).build();
    }

    /**
     * Fanout bindings — no routing key needed.
     * All three queues receive every message published to this exchange.
     */
    @Bean
    public Binding fanoutEmailBinding() {
        return BindingBuilder
                .bind(fanoutEmailQueue())
                .to(fanoutNotificationsExchange());
    }

    @Bean
    public Binding fanoutSmsBinding() {
        return BindingBuilder
                .bind(fanoutSmsQueue())
                .to(fanoutNotificationsExchange());
    }

    @Bean
    public Binding fanoutPushBinding() {
        return BindingBuilder
                .bind(fanoutPushQueue())
                .to(fanoutNotificationsExchange());
    }

    // ══════════════════════════════════════════════════════════
    // TOPIC EXCHANGE — pattern-based routing
    // ══════════════════════════════════════════════════════════

    /**
     * Topic exchange routes by pattern matching on the routing key.
     *
     * Two wildcard characters:
     *   * (star)   — matches exactly one word
     *   # (hash)   — matches zero or more words
     *
     * Examples:
     *   "order.*"   matches "order.placed", "order.shipped" but NOT "order.item.placed"
     *   "order.#"   matches "order.placed", "order.item.placed", "order.item.color.red"
     *   "*.placed"  matches "order.placed", "payment.placed" but not "order.item.placed"
     */
    @Bean
    public TopicExchange topicEventsExchange() {
        return ExchangeBuilder
                .topicExchange(topicExchange)
                .durable(true)
                .build();
    }

    @Bean
    public Queue topicOrdersQueue() {
        return QueueBuilder.durable(topicOrdersQueue).build();
    }

    @Bean
    public Queue topicPaymentsQueue() {
        return QueueBuilder.durable(topicPaymentsQueue).build();
    }

    /**
     * Topic bindings — uses pattern matching.
     * "order.*"   → topicOrdersQueue   receives order.placed, order.shipped etc.
     * "payment.*" → topicPaymentsQueue receives payment.success, payment.failed etc.
     */
    @Bean
    public Binding topicOrdersBinding() {
        return BindingBuilder
                .bind(topicOrdersQueue())
                .to(topicEventsExchange())
                .with(topicOrdersRoutingKey);
    }

    @Bean
    public Binding topicPaymentsBinding() {
        return BindingBuilder
                .bind(topicPaymentsQueue())
                .to(topicEventsExchange())
                .with(topicPaymentsRoutingKey);
    }

}