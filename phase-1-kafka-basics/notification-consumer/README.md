# 🔄 notification-consumer

A Spring Boot application that acts as a Kafka consumer, listening to the `order-events` topic and simulating
email and SMS notifications when an order event arrives.

This is the second half of the Phase 2 demo.
It reads what `order-producer` publishes.

---

## What This App Does

```
Kafka topic: order-events
↓
OrderEventConsumer  →  receives ConsumerRecord
↓
NotificationService  →  processes event
↓
Simulated email + SMS logs
↓
Event stored in memory → inspectable via REST
```

---

## Key Concepts Demonstrated

### Consumer Group
This app uses `group-id: notification-group`.
If you run two instances of this app, Kafka splits the
3 partitions between them — each instance handles some partitions.
This is automatic horizontal scaling with zero code changes.

### ConsumerRecord vs Direct Value
The listener uses `ConsumerRecord<String, OrderEvent>` instead of
just `OrderEvent` directly. This gives access to partition number
and offset — useful for observing Kafka behavior while learning.

### Separation of Concerns
`OrderEventConsumer` only handles Kafka wiring.
`NotificationService` only handles business logic.
This makes both independently testable.

### Trusted Packages
The `spring.json.trusted.packages` config tells the JsonDeserializer
which packages are safe to deserialize into Java objects.
Without this, Kafka will refuse to deserialize the message as a
security measure against arbitrary class instantiation.

---

## How to Run

### Prerequisites
Both Kafka and order-producer must be running first.

```bash
# terminal 1 — start Kafka
docker compose -f docker/kafka-compose.yml up -d

# terminal 2 — start producer
cd phase-2-kafka-basics/order-producer
mvn spring-boot:run

# terminal 3 — start consumer
cd phase-2-kafka-basics/notification-consumer
mvn spring-boot:run
```

App starts on port **8082**.

---

## How to Test

### Step 1 — send an order from the producer
```bash
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId": "CUST-001", "amount": 1500.00}'
```

### Step 2 — confirm the consumer received it
```bash
curl http://localhost:8082/api/notifications
```

### Step 3 — watch the logs
In the consumer's IntelliJ console you should see:

```
Received order event  | partition: 2 | offset: 0  | key: abc-123
Processing order event | orderId: abc-123 | status: PLACED | ...
EMAIL sent → customer: CUST-001 | subject: 'Order abc-123 PLACED'
SMS sent   → customer: CUST-001 | message: 'Your order abc-123 has been PLACED'
```

---

## Experiments to Try

| Experiment             | How                                                 | What to observe                            |
|------------------------|-----------------------------------------------------|--------------------------------------------|
| Partition distribution | Send 9 orders                                       | Messages spread across partitions 0, 1, 2  |
| Consumer group scaling | Run 2 instances of this app                         | Each instance owns some partitions         |
| Replay messages        | Restart consumer with `auto-offset-reset: earliest` | All past messages reprocessed              |
| Consumer lag           | Stop consumer, send 10 orders, restart              | Consumer catches up from where it left off |

---

## Configuration

| Property                                  | Value                | Description                                |
|-------------------------------------------|----------------------|--------------------------------------------|
| `spring.kafka.bootstrap-servers`          | `localhost:9092`     | Kafka broker address                       |
| `spring.kafka.consumer.group-id`          | `notification-group` | Consumer group identifier                  |
| `spring.kafka.consumer.auto-offset-reset` | `earliest`           | Start from beginning if no offset exists   |
| `app.kafka.topic.orders`                  | `order-events`       | Topic this consumer reads from             |
| `server.port`                             | `8082`               | HTTP port for the REST inspection endpoint |