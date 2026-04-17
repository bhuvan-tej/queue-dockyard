# 📦 order-producer

A Spring Boot application that acts as a Kafka producer, simulating an Order Service that publishes order events to Kafka.

This is the first half of the Phase 1.
The `notification-consumer` reads what this app publishes.

---

## What This App Does
```
HTTP POST /api/orders
↓
OrderController  →  builds OrderEvent
↓
OrderProducerService  →  publishes to Kafka
↓
Kafka topic: order-events  (3 partitions)
↓
notification-consumer picks it up
```
---

## Key Concepts Demonstrated

### Message Key
Every message is sent with `orderId` as the Kafka key.
Kafka uses the key to decide which partition to write to.
Same key → same partition → ordering guaranteed for that order.

### Async Send
`KafkaTemplate.send()` is non-blocking — it returns a `CompletableFuture`.
The app does not wait for Kafka to confirm. The callback fires when Kafka responds.
This is why Kafka producers are extremely fast.

### Topic Auto-Creation
`KafkaProducerConfig` registers a `NewTopic` bean.
Spring Kafka creates the topic on startup if it does not already exist.
In production, you would create topics via CLI or Terraform — never auto-create.

---

## How to Run

### Prerequisites
Kafka must be running before starting this app.

```bash
# from repo root
docker compose -f docker/kafka-compose.yml up -d
```

### Start the app
```bash
cd phase-1-kafka-basics/order-producer
mvn spring-boot:run
```

App starts on port **8081**.

---

## How to Test

Once the app is running, send a POST request:

```bash
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId": "CUST-001", "amount": 1500.00}'
```

Expected response:
Order placed successfully | orderId: <generated-uuid>

Then open **Kafka UI** at http://localhost:8080 and check the `order-events` topic.
You should see your message sitting in one of the 3 partitions.

---

## Configuration

| Property                         | Value            | Description                                       |
|----------------------------------|------------------|---------------------------------------------------|
| `spring.kafka.bootstrap-servers` | `localhost:9092` | Kafka broker address                              |
| `spring.kafka.producer.acks`     | `all`            | Wait for all replicas to confirm write            |
| `spring.kafka.producer.retries`  | `3`              | Retry up to 3 times on failure                    |
| `app.kafka.topic.orders`         | `order-events`   | Topic this producer writes to                     |
| `server.port`                    | `8081`           | HTTP port — avoids conflict with Kafka UI on 8080 |

---

## What to Observe

After sending a request, check your IntelliJ console logs.
You should see something like:

Order placed          | orderId: abc-123 | customerId: CUST-001
Order event published | orderId: abc-123 | topic: order-events | partition: 2 | offset: 0

Notice the partition number — send 5 more requests and watch
how Kafka distributes messages across partitions 0, 1, and 2.