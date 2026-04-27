# schema-registry

Demonstrates Apache Kafka with Confluent Schema Registry
and Avro serialization — the production-standard approach
for message contracts between producers and consumers.

---

## The Problem with Plain JSON

In all previous phases, messages were serialized as JSON:

```
// producer
OrderEvent event = new OrderEvent();
kafkaTemplate.send("orders", event);  // serialized to JSON string

// consumer
@KafkaListener
public void onOrder(OrderEvent event) { }  // deserialized from JSON
```

This works — until it breaks silently:
```
Day 1: Producer renames "amount" to "totalAmount" and deploys
Consumer still expects "amount"
Consumer receives null for every order amount
No exception thrown — silent data corruption
You find out when revenue reports show zero
```

JSON has no enforcement. Producer and consumer are on an
honour system — and honour systems fail in production.

---

## The Solution — Schema Registry + Avro
```
Producer wants to publish
↓
Confluent serializer checks schema with Registry
↓
Schema compatible → message published as Avro binary
Schema incompatible → publish REJECTED before reaching Kafka
↓
Consumer receives message
↓
Confluent deserializer reads schema ID from message header
↓
Fetches correct schema from Registry
↓
Deserializes to strongly typed Java object
```

Breaking changes caught at publish time — not at runtime.

---

## How Avro Works

### The schema file (.avsc)

```json
{
  "type": "record",
  "name": "OrderEvent",
  "fields": [
    { "name": "orderId",    "type": "string" },
    { "name": "amount",     "type": "double" },
    { "name": "status",     "type": { "type": "enum",
                                      "symbols": ["PLACED","CANCELLED"] }}
  ]
}
```

The `.avsc` file is the source of truth — not the Java class.
The Java class is generated from it automatically by the Avro Maven Plugin.

### The 5-byte message header

Every Avro message sent via Confluent serializer starts with:
```
Byte 0:   0x00 (magic byte — marks this as a Schema Registry message)
Bytes 1-4: schema ID (integer) — e.g. 0x00 0x00 0x00 0x01 = schema ID 1
Bytes 5+:  Avro binary payload
```

The consumer reads bytes 1-4 to get the schema ID,
fetches that schema from Registry (cached after first fetch),
and deserializes the rest.

### Avro enum vs String

```
// JSON — any string is valid, no enforcement
event.setStatus("PLCAED");  // typo — compiles, no error

// Avro enum — only defined values compile
event.setStatus(OrderStatus.PLACED);    // ✅ compiles
event.setStatus(OrderStatus.PLCAED);   // ❌ compile error
```

---

## Schema Evolution — Backward Compatibility

The real power of Schema Registry is controlled evolution.

### Safe change — adding a field WITH a default

```
// V1
{ "name": "amount", "type": "double" }

// V2 — adds region with default
{ "name": "amount",  "type": "double" },
{ "name": "region",  "type": "string", "default": "UNKNOWN" }
```

V1 consumers can still read V2 messages — they ignore
the unknown `region` field. Schema Registry accepts this.

### Breaking change — removing a field or changing type

```
// V1
{ "name": "amount", "type": "double" }

// Breaking — renamed field, no default
{ "name": "totalAmount", "type": "double" }
```

Schema Registry REJECTS this — existing consumers would break.

### Compatibility modes

| Mode               | What it allows                   |
|--------------------|----------------------------------|
| BACKWARD (default) | New schema can read old messages |
| FORWARD            | Old schema can read new messages |
| FULL               | Both directions                  |
| NONE               | Any change allowed (dangerous)   |

---

## Project Structure
```
schema-registry/
 └── src/main/
 ├── avro/
 │   ├── OrderEvent.avsc       ← V1 schema (source of truth)
 │   └── OrderEventV2.avsc     ← V2 schema (evolved — adds region + email)
 ├── java/com/queuedockyard/schemaregistry/
 │   ├── config/
 │   │   ├── AppConfig.java        ← RestTemplate bean
 │   │   └── KafkaConfig.java      ← topic creation
 │   ├── consumer/
 │   │   └── OrderConsumer.java    ← receives Avro objects, not JSON
 │   ├── controller/
 │   │   └── SchemaController.java ← publish V1/V2, inspect Registry
 │   └── service/
 │       └── OrderProducerService.java ← publishes Avro events
 └── resources/
 └── application.yml
```

Generated classes (do not edit manually):
```
target/generated-sources/avro/
└── com/queuedockyard/schema/
 ├── OrderEvent.java      ← generated from OrderEvent.avsc
 ├── OrderEventV2.java    ← generated from OrderEventV2.avsc
 └── OrderStatus.java     ← generated from the enum in the schema
```

---

## How to Run

```bash
# start Kafka + Schema Registry
docker compose -f docker/kafka-compose.yml up -d

# verify Schema Registry is up
curl http://localhost:8081/subjects
# returns: []  (no schemas yet)

# generate Avro classes and start the app
cd phase-2-kafka-basics/schema-registry
mvn generate-sources   # generates Java classes from .avsc files
mvn spring-boot:run
```

App starts on port **8090**.

---

## Experiments

### Experiment 1 — Publish V1 and check Registry

```bash
curl -X POST http://localhost:8090/api/schema/v1 \
  -H "Content-Type: application/json" \
  -d '{"customerId":"CUST-001","amount":1500.00,"status":"PLACED"}'
```

Then check Schema Registry directly:
```bash
# list all registered schemas
curl http://localhost:8081/subjects
# returns: ["schema-demo-orders-v1-value"]

# get the actual schema
curl http://localhost:8081/subjects/schema-demo-orders-v1-value/versions/1
```

---

### Experiment 2 — Publish V2 (schema evolution)

```bash
curl -X POST http://localhost:8090/api/schema/v2 \
  -H "Content-Type: application/json" \
  -d '{"customerId":"CUST-001","amount":2500.00,"status":"PLACED","region":"SOUTH","customerEmail":"customer@example.com"}'
```

Check Registry again:
```bash
curl http://localhost:8081/subjects
# returns: ["schema-demo-orders-v1-value", "schema-demo-orders-v2-value"]
```

---

### Experiment 3 — Try an invalid status

```bash
curl -X POST http://localhost:8090/api/schema/v1 \
  -H "Content-Type: application/json" \
  -d '{"customerId":"CUST-001","amount":1500.00,"status":"INVALID_STATUS"}'
```

The app throws an exception at serialization time —
`INVALID_STATUS` is not in the enum. Kafka never receives it.
This is type enforcement JSON cannot provide.

---

### Experiment 4 — Inspect via app endpoint

```bash
# list all subjects via the app
curl http://localhost:8090/api/schema/subjects

# list versions of V1 schema
curl http://localhost:8090/api/schema/versions/schema-demo-orders-v1-value
```

---

## Configuration

| Property                   | Value                   | Description                                          |
|----------------------------|-------------------------|------------------------------------------------------|
| `schema.registry.url`      | `http://localhost:8081` | Schema Registry address                              |
| `auto.register.schemas`    | `true`                  | Auto-register on first publish (false in production) |
| `use.specific.avro.reader` | `true`                  | Deserialize to generated class not GenericRecord     |
| `server.port`              | `8090`                  | HTTP port                                            |

---

## JSON vs Avro — Summary

|                             | JSON      | Avro                     |
|-----------------------------|-----------|--------------------------|
| Human readable              | ✅         | ❌ (binary)               |
| Schema enforcement          | ❌         | ✅                        |
| Type safety                 | ❌         | ✅                        |
| Breaking change detection   | ❌         | ✅ (Schema Registry)      |
| Message size                | Large     | 3-10x smaller            |
| Serialization speed         | Moderate  | Fast                     |
| Setup complexity            | Simple    | Requires Schema Registry |
| Used in production at scale | Sometimes | Standard                 |