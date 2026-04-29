package com.queuedockyard.kafkadlt.consumer;

import com.queuedockyard.kafkadlt.model.OrderEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Order consumer with automatic retry and DLT routing.
 *
 * Two annotations drive everything here:
 *
 * @RetryableTopic — marks this listener as retry-capable.
 *   Spring Kafka intercepts any exception thrown from this method
 *   and routes the message to the appropriate retry topic.
 *   No try/catch needed in your consumer code — just throw.
 *
 * @DltHandler — marks the method that handles permanently failed messages.
 *   Called after all retries are exhausted.
 *   Lives in the same class as the main listener for clarity.
 *
 * The flow in plain terms:
 *   onOrderEvent throws exception
 *           ↓
 *   Spring Kafka catches it
 *           ↓
 *   Publishes message to dlt.orders-retry-10000
 *           ↓
 *   After 10 seconds, onOrderEvent called again (attempt 2)
 *           ↓
 *   Throws again → dlt.orders-retry-30000
 *           ↓
 *   After 30 seconds, attempt 3 → throws → dlt.orders-retry-60000
 *           ↓
 *   After 60 seconds, attempt 4 → throws → dlt.orders-dlt
 *           ↓
 *   onDltMessage called — permanent failure handling
 */
@Slf4j
@Component
public class OrderConsumer {

    /**
     * Main consumer — processes order events.
     *
     * @RetryableTopic configuration here overrides the bean-level
     * RetryTopicConfiguration for fine-grained control.
     * Using the bean approach (KafkaConfig) is cleaner for
     * applying the same config across multiple topics.
     * Both approaches work — using bean config here.
     *
     * Note: we do NOT use @RetryableTopic annotation here because
     * we configured retry via RetryTopicConfiguration bean in KafkaConfig.
     * Using both would conflict.
     */
    @KafkaListener(
            topics = "${app.kafka.topics.orders}",
            groupId = "kafka-dlt-group"
    )
    public void onOrderEvent(ConsumerRecord<String, OrderEvent> record,
                             @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                             @Header(KafkaHeaders.OFFSET) long offset) {

        OrderEvent event = record.value();

        log.info("ORDER CONSUMER | topic: {} | offset: {} | orderId: {} | attempt: processing",
                topic, offset, event.getOrderId());

        if (event.isShouldFail()) {

            // simulate a processing failure
            // in production this would be a real exception:
            //   - HttpClientErrorException (downstream service down)
            //   - DataAccessException (database unavailable)
            //   - NullPointerException (unexpected null data)
            log.warn("ORDER CONSUMER | FAILED | orderId: {} | routing to retry topic", event.getOrderId());

            throw new RuntimeException("Simulated processing failure for orderId: " + event.getOrderId());
        }

        // success path
        log.info("ORDER CONSUMER | SUCCESS | orderId: {} | customerId: {} | amount: {}",
                event.getOrderId(),
                event.getCustomerId(),
                event.getAmount());
    }

    /**
     * DLT handler — called when all retries are exhausted.
     *
     * @DltHandler marks this method as the dead letter handler
     * for the @KafkaListener in the same class.
     *
     * This is where you would:
     *   - Send alert to PagerDuty / Slack
     *   - Store the failed event in a database
     *   - Expose it via REST for manual inspection
     *   - Trigger an incident response workflow
     *
     * The receivedTopic header tells you which retry topic
     * the message came from last — useful for debugging.
     */
    @DltHandler
    public void onDltMessage(ConsumerRecord<String, OrderEvent> record,
                             @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                             @Header(KafkaHeaders.OFFSET) long offset) {

        OrderEvent event = record.value();

        log.error("DLT HANDLER | message permanently failed | topic: {} | offset: {} | orderId: {} | customerId: {} | amount: {}",
                topic,
                offset,
                event.getOrderId(),
                event.getCustomerId(),
                event.getAmount());

        log.error("DLT HANDLER | ALERT — manual investigation required | orderId: {}", event.getOrderId());

        // in production: store to DB, send alert, create incident ticket
    }

}