# kafka-streams

Demonstrates Apache Kafka Streams — continuous stream processing
pipelines that run inside a Spring Boot application.

---

## What is Kafka Streams?

### What you built before — Consumer/Producer model

```
Producer → Kafka Topic → Consumer
```

- Your consumer reads events and does something with them.
- Processing is stateless — each message handled independently.
- No transformation, no aggregation, no state between messages.

---

### What Kafka Streams adds — Stream Processing

```
Input Topic → Transform / Aggregate / Join → Output Topic
```

Kafka Streams lets you write continuous data pipelines.
Instead of just consuming events, you transform them in real time.

The pipeline runs inside your Spring Boot app.
No separate infrastructure beyond Kafka itself.

---

### KStream vs KTable

**KStream** — an unbounded stream of individual events:
```
Each record is an independent event
order1 → order2 → order3 → order4 → ...
```

**KTable** — a continuously updated table (latest value per key):
```
CUST-001 → { totalOrders: 3 }   ← updates as new orders arrive
CUST-002 → { totalOrders: 1 }
```

Think of KStream as an append-only log.
Think of KTable as a database table that auto-updates.

---

### What is a Topology?

A topology is the pipeline you define:
```
Source (read from topic)
↓
Processor (filter / map / aggregate)
↓
Processor (another transformation)
↓
Sink (write to topic)
```

You define the topology once.
Kafka Streams runs it continuously and handles:
- Fault tolerance (restarts on failure)
- Scaling (distribute across instances)
- State management (RocksDB local store)

---

## Three Pipelines in This App

All three read from the same `order-events` input topic:

### Pipeline 1 — Order Filter
```
order-events
→ filter(status == "PLACED")
→ placed-order-events
```
Only PLACED orders pass through.
CONFIRMED, CANCELLED, SHIPPED are dropped.

**Why this matters in production:**
Downstream services that only care about new orders
consume `placed-order-events` instead of filtering themselves.
Filtering logic lives in one place — not duplicated everywhere.

---

### Pipeline 2 — Customer Order Counter (KTable)
```
order-events
→ selectKey(customerId)    ← rekey from orderId to customerId
→ groupByKey               ← group all events by customerId
→ count()                  ← running count per customer
→ KTable (state store)     ← queryable without consuming a topic
→ customer-order-counts    ← output topic
```

Produces a continuously updated count per customer.
State stored locally in RocksDB — queryable via REST instantly.

**Why this matters in production:**
"How many orders has CUST-001 placed?" answered in microseconds
from local state — no database query, no Kafka consumer needed.

---

### Pipeline 3 — Revenue per Minute (Windowed Aggregation)
```
order-events
→ selectKey("global")      ← single group for all orders
→ groupByKey
→ windowedBy(1 minute)     ← split into 1-minute buckets
→ aggregate(sum amounts)   ← total revenue per bucket
→ revenue-per-minute       ← output topic
```

Aggregates total revenue within each 1-minute time window.

**Why this matters in production:**
"What was revenue between 2:00pm and 2:01pm?" — this is exactly
how real-time revenue dashboards work at companies like Swiggy,
Zomato, Amazon. The windowed aggregation IS the dashboard data.

---

## Project Structure

```
kafka-streams/
└── src/main/
     ├── java/com/queuedockyard/kafkastreams/
     │   ├── config/
     │   │   └── KafkaStreamsConfig.java      ← @EnableKafkaStreams, topic creation
     │   ├── controller/
     │   │   └── StreamController.java        ← REST triggers + state store query
     │   ├── model/
     │   │   ├── OrderEvent.java              ← input event model
     │   │   └── RevenueWindow.java           ← Pipeline 3 output model
     │   ├── service/
     │   │   └── OrderEventPublisher.java     ← publishes test events
     │   ├── topology/
     │   │   └── OrderStreamTopology.java     ← all three pipeline definitions
     │   └── KafkaStreamsApplication.java
     └── resources/
          └── application.yml
```

---

## Key Concepts Explained

### application-id vs group-id

```yaml
# Regular Kafka consumer
spring.kafka.consumer.group-id: my-group

# Kafka Streams
spring.kafka.streams.application-id: my-streams-app
```

Both serve the same purpose — identifying a group of instances
that share the work. Kafka distributes partitions across them.
If you run two instances of this app, they split the partitions.

---

### selectKey — why it matters

```
// WRONG — key is orderId, grouping by orderId = useless
stream.groupByKey().count()

// RIGHT — rekey to customerId first, then count per customer
stream.selectKey((orderId, event) -> event.getCustomerId())
      .groupByKey()
      .count()
```

In Kafka Streams, aggregations (count, sum, reduce) always
work on the KEY. You must selectKey to the field you want
to aggregate by before calling groupByKey.

---

### State Store — RocksDB

When you call `.count()` or `.aggregate()`, Kafka Streams
stores the running state in a local RocksDB database.

RocksDB is embedded — no separate database to run.
It lives on the same machine as your app.
```
CUST-001 → 4    ← stored in RocksDB on your local disk
CUST-002 → 2
CUST-003 → 1
```

Interactive queries let you read this state via REST
without touching Kafka at all — sub-millisecond lookups.

If the app crashes and restarts, state is rebuilt from
the Kafka changelog topic automatically.

---

### Tumbling Windows vs Hopping Windows
```
Tumbling (used here):
Non-overlapping, fixed-size buckets
|-- window 1 --|-- window 2 --|-- window 3 --|
Each event belongs to exactly ONE window

Hopping:
Overlapping windows — event can appear in multiple windows
|-- window 1 --|
|-- window 2 --|
|-- window 3 --|
Used for sliding averages
```

---

## How to Run

```bash
# Kafka must be running
docker compose -f docker/kafka-compose.yml up -d

# start the app
cd phase-3-kafka-internals/kafka-streams
mvn spring-boot:run
```

App starts on port **8089**.

---

## Experiments

### Experiment 1 — Filter pipeline

```bash
# PLACED — passes through Pipeline 1
curl -X POST http://localhost:8089/api/streams/order \
  -H "Content-Type: application/json" \
  -d '{"customerId":"CUST-001","amount":1500.00,"status":"PLACED"}'

# CONFIRMED — filtered out by Pipeline 1
curl -X POST http://localhost:8089/api/streams/order \
  -H "Content-Type: application/json" \
  -d '{"customerId":"CUST-001","amount":800.00,"status":"CONFIRMED"}'
```

Watch logs:
```
FILTER PIPELINE | passed  | orderId: ORD-XXX  ← PLACED goes through
FILTER PIPELINE | filtered out | status: CONFIRMED  ← CONFIRMED dropped
```

---

### Experiment 2 — Customer counter + state store query

```bash
# publish the batch (9 orders across 4 customers)
curl -X POST http://localhost:8089/api/streams/batch

# query state store — no Kafka consumer needed
curl http://localhost:8089/api/streams/customer/CUST-001
# returns: { "totalOrders": 4 }

curl http://localhost:8089/api/streams/customer/CUST-002
# returns: { "totalOrders": 2 }
```

---

### Experiment 3 — Revenue windowing

```bash
# send several orders within 1 minute
for i in 1 2 3 4 5; do
  curl -s -X POST http://localhost:8089/api/streams/order \
    -H "Content-Type: application/json" \
    -d "{\"customerId\":\"CUST-00$i\",\"amount\":$((i*1000)).00,\"status\":\"PLACED\"}"
  sleep 5
done
```

Watch logs — after 1 minute the window closes and you see:

```
REVENUE PIPELINE | window complete | revenue: 15000.0
```
---

## Configuration

| Property                              | Value                           | Description                |
|---------------------------------------|---------------------------------|----------------------------|
| `spring.kafka.streams.application-id` | `kafka-streams-order-processor` | Stream processing group ID |
| `app.kafka.topics.orders-input`       | `order-events`                  | Input topic                |
| `app.kafka.topics.placed-orders`      | `placed-order-events`           | Pipeline 1 output          |
| `app.kafka.topics.customer-counts`    | `customer-order-counts`         | Pipeline 2 output          |
| `app.kafka.topics.revenue-per-minute` | `revenue-per-minute`            | Pipeline 3 output          |
| `server.port`                         | `8089`                          | HTTP port                  |

---

## Kafka Streams vs Regular Consumer — When to Use What

| Need                                 | Use                    |
|--------------------------------------|------------------------|
| Simple event consumption             | Regular @KafkaListener |
| Filter events to another topic       | Kafka Streams          |
| Count / sum / aggregate              | Kafka Streams          |
| Join two topics                      | Kafka Streams          |
| Time-windowed aggregation            | Kafka Streams          |
| Real-time dashboard data             | Kafka Streams          |
| Enriching events from a lookup table | Kafka Streams          |