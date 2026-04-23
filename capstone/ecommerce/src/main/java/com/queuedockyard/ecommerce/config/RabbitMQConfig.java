package com.queuedockyard.ecommerce.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for the capstone.
 *
 * Uses a Fanout exchange — one order event published once,
 * both Email and SMS queues receive it independently.
 *
 * This is the correct exchange type when:
 *   - All bound services need the same event
 *   - Services are independent (email failure doesn't affect SMS)
 *   - No routing logic needed
 */
@Configuration
public class RabbitMQConfig {

    @Value("${app.rabbitmq.exchange}")
    private String notificationsExchange;

    @Value("${app.rabbitmq.queues.email}")
    private String emailQueue;

    @Value("${app.rabbitmq.queues.sms}")
    private String smsQueue;

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

    /**
     * Fanout exchange — broadcasts to all bound queues.
     * No routing key needed — every bound queue gets every message.
     */
    @Bean
    public FanoutExchange notificationsExchange() {
        return ExchangeBuilder
                .fanoutExchange(notificationsExchange)
                .durable(true)
                .build();
    }

    @Bean
    public Queue emailQueue() {
        return QueueBuilder.durable(emailQueue).build();
    }

    @Bean
    public Queue smsQueue() {
        return QueueBuilder.durable(smsQueue).build();
    }

    @Bean
    public Binding emailBinding() {
        return BindingBuilder
                .bind(emailQueue())
                .to(notificationsExchange());
    }

    @Bean
    public Binding smsBinding() {
        return BindingBuilder
                .bind(smsQueue())
                .to(notificationsExchange());
    }

}