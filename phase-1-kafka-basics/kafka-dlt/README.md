# kafka-dlt

Demonstrates the Dead Letter Topic (DLT) pattern in Apache Kafka —
the production-standard approach for handling poison messages
without blocking your main consumer.

---

## RabbitMQ DLQ vs Kafka DLT — The Key Difference

In Phase 3 you built this with RabbitMQ:

```
RabbitMQ Queue → fails → NACK requeue=false → DLQ exchange → DLQ queue
```

RabbitMQ handles the routing automatically via `x-dead-letter-exchange`.
The broker moves the message for you.

**Kafka works completely differently:**

```
Kafka has no built-in dead letter mechanism.
The broker never moves messages automatically.
YOUR consumer code is responsible for routing failed messages.
```

---

## The Problem — Blocking Retry

Without the DLT pattern, a naive retry looks like this:

```
Main topic → Consumer fails → Retry in place → Fails again → Retry again
                                                                    ↓
                                                    Partition is BLOCKED while retrying
                                                        No other messages processed
                                                One poison message stalls the entire partition
```

This is the same poison message problem from Phase 3 — but in Kafka.

---

## The Solution — Non-Blocking Retry with Dead Letter Topic

```
Main topic   →   Consumer fails
                         ↓
              Spring Kafka catches exception
                         ↓
              Publishes to retry-1 topic (10s delay)
                         ↓ (after 10 seconds)
              retry-1 consumer fails again
                         ↓
              Publishes to retry-2 topic (30s delay)
                         ↓ (after 30 seconds)
              retry-2 consumer fails again
                         ↓
              Publishes to retry-3 topic (60s delay)
                         ↓ (after 60 seconds)
              retry-3 consumer fails again
                         ↓
              Publishes to DLT topic (permanent failure)
                         ↓
              DLT handler fires → alert → store → investigate
```

**Why non-blocking matters:**
While the failed message is sitting in retry-1 waiting 10 seconds,
the main topic consumer keeps processing new messages.
The main partition is never blocked.
One poison message cannot stall the entire system.

---

## How Spring Kafka Makes This Simple

Without Spring Kafka's built-in support, you would write:
- 3 separate `@KafkaListener` methods for each retry topic
- Manual routing logic in each consumer
- Topic creation code for each retry topic
- Delay logic for each retry

With Spring Kafka's `RetryTopicConfiguration`:
- One `@KafkaListener` on the main topic
- One `@DltHandler` for permanent failures
- Spring creates all retry topics automatically
- Spring handles all routing transparently
- You just throw an exception on failure

---

## Topic Structure

```
dlt.orders                  ← main topic (your consumer)
dlt.orders-retry-10000      ← retry after 10 seconds (auto-created)
dlt.orders-retry-30000      ← retry after 30 seconds (auto-created)
dlt.orders-retry-60000      ← retry after 60 seconds (auto-created)
dlt.orders-dlt              ← dead letter topic (auto-created)
```

Topics suffixed with the delay in milliseconds.
All created automatically by Spring Kafka on first message failure.

---

## Project Structure

```
kafka-dlt/
└── src/main/
    ├── java/com/queuedockyard/kafkadlt/
    │   ├── config/
    │   │   └── KafkaConfig.java           ← RetryTopicConfiguration bean
    │   ├── consumer/
    │   │   └── OrderConsumer.java         ← @KafkaListener + @DltHandler
    │   ├── controller/
    │   │   └── OrderController.java       ← REST triggers
    │   ├── model/
    │   │   └── OrderEvent.java            ← message payload
    │   ├── service/
    │   │   └── OrderPublisherService.java ← publishes normal and failing orders
    │   └── KafkaDltApplication.java
    └── resources/
        └── application.yml
```

---

## How to Run

```bash
# Kafka must be running
docker compose -f docker/kafka-compose.yml up -d

# start the app
cd phase-2-kafka-basics/kafka-dlt
mvn spring-boot:run
```

App starts on port **8091**.

---

## Experiments

### Experiment 1 — Normal order (success path)

```bash
curl -X POST http://localhost:8091/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"CUST-001","amount":1500.00}'
```

Expected logs:
```
ORDER CONSUMER | topic: dlt.orders | orderId: ORD-XXX | attempt: processing
ORDER CONSUMER | SUCCESS | orderId: ORD-XXX
```

---

### Experiment 2 — Failing order (full retry + DLT flow)

```bash
curl -X POST http://localhost:8091/api/orders/failing \
  -H "Content-Type: application/json" \
  -d '{"customerId":"CUST-001","amount":999.00}'
```

Watch the logs over the next ~100 seconds:

```
t=0s:   ORDER CONSUMER | FAILED | routing to retry topic
t=10s:  ORDER CONSUMER | topic: dlt.orders-retry-10000 | FAILED
t=40s:  ORDER CONSUMER | topic: dlt.orders-retry-30000 | FAILED
t=100s: ORDER CONSUMER | topic: dlt.orders-retry-60000 | FAILED
        DLT HANDLER | message permanently failed | ALERT
```

---

### Experiment 3 — Verify in Kafka UI

Open http://localhost:8080 while running Experiment 2.
Watch the retry topics appear automatically as messages fail.
You will see messages flowing:
dlt.orders → dlt.orders-retry-0 → dlt.orders-retry-1
→ dlt.orders-retry-2 → dlt.orders-dlt

---

## Retry Delays — Why They Matter

```
Retry 1 after 10 seconds:
  Most transient failures resolve quickly.
  Database blip, momentary network issue.

Retry 2 after 30 seconds:
  Downstream service restart, brief outage.

Retry 3 after 60 seconds:
  Extended outage — giving the system maximum recovery time.

DLT after all retries:
  Permanent failure — requires human investigation.
  Bad data, logic bug, incompatible message format.
```

Increasing delays prevent overwhelming a struggling downstream
service with immediate retries — known as the thundering herd problem.

---

## Production Checklist

- [ ] Set retry delays based on your downstream SLA
- [ ] Alert ops team when messages arrive in DLT
- [ ] Store DLT messages in a database — not just logs
- [ ] Build a replay endpoint — reprocess after fixing root cause
- [ ] Monitor DLT message rate via Prometheus + Grafana
- [ ] Never auto-retry from DLT — always require human review first
- [ ] Set max retention on DLT topic (e.g. 7 days)

---

## DLT vs RabbitMQ DLQ — Summary

|                  | RabbitMQ DLQ                    | Kafka DLT                            |
|------------------|---------------------------------|--------------------------------------|
| Routing          | Broker handles automatically    | Consumer code handles                |
| Blocking         | Main queue blocked during retry | Main topic never blocked             |
| Retry delay      | TTL on retry queue              | Separate retry topics with delay     |
| Built-in support | x-dead-letter-exchange          | Spring Kafka RetryTopicConfiguration |
| Replay           | Manual from DLQ                 | Consume from DLT topic               |
| Visibility       | RabbitMQ Management UI          | Kafka UI / consume DLT topic         |

---

## Configuration

| Property                  | Value            | Description           |
|---------------------------|------------------|-----------------------|
| `app.kafka.topics.orders` | `dlt.orders`     | Main topic            |
| `app.kafka.topics.dlt`    | `dlt.orders-dlt` | Dead letter topic     |
| Max attempts              | 4                | 1 initial + 3 retries |
| Retry delay               | 10s / 30s / 60s  | Increasing backoff    |
| `server.port`             | `8091`           | HTTP port             |