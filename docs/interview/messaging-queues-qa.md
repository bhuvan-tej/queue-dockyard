# Interview Q&A — Messaging Queues

Answers structured as: Problem → Solution → Tradeoff.
Every answer references something built in this repo.

---

## Kafka

### Why would you choose Kafka over RabbitMQ?

It depends on what downstream services need.

**Choose Kafka when:**
- Multiple independent services need the same events
- You need to replay past events (audit, reprocessing, analytics)
- You have high volume — millions of messages per second
- You need windowed aggregations or stream processing

**Choose RabbitMQ when:**
- Distributing tasks across workers — exactly one worker per job
- You need flexible routing (direct, fanout, topic exchanges)
- Messages should be deleted after processing — no replay needed
- You need simpler infrastructure for moderate volumes

In the capstone, both are used simultaneously:
Kafka for Inventory and Analytics (both need the same events independently),
RabbitMQ for Email and SMS (job queue, one worker processes each notification once).

---

### How do consumer groups enable scaling?

A consumer group lets you scale processing horizontally without changing producer code.

With a 3-partition topic and 3 consumer instances sharing the same group ID:
- Kafka assigns one partition per instance automatically
- Work is distributed — each instance handles one third of messages
- If one instance crashes, Kafka rebalances within ~10 seconds
- The surviving instances absorb the orphaned partition
- Zero failover code written — Kafka handles it entirely

**Tradeoff:** You cannot have more active consumers than partitions.
A fourth instance in a 3-partition topic sits idle.
Plan partition count based on your maximum expected consumers.

Demonstrated in: `phase-3-kafka-internals/multi-partition`

---

### What happens when a consumer crashes mid-processing?

With auto-commit (default): Kafka commits offsets on a timer.
If your consumer commits at 14:00:00 and crashes at 14:00:03
mid-processing, that message is lost — the offset was committed
before processing finished.

With manual commit: the offset is committed only when your code
calls `acknowledgment.acknowledge()` after successful processing.
On restart, the consumer re-reads from the last committed offset
and reprocesses the message.

**Tradeoff:** Manual commit gives at-least-once delivery —
the same message might be processed twice on restart.
The fix is an idempotency key so the second processing is a no-op.

Demonstrated in: `phase-3-kafka-internals/exactly-once`

---

### How do idempotency keys prevent duplicate processing?

The producer stamps a UUID on every message at publish time.
The consumer checks Redis before processing:
"Have I seen this messageId before?"

```
Yes → skip processing, commit offset, move on
No  → process, write messageId to Redis, commit offset
```

**Critical:** write to Redis AFTER processing succeeds, never before.
Writing before and crashing = message marked as processed but never handled.

**Why Redis over in-memory:**
In-memory store is lost on restart — duplicates slip through after recovery.
Redis persists across restarts, is shared across multiple instances,
and TTL-based expiry prevents unbounded growth.

Demonstrated in: `phase-3-kafka-internals/exactly-once` (in-memory)
and `capstone/ecommerce` (Redis-backed)

---

### How does the Dead Letter Topic pattern work?

Naive approach — NACK with requeue:
```
Message fails → requeued → fails again → requeued → infinite loop
Entire partition blocked — production incident
```

Non-blocking retry with DLT:
```
Message fails → published to retry-1 topic (wait 10s)
Main topic consumer keeps processing new messages immediately
After 10s: retry-1 consumer tries → fails → retry-2 (wait 30s)
After 30s: retry-2 → fails → retry-3 (wait 60s)
After 60s: retry-3 → fails → Dead Letter Topic
DLT consumer fires → alert ops team → store for investigation
```

The main partition is never blocked.
Spring Kafka's `RetryTopicConfiguration` handles all routing automatically.
Your consumer just throws an exception — Spring creates the retry chain.

**Tradeoff:** Total time to reach DLT = sum of all retry delays (~100s).
Tune delays based on your downstream system's recovery time.

Demonstrated in: `phase-2-kafka-basics/kafka-dlt`

---

### How do Kafka Streams avoid needing a database?

Classic pattern for "how many orders has this customer placed?":
Consumer → write to database → query database → answer.
Two extra hops, database becomes bottleneck.

Kafka Streams builds a continuously updated local state store
using RocksDB — an embedded key-value database running inside
your application on the same machine, no network call.

Every order event updates the customer's count in RocksDB.
Query response time: microseconds (local disk read).
If app crashes and restarts: Kafka Streams replays the topic
and rebuilds state automatically.

**Tradeoff:** State is local — in a multi-instance deployment,
each instance only knows about the partitions it owns.
Interactive queries need to be routed to the right instance.

Three pipelines built:
- Filter: order-events → PLACED only → placed-order-events
- Counter: order-events → count per customer → KTable (queryable)
- Revenue: order-events → sum per 1-minute window → revenue-per-minute

Demonstrated in: `phase-3-kafka-internals/kafka-streams`

---

### How does Schema Registry prevent breaking changes?

Without Schema Registry — honour system between producer and consumer.
Producer renames "amount" to "totalAmount", deploys, consumer gets null.
Silent data corruption. No exception thrown.

With Schema Registry:
```
Producer wants to publish
        ↓
Confluent serializer checks compatibility with Registry
        ↓
Compatible → publish proceeds
Incompatible → publish REJECTED before reaching Kafka
```

Every Avro message starts with 5 bytes: magic byte + schema ID.
Consumer reads schema ID, fetches schema from Registry (cached),
deserializes to strongly typed Java object.

**Safe change (backward compatible):**
Adding a field WITH a default value.
Existing consumers ignore the unknown field.

**Breaking change (rejected):**
Removing a field, renaming a field, changing a type.
Schema Registry rejects these at publish time.

**Avro enum advantage over String:**
```
event.setStatus(OrderStatus.PLACED);    // compiles
event.setStatus(OrderStatus.PLCAED);   // compile error — caught immediately
event.setStatus("PLACED");             // compile error — wrong type
```

Demonstrated in: `phase-2-kafka-basics/schema-registry`

---

### How do you observe a messaging system in production?

Three layers:

**1. Health checks** — `/actuator/health`
Shows Kafka, RabbitMQ, Redis status in one call.
Kubernetes uses this to decide whether to restart a pod.

**2. Metrics** — `/actuator/prometheus`
Micrometer auto-instruments: consumer lag, message rates, JVM health.
Custom business metrics: orders placed, emails sent, duplicates detected,
publish duration p50/p95/p99.

**3. Dashboards** — Grafana
Time series panels for throughput per minute and latency percentiles.

Most important metric: **consumer lag** — how far behind is your
consumer from the latest message? Growing lag = consumer can't keep up.
Track per partition to identify which specific partition is struggling.

Second most important: **duplicate detection counter**.
Non-zero and growing = something is replaying messages it shouldn't.

Demonstrated in: `capstone/ecommerce` with Prometheus + Grafana dashboard.

---

## RabbitMQ

### What are exchange types and when do you use each?

**Direct** — exact routing key match → one queue
```
Producer sends "order.process" → only queue bound to "order.process" receives it
Use for: task distribution, background jobs, one worker per message
```

**Fanout** — broadcast to all bound queues
```
Producer sends → ALL queues receive it, routing key ignored
Use for: notifications, event broadcasting to multiple independent services
```

**Topic** — pattern matching
```
"order.*" matches order.placed, order.shipped (not order.item.placed)
"order.#" matches order.placed, order.item.placed, order.item.color.red
Use for: selective event distribution, microservice event buses
```

**Headers** — match by message header attributes (rarely used)

---

### What is an ACK and why does it matter?

ACK = acknowledgement. The consumer's signal to RabbitMQ:
"I successfully processed this message, you can delete it."

```
Consumer receives message → processes it → sends ACK → deleted ✅
Consumer receives message → crashes → no ACK → RabbitMQ requeues 🔄
Consumer receives message → sends NACK requeue=true → requeued 🔄
Consumer receives message → sends NACK requeue=false → dead-lettered ➡️ DLQ
```

**Manual ACK vs Auto ACK:**
Auto ACK: RabbitMQ deletes message immediately on delivery.
If consumer crashes after delivery but before processing — message lost.

Manual ACK: consumer explicitly ACKs after successful processing.
Guarantees at-least-once delivery. Combined with idempotency keys
gives you the safest approach without exactly-once complexity.

---

### How does the Dead Letter Queue pattern work in RabbitMQ?

Configure the main queue with two arguments:
```
x-dead-letter-exchange: my.dlq.exchange
x-dead-letter-routing-key: my.dlq.routing.key
```

When consumer sends `basicNack(deliveryTag, false, false)` —
requeue=false — RabbitMQ automatically routes the message
to the x-dead-letter-exchange. No extra code needed.

The DLQ consumer receives permanently failed messages,
stores them for inspection, and sends alerts.

**Key difference from Kafka DLT:**
RabbitMQ routes automatically via broker configuration.
Kafka requires your consumer code to route to retry topics.
RabbitMQ retry blocks the main queue.
Kafka retry is non-blocking — main topic never blocked.

---

## SQS

### What is visibility timeout?

When a consumer receives a message, SQS hides it from all
other consumers for the visibility timeout duration (e.g. 30s).

```
Consumer receives message → message becomes invisible
        ↓
Consumer processes and deletes → message gone ✅
        ↓ (if NOT deleted within timeout)
Visibility timeout expires → message becomes visible again
Another consumer receives and processes → at-least-once delivery
```

This is SQS's equivalent of RabbitMQ's manual ACK.
The difference: SQS requires explicit deletion, RabbitMQ requires ACK.
Forgetting to delete = message redelivered after timeout.

---

### Standard Queue vs FIFO Queue — when to use which?

**Standard Queue:**
- At-least-once delivery (duplicates possible)
- Best-effort ordering (not guaranteed)
- Nearly unlimited throughput
- Use when: order doesn't matter, maximum speed needed

**FIFO Queue:**
- Exactly-once processing (built-in deduplication)
- Strict ordering within a message group (MessageGroupId)
- Up to 3,000 messages/sec with batching
- Name MUST end with `.fifo`
- Use when: order lifecycle events, financial transactions

**FIFO deduplication:**
Messages with the same MessageDeduplicationId within a
5-minute window are deduplicated automatically by SQS.
No consumer-side idempotency logic needed.

---

## General

### At-most-once vs At-least-once vs Exactly-once

**At-most-once:** message delivered 0 or 1 times. May be lost. Never duplicated.
Use for: non-critical logging, metrics where loss is acceptable.

**At-least-once:** message delivered 1 or more times. Never lost. Duplicates possible.
Use for: most production systems. Combine with idempotency keys.
This is the default for Kafka, RabbitMQ (manual ACK), and SQS.

**Exactly-once:** message delivered exactly once. No loss, no duplicates.
Use for: financial transactions only.
Kafka supports this with transactions (performance cost).
RabbitMQ and SQS do not support it natively.

**Practical recommendation:**
At-least-once + idempotent consumers is simpler, more scalable,
and what most production systems use. Exactly-once adds
significant complexity for marginal gain in most scenarios.

---

### What is a partition and why does it matter?

A partition is an ordered, independent log within a Kafka topic.
It is the unit of parallelism.

```
Topic: orders (3 partitions)
  Partition 0: [msg1:0] [msg4:1] [msg7:2]
  Partition 1: [msg2:0] [msg5:1] [msg8:2]
  Partition 2: [msg3:0] [msg6:1] [msg9:2]
```

**Why it matters:**
- More partitions = more consumer instances can work in parallel
- Messages with the same key always go to the same partition
  → ordering guaranteed per key (e.g. all events for orderId-001 are ordered)
- You can add partitions but cannot reduce them
- Partition count = max parallelism ceiling

**Rule of thumb:**
Set partitions = maximum number of consumers you expect to run.
Start with 3 for most workloads. Scale up as needed.