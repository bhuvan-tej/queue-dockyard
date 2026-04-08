# 🧠 Messaging Core Concepts

Before working with Kafka, RabbitMQ, or SQS, you must understand these core terms.

You will see them everywhere.

---
## 🧩 Core Concepts

### Producer

The service that **creates and publishes messages.**

 - In Kafka: publishes to a Topic.
 - In RabbitMQ: publishes to an Exchange.
 - In SQS: sends to a Queue.

---

### Consumer

The service that **receives and processes messages**.

 - Kafka → reads from a Topic
 - RabbitMQ → consumes from a Queue and sends ACK
 - SQS → polls the queue and deletes message after processing

---

### Broker

The system that **manages message flow** between producers and consumers.

Examples:
 - Kafka
 - RabbitMQ
 - AWS SQS 

---

## 🟠 Kafka-Specific Terms

### Topic

 - A named, ordered, append-only log of messages.
 - Split into Partitions for parallel processing.
 - Messages expire by time or size, not by consumption.

Topic: "orders"

---

### Partition

A Topic is split into **partitions** for parallel processing.
 - Each partition is an ordered, independent log.
 - More partitions = more consumers can work in parallel.
 - Messages with the same key always go to the same partition (ordering guarantee).
```
   Partition 0: [msg1] [msg4] [msg7]
   Partition 1: [msg2] [msg5] [msg8]
   Partition 2: [msg3] [msg6] [msg9]
```

---

### Offset

The **position of a message** in a partition.
 - Starts at 0
 - Each consumer group tracks its own offset per partition.
 - This is what allows replay — just reset the offset to any position.
```
Partition 0: [msg1:offset0] [msg4:offset1] [msg7:offset2]
                                                    ↑
                                           consumer is here (offset 2)
```

---

### Consumer Group

 - A group of consumer instances that share the work of reading a topic.
 - Kafka assigns each partition to exactly one consumer in the group.
 - Two groups reading the same topic each get ALL messages independently.
```
Topic "orders" — 3 partitions
  Group A (Inventory Service): Consumer1→P0, Consumer2→P1, Consumer3→P2
  Group B (Analytics Service): ConsumerA→P0, ConsumerB→P1, ConsumerC→P2
  Both groups receive every message. They are completely independent.
```

---

## 🟣 RabbitMQ-Specific Terms

### Exchange

The **routing layer** in RabbitMQ.

 - The routing layer. Producers never send directly to a Queue.
 - They send to an Exchange, which routes based on rules.

| Exchange Type | Routing Behaviour                                               |
|---------------|-----------------------------------------------------------------|
| Direct        | Routes to queue whose binding key exactly matches               |
| Fanout        | Routes to ALL bound queues (broadcast)                          |
| Topic         | Routes by pattern match (e.g. `order.*` matches `order.placed`) |
| Headers       | Routes by message header attributes                             |

---

### Queue (RabbitMQ and SQS)

 - A buffer that holds messages until a consumer picks them up.
 - In RabbitMQ: message deleted after ACK.
 - In SQS: message deleted after explicit DeleteMessage call.

---

### ACK — Acknowledgement

 - A signal sent by the consumer to confirm: **Message processed successfully**
 - If the consumer crashes before sending an ACK, RabbitMQ re-queues the message.
 - This is how RabbitMQ guarantees at-least-once delivery and prevents message loss.

---

## 🟡 Common Concepts (Across Systems)

### Dead Letter Queue (DLQ)

 - Where messages go when they fail processing too many times.
 - Prevents a single bad message (called a "poison message") from blocking your queue forever.

Used to:
- Prevent blocking
 - Debug failures
 - Reprocess later 

---

### Idempotency

 - Processing the same message twice produces the same result as once.
 - This matters because all three systems guarantee at-least-once delivery —
   meaning your consumer WILL occasionally receive duplicates.
```
// NOT idempotent — running twice charges the customer twice
chargeCustomer(orderId, amount);

// Idempotent — second run does nothing because order is already charged
if (!alreadyCharged(orderId)) {
    chargeCustomer(orderId, amount);
    markAsCharged(orderId);
}
```