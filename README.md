```
 ██████╗ ██╗   ██╗███████╗██╗   ██╗███████╗    ██████╗  ██████╗  ██████╗██╗  ██╗██╗   ██╗ █████╗ ██████╗ ██████╗ 
██╔═══██╗██║   ██║██╔════╝██║   ██║██╔════╝    ██╔══██╗██╔═══██╗██╔════╝██║ ██╔╝╚██╗ ██╔╝██╔══██╗██╔══██╗██╔══██╗
██║   ██║██║   ██║█████╗  ██║   ██║█████╗      ██║  ██║██║   ██║██║     █████╔╝  ╚████╔╝ ███████║██████╔╝██║  ██║
██║▄▄ ██║██║   ██║██╔══╝  ██║   ██║██╔══╝      ██║  ██║██║   ██║██║     ██╔═██╗   ╚██╔╝  ██╔══██║██╔══██╗██║  ██║
╚██████╔╝╚██████╔╝███████╗╚██████╔╝███████╗    ██████╔╝╚██████╔╝╚██████╗██║  ██╗   ██║   ██║  ██║██║  ██║██████╔╝
 ╚══▀▀═╝  ╚═════╝ ╚══════╝ ╚═════╝ ╚══════╝    ╚═════╝  ╚═════╝  ╚═════╝╚═╝  ╚═╝   ╚═╝   ╚═╝  ╚═╝╚═╝  ╚═╝╚═════╝
```

A hands-on learning platform covering Apache Kafka, RabbitMQ, and AWS SQS — built progressively from 
first principles to production-grade patterns.

Every concept is backed by runnable Spring Boot code.

---

## 📚 What this repo covers

| Area                | Topics                                                                 |
|---------------------|------------------------------------------------------------------------|
| Apache Kafka        | Producer/consumer, partitions, consumer groups, offsets, manual commit |
| Kafka Internals     | Exactly-once, idempotency keys, partition rebalancing                  |
| Kafka Streams       | Filter, KTable aggregation, windowing, stream joins                    |
| Schema Registry     | Avro serialization, schema evolution, breaking change detection        |
| Dead Letter Topic   | Non-blocking retry, poison message handling                            |
| RabbitMQ            | Direct/fanout/topic exchanges, manual ACK, DLQ pattern                 |
| AWS SQS             | Standard queue, FIFO queue, visibility timeout, LocalStack             |
| Production Patterns | Idempotency, retry with backoff, DLQ, Redis state store                |
| Observability       | Actuator health checks, Prometheus metrics, Grafana dashboards         |
| Testing             | Testcontainers integration tests with real Kafka, RabbitMQ, Redis      |

---

## 🏗️ Repo Structure

```
queue-dockyard/
│
├── docs/                                     ← start here before writing any code
│   ├── fundamentals/
│   │   ├── 01-message-queues.md              ← what queues are and why they exist
│   │   ├── 02-messaging-patterns.md          ← point-to-point vs pub/sub
│   │   ├── 03-core-concepts.md               ← producer, consumer, topic, offset, DLQ
│   │   └── 04-delivery-guarantees.md         ← at-most-once, at-least-once, exactly-once
│   ├── systems/
│   │   ├── kafka.md                          ← Kafka deep dive
│   │   ├── rabbitmq.md                       ← RabbitMQ deep dive
│   │   ├── sqs.md                            ← SQS deep dive
│   │   └── kafka-vs-rabbitmq.md              ← when to use which
│   ├── monitoring/
│   │   └── observability.md                  ← Prometheus, Grafana, Micrometer explained
│   └── interview/
│       └── messaging-queues-qa.md            ← Q&A structured as problem/solution/tradeoff
│
├── docker/                                   ← all local infrastructure
│   ├── kafka-compose.yml                     ← Kafka + Zookeeper + Kafka UI + Schema Registry
│   ├── rabbit-compose.yml                    ← RabbitMQ + management UI (localhost:15672)
│   ├── localstack-compose.yml                ← AWS SQS locally via LocalStack
│   ├── monitoring-compose.yml                ← Redis + Prometheus + Grafana
│   ├── prometheus.yml                        ← Prometheus scrape config
│   └── grafana/
│       └── provisioning/
│           ├── datasources/
│           │   └── prometheus.yml            ← auto-wires Prometheus as Grafana datasource
│           └── dashboards/
│               ├── dashboard.yml             ← tells Grafana where to find dashboard JSON
│               └── queue-dockyard.json       ← ecommerce pipeline dashboard definition
│
├── phase-1-kafka-basics/
│   ├── order-producer/                       ← KafkaTemplate, async send, JSON serialization
│   ├── notification-consumer/                ← @KafkaListener, consumer groups, offset commit
│   ├── schema-registry/                      ← Avro schemas, Schema Registry, schema evolution
│   └── kafka-dlt/                            ← non-blocking retry, dead letter topic pattern
│
├── phase-2-kafka-internals/
│   ├── multi-partition/                      ← partition routing, consumer group rebalancing
│   ├── exactly-once/                         ← idempotency keys, manual offset commit
│   └── kafka-streams/                        ← filter, KTable, windowed aggregation, join
│
├── phase-3-rabbitmq/
│   ├── job-queue/                            ← direct, fanout, topic exchanges, manual ACK
│   └── dead-letter/                          ← retry with backoff, DLQ pattern
│
├── phase-4-sqs/
│   └── localstack/                           ← standard queue, FIFO queue, polling consumer
│
└── capstone/
    └── ecommerce/                            ← all three systems in one production pipeline
                                                 Redis idempotency, Prometheus metrics,
                                                 Grafana dashboard, Testcontainers tests
```

---

## 🚀 Quick start

```bash
# start all infrastructure
docker compose -f docker/kafka-compose.yml up -d
docker compose -f docker/rabbit-compose.yml up -d
docker compose -f docker/localstack-compose.yml up -d
docker compose -f docker/monitoring-compose.yml up -d

# open dashboards
# Kafka UI:        http://localhost:8080
# RabbitMQ UI:     http://localhost:15672  (guest/guest)
# Grafana:         http://localhost:3000   (admin/admin)
# Prometheus:      http://localhost:9090
# Schema Registry: http://localhost:8081/subjects
```

---

## 📚 Core Concepts

---

 - [Message Queues](docs/fundamentals/01-message-queues.md)
 - [Messaging Patterns](docs/fundamentals/02-messaging-patterns.md)
 - [Vocabulary](docs/fundamentals/03-core-concepts.md)
 - [Delivery Guarantees](docs/fundamentals/04-delivery-guarantees.md)

## ⚙️ Systems

---

- [Kafka](docs/systems/kafka.md)
- [RabbitMQ](docs/systems/rabbitmq.md)
- [SQS](docs/systems/sqs.md)
- [Kafka vs RabbitMQ](docs/systems/kafka-vs-rabbitmq.md)

---


## 📋 Prerequisites

You need these installed before writing any code:

| Tool           | Why                                      | Install                            |
|----------------|------------------------------------------|------------------------------------|
| Java 17+       | All Spring Boot apps run on Java 17      | `sdk install java 17` via SDKMAN   |
| Docker Desktop | Runs Kafka, RabbitMQ, LocalStack locally | docker.com/products/docker-desktop |
| Maven 3.8+     | Builds the Spring Boot projects          | `brew install maven` or SDKMAN     |
| IntelliJ IDEA  | Best Java IDE                            | jetbrains.com/idea                 |
| AWS CLI        | Needed to interact with LocalStack SQS   | `brew install awscli`              |