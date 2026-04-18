# 🔀 multi-partition-demo

Demonstrates two of Kafka's most powerful built-in behaviors:
1. **Partition-based message routing** — same key always → same partition
2. **Consumer group rebalancing** — Kafka automatically redistributes
   partitions when consumers join or leave the group

This is a hands-on observability demo.
The real understanding happens in the logs, not the code.

---

## What This App Does

```
POST /api/inventory/batch
↓
Publishes 9 events (3 products × 3 events each)
↓
Kafka routes by key (productId) → same product → same partition
↓
Consumer threads receive events — each thread owns one partition
```

---

## Key Concepts Demonstrated

### Message Key → Partition Routing
Kafka hashes the message key to decide which partition to write to.
Using `productId` as the key guarantees that all events for the
same product always land on the same partition — preserving order.

```
PROD-001 → hash → partition 0 (always)
PROD-002 → hash → partition 1 (always)
PROD-003 → hash → partition 2 (always)
```

### Consumer Group Rebalancing
When a consumer joins or leaves a group, Kafka triggers a rebalance —
redistributing partition ownership across active consumers.
This is automatic. You write zero failover code.

```
Before kill:   Instance1→P0   Instance2→P1   Instance3→P2
After kill:    Instance1→P0,P2               Instance2→P1
```

---

## Experiments

### Experiment 1 — Partition distribution (single instance)

```bash
# start Kafka
docker compose -f docker/kafka-compose.yml up -d

# start the app
mvn spring-boot:run

# trigger batch publish
curl -X POST http://localhost:8083/api/inventory/batch
```

Watch the logs — notice PROD-001 always appears on the same partition.

---

### Experiment 2 — Consumer group rebalancing (3 instances)

Open 3 separate terminals:

```bash
# terminal 1
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8083

# terminal 2
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8084

# terminal 3
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8085
```

Watch the logs — each instance logs which partition it was assigned.
Then kill terminal 3 (Ctrl+C) and watch the rebalance happen in terminals 1 and 2.

---

### Experiment 3 — What happens with more consumers than partitions?

Start a 4th instance on port 8086.
That instance will sit idle — Kafka has no partition to give it.
This is why choosing the right partition count upfront matters.

---

## Configuration

| Property                                  | Value                  | Description                          |
|-------------------------------------------|------------------------|--------------------------------------|
| `spring.kafka.consumer.group-id`          | `partition-demo-group` | Shared group for all instances       |
| `spring.kafka.consumer.auto-offset-reset` | `earliest`             | Read from beginning on first run     |
| `app.kafka.topic.inventory`               | `inventory-events`     | Topic with 3 partitions              |
| `server.port`                             | `8083`                 | Default port — override per instance |