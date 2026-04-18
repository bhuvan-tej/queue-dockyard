package com.queuedockyard.multipartitiondemo.consumer;

import com.queuedockyard.multipartitiondemo.model.InventoryEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for inventory events.
 *
 * This is where the interesting learning happens.
 *
 * When you run ONE instance of this app:
 *   - This listener handles all 3 partitions (3 threads via setConcurrency)
 *
 * When you run THREE instances of this app:
 *   - Kafka triggers a REBALANCE
 *   - Each instance gets assigned exactly 1 partition
 *   - You will see in the logs: "partition assigned: [X]"
 *
 * When you KILL one instance:
 *   - Kafka detects the consumer left the group (within ~10 seconds)
 *   - Another rebalance happens
 *   - The surviving instances absorb the orphaned partition
 *   - You will see this in the logs without writing a single line of failover code
 *
 * This automatic rebalancing is one of Kafka's most powerful features.
 */
@Slf4j
@Component
public class InventoryEventConsumer {

    /**
     * Listens to inventory-events topic.
     *
     * containerFactory refers to the bean we defined in KafkaConfig.
     * This is what gives us 3 concurrent consumer threads.
     *
     * The log format here is intentionally detailed — partition and offset
     * are the most important things to watch in this demo.
     */
    @KafkaListener(
            topics = "${app.kafka.topic.inventory}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onInventoryEvent(ConsumerRecord<String, InventoryEvent> record) {

        InventoryEvent event = record.value();

        // notice the partition number in your logs —
        // same productId will ALWAYS appear on the same partition
        log.info("RECEIVED | partition: {} | offset: {} | productId: {} | eventType: {} | qty: {}",
                record.partition(),
                record.offset(),
                event.getProductId(),
                event.getEventType(),
                event.getQuantity());
    }

}