# Observability — Prometheus and Grafana

## 👀 What is Observability?

Observability is the ability to understand what is happening inside your system by looking at its outputs.

A system is observable when you can answer:
- Is the system healthy right now?
- How many orders were processed in the last 5 minutes?
- Is processing slowing down compared to yesterday?
- Which component failed and when?

Without observability, you are flying blind in production.
You find out something broke when a customer complains — not before.

Three pillars of observability:
- 📜 **Logs** → what happened (you already have this via @Slf4j)
- 📊 **Metrics** → how much / how fast / how long (Prometheus + Micrometer)
- 🔍 **Traces** → which path did a request take (future — OpenTelemetry)

---

## The Metrics Stack

```
[Spring Boot App] 🚀
        │
        │ exposes metrics at
        ▼
[/actuator/prometheus] 📡   ← raw metrics in Prometheus format
        │
        │ scraped every 15s
        ▼
[Prometheus] 📊   ← stores time-series metrics data
        │
        │ Grafana queries Prometheus
        ▼
[Grafana] 📈   ← visualizes metrics in dashboards
```

Each tool has one job:
- **Your app** — records what happened (counters, timers)
- **Prometheus** — collects and stores it over time
- **Grafana** — makes it human-readable

---

## Micrometer — The Metrics Facade

Micrometer is to metrics what SLF4J is to logging. It provides a simple API to record metrics in your Spring Boot app, and it can export them in a format that Prometheus understands.
```
SLF4J → your code calls log.info()
SLF4J routes to Logback / Log4j underneath
Micrometer → your code calls counter.increment()
Micrometer routes to Prometheus / Datadog / CloudWatch underneath
```

You write metrics code once using Micrometer.
Switching from Prometheus to Datadog in production
requires zero code changes — just swap the registry dependency.

### Metric types

- **Counter**: tracks how many times something happened (example: orders placed, emails sent, errors)
- **Gauge**: tracks a value at a point in time (example: queue depth, active connections, memory used)
- **Timer**: tracks how long something took (example: request latency, processing time)
- **Histogram**: tracks distribution of values (example: message size, batch size)

### How Micrometer auto-instruments

When you add `spring-boot-starter-actuator` and
`micrometer-registry-prometheus`, Micrometer automatically
instruments these without any code from you:

| What             | Metrics                                                                     |
|------------------|-----------------------------------------------------------------------------|
| JVM memory       | `jvm_memory_used_bytes`, `jvm_memory_committed_bytes`                       |
| JVM threads      | `jvm_threads_live_threads`, `jvm_threads_daemon_threads`                    |
| HTTP requests    | `http_server_requests_seconds_count`, `http_server_requests_seconds_bucket` |
| Kafka consumer   | `kafka_consumer_fetch_manager_records_consumed_total`                       |
| Kafka producer   | `kafka_producer_record_send_total`                                          |
| Connection pools | `hikaricp_connections_active`                                               |

You only need to write code for **business metrics** —
things Micrometer can't know about automatically,
like "how many orders were placed" or "how many duplicates detected".

---

## 📊 Prometheus — Time Series Database

- Prometheus is a pull-based metrics system.
- Instead of your app pushing metrics to Prometheus, Prometheus scrapes your app on a schedule.

```
Every 15 seconds:
Prometheus → GET http://your-app/actuator/prometheus
- receives all metrics as plain text
- stores as time-series data
```

### Prometheus data model

Every metric has:
- A name: `orders_placed_total`
- Labels (key-value pairs): `{application="ecommerce", instance="localhost:8088"}`
- A value: `42`
- A timestamp: `1713456789`

Labels are what make Prometheus powerful.
The same metric name with different labels lets you filter and group in any way you need.

### PromQL — Prometheus Query Language

Prometheus has its own query language called PromQL.
You write PromQL expressions in Grafana panels.

```
current value of a counter
orders_placed_total{application="ecommerce"}

rate of orders per second over last 1 minute
rate(orders_placed_total{application="ecommerce"}[1m])

rate converted to per minute
rate(orders_placed_total{application="ecommerce"}[1m]) * 60

95th percentile latency
histogram_quantile(0.95,
rate(http_server_requests_seconds_bucket{uri="/api/orders"}[5m]))
```

### Key PromQL functions

| Function                        | What it does                | When to use                           |
|---------------------------------|-----------------------------|---------------------------------------|
| `rate(metric[window])`          | Per-second rate of increase | Counters — requests/sec, messages/sec |
| `increase(metric[window])`      | Total increase over window  | How many in last hour                 |
| `histogram_quantile(p, metric)` | Percentile from histogram   | p50/p95/p99 latency                   |
| `sum by (label)`                | Aggregate across instances  | Total across all app instances        |
| `avg_over_time(metric[window])` | Average over time window    | Smoothing noisy gauges                |

---

## 📈 Grafana — Visualization Layer

Grafana connects to Prometheus (and other datasources)
and lets you build dashboards using PromQL queries.

### Panel types used in this repo

| Panel       | Used for                                             |
|-------------|------------------------------------------------------|
| Stat        | Single number — total orders, total emails           |
| Time series | Values over time — throughput graphs, latency trends |
| Row         | Groups related panels together                       |

### Dashboard refresh

The queue-dockyard dashboard refreshes every 10 seconds.
You can watch metrics update in real time while placing orders.

### Thresholds

Stat panels use color thresholds to signal health:

- Green  → healthy (duplicates = 0)
- Yellow → warning (duplicates = 1-9)
- Red    → critical (duplicates >= 10)

This is how teams get instant visual health signals without reading raw numbers.

---

## What the Queue Dockyard Dashboard Shows

### Row 1 — Order Pipeline Overview
Six stat panels showing total counts since startup:
- Orders placed
- Inventory processed
- Analytics recorded
- Emails sent
- SMS sent
- Invoices generated

In a healthy system all six numbers should be equal.
If emails sent < orders placed, the email consumer is behind.

### Row 2 — Order Rate Over Time
Time series showing events per minute across all consumers.
All six lines should track together — divergence means lag.

### Row 3 — Publish Duration
p50, p95, p99 latency for publishing to all three systems.
Sudden spikes mean one of the brokers is slow.

### Row 4 — JVM Health
Heap memory and thread counts over time.
Steadily growing heap = memory leak.
Thread count spike = blocking calls or connection pool exhaustion.

### Row 5 — HTTP API
Request rate and latency for POST /api/orders.
p99 latency spike = slow broker or Redis.

---

## Actuator Endpoints

Spring Boot Actuator exposes these endpoints:

| Endpoint   | URL                    | What it shows                           |
|------------|------------------------|-----------------------------------------|
| Health     | `/actuator/health`     | App + Redis + Kafka + RabbitMQ status   |
| Metrics    | `/actuator/metrics`    | List of all available metrics           |
| Prometheus | `/actuator/prometheus` | Raw metrics in Prometheus scrape format |
| Info       | `/actuator/info`       | App name, version, build info           |

### Health check example

```bash
curl http://localhost:8088/actuator/health
```

```json
{
  "status": "UP",
  "components": {
    "kafka": { "status": "UP" },
    "rabbit": { "status": "UP" },
    "redis": { "status": "UP" },
    "diskSpace": { "status": "UP" }
  }
}
```

If any component shows DOWN, your app is degraded.
In production, Kubernetes uses this endpoint to decide
whether to restart the pod.

---

## Production Differences

This setup is simplified for learning. In production:

| This repo                          | Production                                |
|------------------------------------|-------------------------------------------|
| Single Prometheus instance         | Prometheus with high-availability replica |
| Local Grafana                      | Grafana Cloud or self-hosted with auth    |
| App exposes all actuator endpoints | Only health and prometheus exposed        |
| No alerting                        | Alertmanager sends PagerDuty/Slack alerts |
| 7 day metric retention             | 90+ day retention with remote storage     |
| Manual dashboard JSON              | Dashboards as code via Grafonnet          |

### Alerting example (production)

```yaml
# Prometheus alert rule
- alert: HighDuplicateRate
  expr: rate(messages_duplicates_detected_total[5m]) > 0.1
  for: 2m
  labels:
    severity: warning
  annotations:
    summary: "High duplicate message rate detected"
    description: "More than 0.1 duplicates/sec for 2 minutes"
```

When this fires, Alert manager sends a Slack message to your on-call channel. You investigate before customers notice.