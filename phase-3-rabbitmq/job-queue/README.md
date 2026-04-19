# 📬 job-queue-demo

Demonstrates RabbitMQ's three most important exchange types —
Direct, Fanout, and Topic — and how each routes messages differently.

Also demonstrates manual ACK and fair worker dispatch using prefetch.

---

## RabbitMQ vs Kafka — The Key Difference

```
Kafka:   Producer → Topic → Consumer
Rabbit:  Producer → Exchange → Queue(s) → Consumer
```

In RabbitMQ you never publish directly to a queue.
You publish to an Exchange with a routing key.
The Exchange applies its routing rules and delivers to the right queue(s).

---

## Exchange Types

### Direct — exact match routing

```
Producer → "order.process" → DirectExchange → direct.orders.queue → Worker
```

One message → one queue → one worker processes it.

Use for: task queues, background jobs, work distribution.

---

### Fanout — broadcast to all

```
Producer → FanoutExchange → email.queue → Email Service  
                          → sms.queue   → SMS Service  
                          → push.queue  → Push Service
```

One message → all bound queues → all consumers receive it independently.

Use for: notifications, broadcasting events to multiple services.

---

### Topic — pattern-based routing

```
Routing key → pattern match → queue → consumer

"order.placed"    → matches "order.*"   → orders.queue   → Order Consumer
"payment.success" → matches "payment.*" → payments.queue → Payment Consumer
"inventory.low"   → no match            → (dropped)
```

Wildcard rules:
- `*` matches exactly one word
- `#` matches zero or more words

Use for: selective event distribution, microservice event buses.

---

## How to Run

```bash
# start RabbitMQ
docker compose -f docker/rabbit-compose.yml up -d

# start the app
cd phase-4-rabbitmq/job-queue-demo
mvn spring-boot:run
```

App starts on port **8085**.
RabbitMQ Management UI: http://localhost:15672 (guest/guest)

---

## Experiments

### Experiment 1 — Direct exchange
```bash
curl -X POST http://localhost:8085/api/jobs/direct \
  -H "Content-Type: application/json" \
  -d '{"payload": "Process order ORD-001"}'
```
Watch logs — only `DirectOrderConsumer` receives it.

---

### Experiment 2 — Fanout exchange
```bash
curl -X POST http://localhost:8085/api/jobs/fanout \
  -H "Content-Type: application/json" \
  -d '{"payload": "User registered: user@example.com"}'
```
Watch logs — all three fanout consumers (email, sms, push) receive it.
Same messageId in all three log lines — same message, three queues.

---

### Experiment 3 — Topic exchange
```bash
# goes to orders consumer only
curl -X POST http://localhost:8085/api/jobs/topic \
  -H "Content-Type: application/json" \
  -d '{"routingKey": "order.placed", "payload": "New order placed"}'

# goes to payments consumer only
curl -X POST http://localhost:8085/api/jobs/topic \
  -H "Content-Type: application/json" \
  -d '{"routingKey": "payment.success", "payload": "Payment received"}'

# goes to NOBODY — no pattern matches "inventory.*"
curl -X POST http://localhost:8085/api/jobs/topic \
  -H "Content-Type: application/json" \
  -d '{"routingKey": "inventory.low", "payload": "Stock running low"}'
```

---

### Experiment 4 — Watch in RabbitMQ UI
Open http://localhost:15672 while sending messages.
You can see: queues, message counts, consumer connections, and message rates.
This is the equivalent of Kafka UI for RabbitMQ.

---

## Configuration

| Property                                           | Value       | Description                                      |
|----------------------------------------------------|-------------|--------------------------------------------------|
| `spring.rabbitmq.host`                             | `localhost` | RabbitMQ broker address                          |
| `spring.rabbitmq.port`                             | `5672`      | AMQP protocol port                               |
| `spring.rabbitmq.listener.simple.acknowledge-mode` | `manual`    | Explicit ACK required                            |
| `spring.rabbitmq.listener.simple.prefetch`         | `1`         | Fair dispatch — one message at a time per worker |
| `server.port`                                      | `8085`      | HTTP port                                        |