# ⚙️ exactly-once-demo

Demonstrates the duplicate message problem in distributed systems
and how to solve it using idempotency keys.

---

## The Problem — At-Least-Once Delivery

In your current setup, if your consumer crashes mid-processing —
after reading the message but before committing the offset —
Kafka will redeliver that message on restart.

 ```
Consumer reads message → starts processing → CRASH
↓
Kafka sees offset was never committed
↓
Consumer restarts → receives same message again
↓
Message processed TWICE ← this is the problem
```

Real world consequence — a payment service crashes after charging
the customer but before committing the offset. On restart, it charges
the customer again. That is a production incident.

---

## The Solution — Idempotency Key

```
Consumer reads message
↓
Check: have I already processed this messageId?
↓
YES → skip it, commit offset, move on (duplicate safely ignored)
NO  → process it, save messageId, commit offset
```

The `messageId` stored after processing is the idempotency key.
Processing the same message twice produces the same result as once.

---

## Two Consumers — Side by Side

This demo runs two consumers on the same topic in different groups
so you can compare the broken vs correct behavior in one run.

| Consumer                | Group                  | Behaviour                                                          |
|-------------------------|------------------------|--------------------------------------------------------------------|
| `NonIdempotentConsumer` | `non-idempotent-group` | Processes every message blindly — duplicates cause double charging |
| `IdempotentConsumer`    | `idempotent-group`     | Checks messageId before processing — duplicates safely skipped     |

---

## Key Concepts Demonstrated

### Manual Offset Commit
Default Kafka commits offsets on a timer — dangerous.
With `ack-mode: MANUAL_IMMEDIATE` we commit only after
successful processing. Full control, no silent data loss.

### Idempotency Key
A unique ID stamped on every message by the producer.
The consumer stores processed IDs and checks before processing.
Same messageId seen twice → second one is skipped.

### Idempotent Producer
`enable.idempotence: true` on the producer prevents
broker-level duplicates caused by producer retries.
This handles producer-side duplicates only —
consumer-side duplicates still need idempotency keys.

### Production Note — Redis vs In-Memory
This demo uses an in-memory store for processed IDs.
In production use Redis SET — survives restarts, shared across
multiple consumer instances, O(1) lookup and write.

```
// production idempotency check with Redis
if (redisTemplate.opsForSet().isMember("processed-payments", messageId)) {
// duplicate — skip
}
redisTemplate.opsForSet().add("processed-payments", messageId);
```

---

## How to Run

```bash
# start Kafka
docker compose -f docker/kafka-compose.yml up -d

# start the app
cd phase-3-kafka-internals/exactly-once-demo
mvn spring-boot:run
```

App starts on port **8084**.

---

## Experiments

### Experiment 1 — Normal payment (no duplicate)

```bash
curl -X POST http://localhost:8084/api/payments \
  -H "Content-Type: application/json" \
  -d '{"orderId":"ORD-001","customerId":"CUST-001","amount":1500.00,"paymentMethod":"UPI"}'
```

Both consumers process it once. No difference yet.

---

### Experiment 2 — Duplicate payment (the key demo)

```bash
curl -X POST http://localhost:8084/api/payments/duplicate \
  -H "Content-Type: application/json" \
  -d '{"orderId":"ORD-002","customerId":"CUST-002","amount":2500.00,"paymentMethod":"CREDIT_CARD"}'
```

Watch the logs carefully:

```
NonIdempotentConsumer — BUG
NON-IDEMPOTENT | CHARGING CUSTOMER: CUST-002 | amount: 2500.0   ← first delivery
NON-IDEMPOTENT | CHARGING CUSTOMER: CUST-002 | amount: 2500.0   ← duplicate — charged again!

IdempotentConsumer — CORRECT
IDEMPOTENT | CHARGING CUSTOMER: CUST-002 | amount: 2500.0        ← first delivery
IDEMPOTENT | DUPLICATE DETECTED — skipping | messageId: abc-123  ← duplicate safely ignored
```

---

### Experiment 3 — Inspect processed IDs

```bash
curl http://localhost:8084/api/payments/processed
```

Shows all messageIds the IdempotentConsumer has processed.
Notice the count never exceeds the number of unique payments —
even after sending duplicates.

---

## Configuration

| Property                                              | Value              | Description                                |
|-------------------------------------------------------|--------------------|--------------------------------------------|
| `spring.kafka.consumer.enable-auto-commit`            | `false`            | Disable auto offset commit                 |
| `spring.kafka.listener.ack-mode`                      | `MANUAL_IMMEDIATE` | Commit only when we call ack.acknowledge() |
| `spring.kafka.producer.properties.enable.idempotence` | `true`             | Prevent producer-side duplicates           |
| `app.kafka.topic.payments`                            | `payment-events`   | Topic for this demo                        |
| `server.port`                                         | `8084`             | HTTP port                                  |