# 🛒 ecommerce — Capstone

The final project — a production-style e-commerce event pipeline
that uses all three messaging systems for what each does best.

---

## 🏗️ Architecture

```
Customer → POST /api/orders
                │
                ▼
          Order Producer
                │
     ┌──────────┼───────────┐
     ▼          ▼           ▼
   Kafka     RabbitMQ      SQS
     │          │           │
     │          │           ▼
     │          │    Invoice Generator
     │          │
     │      ┌───┴───┐
     │      ▼       ▼
     │    Email    SMS
     │
 ┌───┴───────┐
 ▼           ▼
Inventory Analytics
Consumer  Consumer
 (inv)     (ana)
```

---

## Why Each System Is Used Here

| System   | Consumer          | Reason                                       |
|----------|-------------------|----------------------------------------------|
| Kafka    | Inventory Service | High volume, needs replay capability         |
| Kafka    | Analytics Service | Same events, completely independent group    |
| RabbitMQ | Email Service     | Job queue — deliver once, process once       |
| RabbitMQ | SMS Service       | Independent from email via fanout exchange   |
| SQS      | Invoice Generator | AWS-native pattern, serverless in production |

---

## Patterns Demonstrated

### Idempotency across all systems
One `messageId` (UUID) generated at order placement.
Stamped on every Kafka, RabbitMQ, and SQS message.
Stored in Redis after processing — survives restarts.
Any consumer seeing the same `messageId` twice skips processing.

### Consumer group independence (Kafka)
`InventoryConsumer` uses `ecommerce-inventory-group`.
`AnalyticsConsumer` uses `ecommerce-analytics-group`.
Both receive every order event — neither knows the other exists.

### Fanout independence (RabbitMQ)
`EmailConsumer` and `SmsConsumer` are bound to the same fanout exchange.
If email sending fails, SMS is unaffected — separate queues.

### Pull-based SQS vs push-based Kafka/RabbitMQ
Kafka and RabbitMQ push messages to consumers.
SQS requires consumers to poll — `InvoiceConsumer` runs a polling loop.
In production, AWS Lambda handles this automatically.

### Dead Letter Topic — inventory consumer (Addition 3)
The inventory consumer has non-blocking retry with DLT.
A simulated warehouse system failure triggers:
retry 1 (5s) → retry 2 (15s) → retry 3 (30s) → DLT
Main topic never blocked — other orders keep processing.

---

## Project Structure

```
ecommerce/
└── src/main/
    ├── java/com/queuedockyard/ecommerce/
    │   ├── config/
    │   │   ├── KafkaConfig.java           ← topics, producer, consumer factory
    │   │   ├── RabbitMQConfig.java        ← fanout exchange, email+sms queues
    │   │   ├── SqsConfig.java             ← SQS client → LocalStack
    │   │   └── SqsQueueInitializer.java   ← creates invoice queue on startup
    │   ├── consumer/
    │   │   ├── InventoryConsumer.java     ← Kafka, inventory-group, DLT retry
    │   │   ├── AnalyticsConsumer.java     ← Kafka, analytics-group
    │   │   ├── EmailConsumer.java         ← RabbitMQ, email queue
    │   │   ├── SmsConsumer.java           ← RabbitMQ, sms queue
    │   │   └── InvoiceConsumer.java       ← SQS polling loop
    │   ├── controller/
    │   │   └── OrderController.java       ← POST /api/orders
    │   │   └── InventoryDltController.java ← GET /api/inventory/failed
    │   ├── metrics/
    │   │   └── MetricsService.java        ← Prometheus custom metrics
    │   ├── model/
    │   │   └── OrderEvent.java            ← shared event model
    │   ├── service/
    │   │   └── OrderEventPublisher.java   ← publishes to all three systems
    │   └── store/
    │       └── RedisIdempotencyStore.java ← Redis-backed idempotency
    └── resources/
        └── application.yml
```

---

## How to Run

Start all infrastructure:

```bash
docker compose -f docker/kafka-compose.yml up -d
docker compose -f docker/rabbit-compose.yml up -d
docker compose -f docker/localstack-compose.yml up -d
docker compose -f docker/monitoring-compose.yml up -d
```

Start the app:

```bash
cd capstone/ecommerce
mvn spring-boot:run
```

App starts on port **8088**.

---

## Place an Order

```bash
curl -X POST http://localhost:8088/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORD-001",
    "customerId": "CUST-001",
    "customerEmail": "customer@example.com",
    "customerPhone": "+91-9999999999",
    "amount": 2500.00,
    "items": "Laptop Stand, USB Hub, Mousepad"
  }'
```

## What You Should See in Logs

```
KAFKA    | published | messageId: abc-123 | partition: 1 | offset: 0
RABBITMQ | published | messageId: abc-123
SQS      | published | messageId: abc-123

INVENTORY | received | orderId: ORD-001 | items: Laptop Stand, USB Hub, Mousepad
ANALYTICS | received | amount: 2500.0   | customer: CUST-001
EMAIL     | sending confirmation | to: customer@example.com
SMS       | sending | to: +91-9999999999
INVOICE   | generating | invoiceNo: INV-ORD-001 | amount: ₹2500.0
```

All five consumers respond to a single order placement.

---

## Monitoring

| URL                                       | What it shows                          |
|-------------------------------------------|----------------------------------------|
| http://localhost:8088/actuator/health     | Kafka, RabbitMQ, Redis status          |
| http://localhost:8088/actuator/prometheus | Raw Prometheus metrics                 |
| http://localhost:8080                     | Kafka UI — topics and messages         |
| http://localhost:15672                    | RabbitMQ UI — queues and consumers     |
| http://localhost:3000                     | Grafana — ecommerce pipeline dashboard |

---

## Production Hardening Applied

| Pattern             | Implementation                                     |
|---------------------|----------------------------------------------------|
| Health checks       | Spring Boot Actuator `/actuator/health`            |
| Metrics             | Micrometer + Prometheus at `/actuator/prometheus`  |
| Redis idempotency   | `RedisIdempotencyStore` — survives restarts        |
| Business metrics    | `MetricsService` — counters per consumer           |
| Publish duration    | Timer wrapping all three system publishes          |
| Duplicate detection | Counter incremented on duplicate messageId         |
| Non-blocking retry  | Inventory consumer — retry topics + DLT            |
| Testcontainers      | Integration tests with real Kafka, RabbitMQ, Redis |

---

## Note on Kafka Streams

Kafka Streams stream processing is demonstrated as a standalone
project in `phase-3-kafka-internals/kafka-streams`.

Running Kafka Streams alongside regular Kafka consumers in the
same Spring Boot app requires careful coordination of internal
repartition topic serdes — a production concern that adds
complexity beyond the scope of this capstone.

In production, Kafka Streams applications are typically deployed
as separate services from regular consumers for exactly this reason:
clean separation of concerns, independent scaling, and no serde conflicts.