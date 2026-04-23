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
     ┌──────────┼──────────┐
     ▼          ▼          ▼
   Kafka     RabbitMQ      SQS
     │          │          │
     │          │          ▼
     │          │    Invoice Generator
     │          │
     │      ┌───┴───┐
     │      ▼       ▼
     │    Email     SMS
     │
 ┌───┴────┐
 ▼        ▼
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
Stamped on every Kafka message, RabbitMQ message, and SQS message.
Any consumer seeing the same `messageId` twice can safely skip.

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

---

## Project Structure

```
ecommerce/
└── src/main/
     ├── java/com/queuedockyard/ecommerce/
     │   ├── config/
     │   │   ├── KafkaConfig.java           ← topic, manual ACK factory
     │   │   ├── RabbitMQConfig.java        ← fanout exchange, email+sms queues
     │   │   ├── SqsConfig.java             ← SQS client → LocalStack
     │   │   └── SqsQueueInitializer.java   ← creates invoice queue on startup
     │   ├── consumer/
     │   │   ├── InventoryConsumer.java     ← Kafka, inventory-group
     │   │   ├── AnalyticsConsumer.java     ← Kafka, analytics-group
     │   │   ├── EmailConsumer.java         ← RabbitMQ, email queue
     │   │   ├── SmsConsumer.java           ← RabbitMQ, sms queue
     │   │   └── InvoiceConsumer.java       ← SQS polling loop
     │   ├── controller/
     │   │   └── OrderController.java       ← POST /api/orders
     │   ├── model/
     │   │   └── OrderEvent.java            ← shared event model
     │   ├── service/
     │   │   └── OrderEventPublisher.java   ← publishes to all three systems
     │   └── EcommerceApplication.java
     └── resources/
         └── application.yml
```

---

## How to Run

Start all infrastructure first:

```bash
# terminal 1 — Kafka
docker compose -f docker/kafka-compose.yml up -d

# terminal 2 — RabbitMQ
docker compose -f docker/rabbit-compose.yml up -d

# terminal 3 — LocalStack
docker compose -f docker/localstack-compose.yml up -d

# verify all are healthy
docker ps
```

Then start the app:

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

Within 3 seconds of placing the order:
```
OrderEventPublisher | Publishing order event | messageId: abc-123
KAFKA    | published | partition: 1 | offset: 0
RABBITMQ | published to fanout exchange
SQS      | published to invoice queue

INVENTORY | received | items: Laptop Stand, USB Hub, Mousepad
INVENTORY | deducting stock...

ANALYTICS | received | revenue: 2500.0 | customer: CUST-001
ANALYTICS | recording...

EMAIL | sending confirmation | to: customer@example.com
EMAIL | sent and ACKed
SMS | sending | to: +91-9999999999 | message: 'Order ORD-001 placed...'
SMS | sent and ACKed

INVOICE | generating | invoiceNo: INV-ORD-001 | amount: ₹2500.0
INVOICE | generated and deleted from SQS
```

All five consumers respond to a single order placement.

---

## Configuration

| Property                     | Value                              | Description        |
|------------------------------|------------------------------------|--------------------|
| `app.kafka.topics.orders`    | `ecommerce.order.events`           | Shared Kafka topic |
| `app.rabbitmq.exchange`      | `ecommerce.notifications.exchange` | Fanout exchange    |
| `aws.sqs.invoice-queue-name` | `invoice-queue`                    | SQS invoice queue  |
| `server.port`                | `8088`                             | HTTP port          |