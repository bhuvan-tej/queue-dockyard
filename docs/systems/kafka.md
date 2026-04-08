# ⚙️ Apache Kafka

## 🚀 What is Kafka?

Apache Kafka is a **distributed event streaming platform**.

It is designed to handle:
 - High-throughput data streams
 - Real-time processing
 - Event-driven architectures

---

## 🧠 Mental Model

 - Think of Kafka like a database table that only supports INSERT and sequential READ.
 - Messages are written to disk and kept for a configurable period (default 7 days).
 - Consumers read at their own pace and track their own position (called an offset).

Because messages are never deleted on read, multiple independent services
can consume the same stream of events — each from their own position.

---

## 🔄 How Kafka Works

> Producer → Topic → Consumer

---

### Components

 - **Producer** → sends messages
 - **Topic** → logical stream of messages
 - **Partition** → splits topic for parallelism
 - **Consumer** → reads messages
 - **Broker** → Kafka server

---

## 🧩 Topics and Partitions

A topic is divided into partitions:
```
Topic: orders

Partition 0 → [msg1] [msg4] [msg7]
Partition 1 → [msg2] [msg5] [msg8]
Partition 2 → [msg3] [msg6] [msg9]
```

### Why Partitions Matter

 - Enable **parallel processing**
 - Improve **throughput**
 - Maintain **ordering within a partition**

---

## 📍 Offsets

Each message has a position called an **offset**.
> [msg1:0] [msg2:1] [msg3:2]

Consumers track offsets to know:
 - What has been read
 - Where to resume

---

## 👥 Consumer Groups

Consumers can be grouped together:

 - Each partition → one consumer in the group
 - Multiple groups → each gets all messages
```
Topic "orders" — 3 partitions
Group A (Inventory Service): Consumer1→P0, Consumer2→P1, Consumer3→P2
Group B (Analytics Service): ConsumerA→P0, ConsumerB→P1, ConsumerC→P2
Both groups receive every message. They are completely independent.
```

---

## ⚡ Key Characteristics

### 1. High Throughput
Kafka can handle **millions of messages per second**.

---

### 2. Message Retention

Messages are stored for a configured time (e.g., 7 days).

 - Consumers can replay old messages
 - Useful for debugging and analytics

---

### 3. Replay Capability

Consumers can:
 - Restart from beginning
 - Reprocess data

---

## 🔥 When to Use Kafka

Use Kafka when:

 - You need **event streaming**
 - Multiple services need the same data
 - You want to **replay events**
 - You handle **large-scale data**

---

## 🧠 Real-World Use Cases

### 1. Event-Driven Microservices
 - Order created → multiple services react

---

### 2. Log Aggregation
 - Collect logs from multiple systems

---

### 3. Real-Time Analytics
 - Stream data to analytics pipelines

---

## ⚠️ When NOT to Use Kafka

Avoid Kafka if:

 - You just need a simple task queue
 - You don’t need message replay
 - Your system is low volume

👉 Use RabbitMQ instead

---

## ⚖️ Kafka vs Traditional Queue

| Feature           | Kafka      | Traditional Queue |
|-------------------|------------|-------------------|
| Message retention | Yes        | No                |
| Replay            | Yes        | No                |
| Model             | Stream/log | Queue             |
| Throughput        | Very high  | Moderate          |

---

## 🧭 Mental Model Recap

 - Kafka = **Event Stream**
 - Data flows continuously
 - Consumers read independently

---

## 💡 Insights

Kafka is not just a queue.

It is a **streaming platform**.

If you treat Kafka like a queue:
 - You lose its advantages
 - You complicate your system

---

## 🏁 Final Takeaway

Use Kafka when:
 - Data needs to be shared across multiple systems
 - Events matter over time
 - You need scalability and replay

Avoid it for simple background tasks.