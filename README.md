```
 ██████╗ ██╗   ██╗███████╗██╗   ██╗███████╗    ██████╗  ██████╗  ██████╗██╗  ██╗██╗   ██╗ █████╗ ██████╗ ██████╗ 
██╔═══██╗██║   ██║██╔════╝██║   ██║██╔════╝    ██╔══██╗██╔═══██╗██╔════╝██║ ██╔╝╚██╗ ██╔╝██╔══██╗██╔══██╗██╔══██╗
██║   ██║██║   ██║█████╗  ██║   ██║█████╗      ██║  ██║██║   ██║██║     █████╔╝  ╚████╔╝ ███████║██████╔╝██║  ██║
██║▄▄ ██║██║   ██║██╔══╝  ██║   ██║██╔══╝      ██║  ██║██║   ██║██║     ██╔═██╗   ╚██╔╝  ██╔══██║██╔══██╗██║  ██║
╚██████╔╝╚██████╔╝███████╗╚██████╔╝███████╗    ██████╔╝╚██████╔╝╚██████╗██║  ██╗   ██║   ██║  ██║██║  ██║██████╔╝
 ╚══▀▀═╝  ╚═════╝ ╚══════╝ ╚═════╝ ╚══════╝    ╚═════╝  ╚═════╝  ╚═════╝╚═╝  ╚═╝   ╚═╝   ╚═╝  ╚═╝╚═╝  ╚═╝╚═════╝
```
## 🗂️ Repo Structure
```
queue-dockyard/
├── docs/
│   ├── fundamentals/        ← concepts, patterns, vocabulary, delivery guarantees
│   └── systems/             ← Kafka, RabbitMQ, SQS deep dives + comparison
├── docker/                  ← local infra for all three systems
├── phase-1-fundamentals/    ← no code, just notes
├── phase-2-kafka-basics/    ← first producer + consumer
├── phase-3-kafka-internals/ ← partitions, exactly-once
├── phase-4-rabbitmq/        ← exchanges, DLQ, retry
├── phase-5-sqs/             ← SQS via LocalStack
└── capstone/                ← full e-commerce pipeline
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
| IntelliJ IDEA  | Best Java IDE, excellent Spring support  | jetbrains.com/idea                 |
| AWS CLI        | Needed to interact with LocalStack SQS   | `brew install awscli`              |