# ☠️ dead-letter (DLQ)

Demonstrates the Dead Letter Queue (DLQ) pattern in RabbitMQ —
the standard solution for handling poison messages in production.

---

## The Problem — Poison Messages

Without a DLQ, a single failing message can block your queue forever:
```
Normal queue → consumer receives message → processing fails → NACK → requeued  
                                                                    ↓  
                                                           consumer receives again  
                                                                    ↓  
                                                           fails again → NACK → requeued  
                                                                    ↓  
                                                           🔁 infinite loop  
                                                           🚨 queue gets blocked ← production incident
```

---

## The Solution — Retry Limit + DLQ

```
Queue → processing fails → retry 1 → retry 2 → retry 3  
      → max retries exceeded  
      → NACK (requeue=false)  
      → dead-lettered to DLQ exchange  
      → DLQ queue → DLQ Consumer  
      → alert team / investigate / replay after fix
```

Main queue unblocked. Failed message safely stored. Team notified.

---

## How DLQ Routing Works in RabbitMQ

The main queue is configured with two special arguments:
```
x-dead-letter-exchange:     orders.dlq.exchange
x-dead-letter-routing-key:  order.process.dlq
```

When a consumer sends `basicNack(deliveryTag, false, false)` —
the `false` at the end means **do not requeue** —
RabbitMQ automatically forwards the message to the DLQ exchange
using the routing key specified above.

No extra code needed. The broker handles the routing.

---

## The Critical Line

```
// requeue=true  → back to main queue (retry)
channel.basicNack(deliveryTag, false, true);

// requeue=false → dead-letter to DLQ (give up)
channel.basicNack(deliveryTag, false, false);
```

That single boolean is the difference between retrying and dead-lettering.

---

## Project Structure

```
dead-letter/
└── src/main/
     ├── java/com/queuedockyard/deadletter/
     │   ├── config/
     │   │   └── RabbitMQConfig.java       ← main queue with DLQ args, DLQ exchange + queue
     │   ├── consumer/
     │   │   ├── OrderConsumer.java        ← retry logic, dead-letters after max retries
     │   │   └── DlqConsumer.java          ← receives failed messages, alerts, stores
     │   ├── controller/
     │   │   └── OrderController.java      ← trigger normal and failing orders, inspect DLQ
     │   ├── model/
     │   │   └── OrderMessage.java         ← message payload with retryCount + shouldFail
     │   ├── service/
     │   │   └── OrderPublisherService.java ← publishes normal and failing orders
     │   └── DeadLetterApplication.java
     └── resources/
         └── application.yml
```

---

## How to Run

```bash
# start RabbitMQ (if not already running)
docker compose -f docker/rabbit-compose.yml up -d

# start the app
cd phase-4-rabbitmq/dead-letter
mvn spring-boot:run
```

App starts on port **8086**.

---

## Experiments

### Experiment 1 — Normal order (success path)

```bash
curl -X POST http://localhost:8086/api/orders \
  -H "Content-Type: application/json" \
  -d '{"orderId":"ORD-001","customerId":"CUST-001","amount":1500.00}'
```

Expected logs:
```
ORDER CONSUMER | attempt 1 of 3 | messageId: ...
ORDER CONSUMER | SUCCESS | ACK sent
```

---

### Experiment 2 — Failing order (retry + DLQ path)

```bash
curl -X POST http://localhost:8086/api/orders/failing \
  -H "Content-Type: application/json" \
  -d '{"orderId":"ORD-BAD-001","customerId":"CUST-001","amount":999.00}'
```

Expected logs:
```
ORDER CONSUMER | attempt 1 of 3 | FAILED | will retry
ORDER CONSUMER | attempt 2 of 3 | FAILED | will retry
ORDER CONSUMER | attempt 3 of 3 | MAX RETRIES EXCEEDED | sending to DLQ
DLQ CONSUMER   | message arrived in DLQ | ALERT
DLQ CONSUMER   | stored for manual review | total in DLQ store: 1
```
---

### Experiment 3 — Inspect the DLQ store

```bash
curl http://localhost:8086/api/orders/dlq
```

Returns all messages that ended up in the DLQ.
In production this would be a database query for the ops team.

---

### Experiment 4 — Watch in RabbitMQ UI

Open http://localhost:15672 while running experiments.
You will see:
- `orders.queue` — message count spikes then drops as retries happen
- `orders.dlq.queue` — message count grows as failing messages arrive
- Consumer connections and message rates in real time

---

## Configuration

| Property                                   | Value              | Description                        |
|--------------------------------------------|--------------------|------------------------------------|
| `app.rabbitmq.main.max-retries`            | `3`                | Attempts before dead-lettering     |
| `app.rabbitmq.main.queue`                  | `orders.queue`     | Main processing queue              |
| `app.rabbitmq.dlq.queue`                   | `orders.dlq.queue` | Dead letter queue                  |
| `spring.rabbitmq.listener.simple.prefetch` | `1`                | One message at a time per consumer |
| `server.port`                              | `8086`             | HTTP port                          |

---

## Production Checklist

When implementing DLQ in production:

- [ ] Set sensible max-retries (3-5 is typical)
- [ ] Alert ops team when messages arrive in DLQ (PagerDuty, Slack)
- [ ] Store DLQ messages in a database — not in memory
- [ ] Build a replay endpoint — reprocess after fixing the root cause
- [ ] Monitor DLQ message count — spike means something is broken
- [ ] Never auto-retry from DLQ — always require human investigation first