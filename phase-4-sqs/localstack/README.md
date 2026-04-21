# localstack

Demonstrates AWS SQS using LocalStack — a local AWS cloud emulator.
No AWS account needed. No cost. Works completely offline.

---

## SQS vs Kafka vs RabbitMQ

| Feature           | SQS                  | Kafka                         | RabbitMQ           |
|-------------------|----------------------|-------------------------------|--------------------|
| Infra management  | Managed by AWS       | Self-managed                  | Self-managed       |
| Message retention | Up to 14 days        | Configurable (default 7 days) | Deleted after ACK  |
| Replay            | No                   | Yes                           | No                 |
| Ordering          | FIFO queue only      | Per partition                 | No                 |
| Consumer model    | Pull (polling)       | Pull (offset-based)           | Push               |
| Routing           | Limited              | By topic/partition            | Flexible exchanges |
| Best for          | AWS-native workloads | Event streaming               | Task queues        |

---

## How SQS Works — The Three-Step Pattern

```
Producer sends message
↓
SQS stores message
↓
Consumer polls — message becomes INVISIBLE (visibility timeout starts)
↓
Consumer processes message
↓
Consumer explicitly DELETES message ← critical step
↓
If delete is skipped:
Visibility timeout expires (30s in our config)
↓
Message becomes visible again → redelivered → at-least-once
```

---

## Visibility Timeout — SQS's Delivery Guarantee

When a consumer receives a message, SQS hides it from all other
consumers for the duration of the visibility timeout.

This is SQS's equivalent of:
- RabbitMQ's unACKed message (held until ACK or consumer crash)
- Kafka's uncommitted offset (message redelivered on restart)

If your consumer crashes before deleting:
- Visibility timeout expires
- Message becomes visible again
- Consumer receives and processes it again (at-least-once)

---

## Standard Queue vs FIFO Queue

### Standard Queue
- At-least-once delivery — duplicates possible
- Best-effort ordering — no guarantee
- Nearly unlimited throughput
- Use when: order doesn't matter, maximum speed needed

### FIFO Queue
- Exactly-once processing — built-in deduplication
- Strict ordering within a message group
- Up to 3,000 messages/sec with batching
- Name MUST end with `.fifo`
- Requires MessageGroupId on every send
- Use when: order matters (order lifecycle events, financial transactions)

---

## Project Structure

```
localstack/
└── src/main/
     ├── java/com/queuedockyard/localstack/
     │   ├── config/
     │   │   ├── SqsConfig.java           ← SQS client pointed at LocalStack
     │   │   └── QueueInitializer.java    ← creates queues on startup
     │   ├── controller/
     │   │   └── OrderController.java     ← REST endpoints to send messages
     │   ├── model/
     │   │   └── OrderMessage.java        ← message payload
     │   ├── service/
     │   │   ├── SqsProducerService.java  ← sends to standard and FIFO queues
     │   │   └── SqsConsumerService.java  ← polling loop, process, delete
     │   └── LocalstackApplication.java
     └── resources/
         └── application.yml
```

---

## How to Run

```bash
# start LocalStack
docker compose -f docker/localstack-compose.yml up -d

# verify LocalStack is ready
curl http://localhost:4566/_localstack/health

# start the app
cd phase-5-sqs/localstack
mvn spring-boot:run
```

App starts on port **8087**.
On startup, QueueInitializer creates both queues automatically.

---

## Experiments

### Experiment 1 — Standard queue

```bash
curl -X POST http://localhost:8087/api/orders/standard \
  -H "Content-Type: application/json" \
  -d '{"orderId":"ORD-001","customerId":"CUST-001","amount":1500.00,"paymentMethod":"UPI"}'
```

Watch logs — consumer picks it up within 2 seconds and deletes it.

---

### Experiment 2 — FIFO queue with ordering

Send 3 messages for the same customer in quick succession:

```bash
curl -X POST http://localhost:8087/api/orders/fifo \
  -H "Content-Type: application/json" \
  -d '{"orderId":"ORD-001","customerId":"CUST-001","amount":500.00,"paymentMethod":"UPI"}'

curl -X POST http://localhost:8087/api/orders/fifo \
  -H "Content-Type: application/json" \
  -d '{"orderId":"ORD-002","customerId":"CUST-001","amount":1000.00,"paymentMethod":"UPI"}'

curl -X POST http://localhost:8087/api/orders/fifo \
  -H "Content-Type: application/json" \
  -d '{"orderId":"ORD-003","customerId":"CUST-001","amount":1500.00,"paymentMethod":"UPI"}'
```

Watch logs — ORD-001 processed before ORD-002, ORD-002 before ORD-003.
Ordering guaranteed because all share the same MessageGroupId (CUST-001).

---

### Experiment 3 — Inspect processed messages

```bash
curl http://localhost:8087/api/orders/processed
```

---

## Configuration

| Property                     | Value                   | Description                                |
|------------------------------|-------------------------|--------------------------------------------|
| `aws.endpoint`               | `http://localhost:4566` | LocalStack endpoint                        |
| `aws.region`                 | `us-east-1`             | Any region works with LocalStack           |
| `aws.sqs.visibility-timeout` | `30`                    | Seconds before undeleted message reappears |
| `aws.sqs.wait-time-seconds`  | `5`                     | Long polling duration                      |
| `server.port`                | `8087`                  | HTTP port                                  |

---

## Production Checklist

When moving from LocalStack to real AWS:

- [ ] Remove `endpointOverride` from SqsConfig
- [ ] Replace `StaticCredentialsProvider` with `DefaultCredentialsProvider`
- [ ] Use IAM roles instead of hardcoded credentials
- [ ] Set appropriate visibility timeout (longer than max processing time)
- [ ] Enable SQS dead-letter queue for failed messages
- [ ] Monitor queue depth via CloudWatch alarms
- [ ] Use `spring-cloud-aws` for cleaner `@SqsListener` integration