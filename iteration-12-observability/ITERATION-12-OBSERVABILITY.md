# Iteration 12: Observability with OpenTelemetry

> **Training Module for NatWest Java Developers**
>
> _From flying blind to full visibility -- unified traces, metrics, and logs
> with zero Java code changes._

---

## Table of Contents

1. [The Problem](#the-problem)
2. [The 3 Pillars of Observability](#the-3-pillars-of-observability)
3. [What is OpenTelemetry (OTEL)](#what-is-opentelemetry-otel)
4. [Our Architecture](#our-architecture)
5. [The Tool Stack](#the-tool-stack)
6. [What Changed from Iteration 10](#what-changed-from-iteration-10)
7. [How the Data Flows (End to End)](#how-the-data-flows-end-to-end)
8. [How to Run](#how-to-run)
9. [Accessing the UIs](#accessing-the-uis)
10. [Hands-On Exercises](#hands-on-exercises)
11. [Useful Queries Reference](#useful-queries-reference)
12. [OTEL vs Previous Approach (Comparison)](#otel-vs-previous-approach-comparison)
13. [Port Reference](#port-reference)
14. [Architecture Decision Records](#architecture-decision-records)
15. [Key Takeaways](#key-takeaways)

---

## The Problem

Our FTGO application has grown to **7 microservices**, **3 infrastructure services** (Kafka, Eureka, Config Server), and a frontend -- totalling **16 containers** running simultaneously via Docker Compose.

A single **"Create Order"** request touches **5+ services**:

```
Browser --> api-gateway --> order-service --> restaurant-service
                                         --> accounting-service
                                         --> kitchen-service --> (Kafka) --> notification-service
                                         --> delivery-service
```

This architecture works functionally, but we are **flying blind in production**.

### Pain Point 1: Logs Are Scattered Across Containers

When a user reports "my order failed," which of the 7 containers do you check? You end up
running all of these -- one by one -- in separate terminals:

```bash
docker logs ftgo-order-service-1
docker logs ftgo-restaurant-service-1
docker logs ftgo-kitchen-service-1
docker logs ftgo-accounting-service-1
docker logs ftgo-notification-service-1
docker logs ftgo-delivery-service-1
docker logs ftgo-api-gateway-1
```

That is **7 commands**, **7 terminal windows**, and **no way to correlate** log lines from the
same request across them. Good luck finding a needle in that haystack.

### Pain Point 2: No Way to Trace a Request Across Services

The order creation request fans out to 5+ services. When it returns a 500 error:

- **Which service** caused the failure?
- **How long** did each hop take?
- Did the request even **reach** the downstream service?
- Was the Kafka message **published**? Was it **consumed**?

Without distributed tracing, these questions are unanswerable. You are reduced to timestamp
matching across separate log files.

### Pain Point 3: No Visibility into System Health

You have **zero metrics**. You cannot answer:

| Question | Answer Without Metrics |
|----------|----------------------|
| How much JVM heap is order-service using? | No idea -- until it OOMs |
| What is the p95 HTTP latency? | No idea -- until users complain |
| What is the error rate right now? | No idea -- until the support queue fills up |
| Is Kafka consumer falling behind? | No idea -- until orders stop processing |

Without metrics, you discover problems **after** customers do.

### Pain Point 4: Previous Approach Was Fragmented

In earlier iterations, we explored separate tools:

| Iteration | Tool | Covers | Problem |
|-----------|------|--------|---------|
| Iteration 11 | Zipkin | Traces only | Needed Brave/Micrometer code in pom.xml |
| Iteration 12 (prev) | ELK (Elasticsearch + Filebeat + Kibana) | Logs only | Elasticsearch needs 2+ GB RAM |
| -- | Nothing | Metrics | No solution existed |

**The result:**

- **3 separate tools**, **3 separate UIs**, **3 separate query languages**
- No correlation between traces and logs (copy a traceId from Zipkin, paste into Kibana)
- No metrics at all
- Each tool required its own integration code or sidecar agent
- High resource overhead

---

## The 3 Pillars of Observability

Production microservices need **three types** of telemetry data. Each answers a different
question, and together they provide complete visibility.

### Pillar 1: Traces

> **Question: What path did this request take, and how long did each hop take?**

A trace is a tree of **spans** that follows a single request across all services it touches.

**FTGO Example:** A "Create Order" trace:

```
api-gateway       POST /api/orders          ################################  450ms
  order-service     POST /orders            ##############################    420ms
    restaurant-svc    GET /restaurants/1     ################                  180ms
    accounting-svc    POST /accounts/auth    ##########                        120ms
    kitchen-svc       POST /tickets          ########                           90ms
      kafka-produce   order-events           ##                                 15ms
```

Each bar is a **span**. The trace tells you: order-service spent most of its time waiting
on restaurant-service (180ms). That is your bottleneck.

### Pillar 2: Metrics

> **Question: Is the system healthy right now? What are the trends?**

Metrics are **numeric time-series data** sampled at regular intervals.

**FTGO Examples:**

| Metric | What It Tells You |
|--------|-------------------|
| `jvm_memory_used_bytes{area="heap"}` | order-service heap is at 78% -- approaching danger zone |
| `http_server_request_duration_seconds` (p99) | p99 latency is 320ms -- acceptable |
| `http_server_request_duration_seconds_count{status=~"5.."}` | 12 errors in the last 5 minutes -- investigate |
| `kafka_consumer_records_lag_max` | Consumer lag is growing -- notification-service is falling behind |
| `jvm_thread_count` | 150 active threads -- normal |

### Pillar 3: Logs

> **Question: What happened in detail at a specific point in time?**

Logs are **timestamped text records** emitted by application code.

**FTGO Example:**

```
2024-01-15 10:23:45.123 ERROR [order-service] OrderService:
    Restaurant 42 not found for order 1001
    traceId=abc123def456 spanId=789xyz
```

The key detail: each log line carries a **traceId** and **spanId**, linking it to the
exact request that produced it.

### How the 3 Pillars Connect

The real power is **correlation** -- moving between pillars to find root cause:

```
  METRIC SPIKE                TRACE                         LOGS
  +--------------+     +-------------------+     +-------------------------+
  | p99 latency  |     | order-service     |     | 10:23:45 ERROR          |
  | jumped to    | --> | POST /orders      | --> | Restaurant 42 not found |
  | 2.5 seconds  |     |   restaurant-svc  |     | for order 1001          |
  | at 10:23     |     |   GET /rest/42    |     | traceId=abc123def456    |
  |              |     |   TIMEOUT (2000ms)|     |                         |
  +--------------+     +-------------------+     +-------------------------+

  "Something is slow"    "restaurant-service       "Restaurant 42 was
                          timed out"                deleted from the DB"
```

**Metric spike** --> **find the trace** --> **find the logs** --> **root cause identified.**

This workflow is impossible when traces, metrics, and logs live in separate, unlinked tools.

---

## What is OpenTelemetry (OTEL)

### The Problem Before OTEL: Vendor Lock-In

Before OpenTelemetry, each observability vendor had its own proprietary SDK:

| Vendor | Tracing SDK | Metrics SDK | Lock-In |
|--------|-------------|-------------|---------|
| Zipkin | Brave | -- | Zipkin-specific wire format |
| Jaeger | Jaeger client | -- | Jaeger-specific wire format |
| Datadog | dd-trace-java | Datadog agent | Datadog-only export |
| New Relic | New Relic agent | New Relic agent | New Relic-only export |
| AWS X-Ray | X-Ray SDK | CloudWatch SDK | AWS-only export |

Switching vendors meant **rewriting your instrumentation code**. Each SDK had different
APIs, different configuration, and different capabilities. And none of them covered all
three pillars (traces + metrics + logs) in a unified way.

### OTEL = The Universal Standard

**OpenTelemetry** is a CNCF (Cloud Native Computing Foundation) **graduated project** --
the same organization and maturity level as Kubernetes. It provides:

- **One standard** for traces, metrics, AND logs
- **One wire protocol** (OTLP -- OpenTelemetry Protocol) that all vendors accept
- **One instrumentation** that works with ANY backend

```
                  Before OTEL                           With OTEL
        +---------------------------+          +---------------------------+
        |     Application Code      |          |     Application Code      |
        +---------------------------+          +---------------------------+
        | Zipkin  | Datadog | Jaeger |          |    OTEL Java Agent        |
        | SDK     | SDK     | SDK    |          |    (one agent, all 3      |
        +---------+---------+--------+          |     pillars)              |
              |         |        |              +---------------------------+
              v         v        v                          |
          Zipkin    Datadog   Jaeger                  OTLP Protocol
                                                            |
                                                   ANY backend:
                                                   Grafana, Datadog,
                                                   New Relic, AWS, etc.
```

### The 3 Components of OpenTelemetry

| Component | What It Does | In Our Setup |
|-----------|-------------|--------------|
| **API / SDK** | Standard interfaces for creating telemetry | Bundled inside the Java Agent |
| **Auto-Instrumentation Agent** | Bytecode agent that instruments frameworks automatically | `opentelemetry-javaagent.jar` (v2.11.0) |
| **Collector** | Central pipeline: receive, process, export telemetry | `otel/opentelemetry-collector-contrib:0.114.0` |

### The Java Agent: Zero Code Changes

The OTEL Java Agent (`opentelemetry-javaagent.jar`) uses **bytecode instrumentation** via
Java's `java.lang.instrument` API. It intercepts method calls in well-known frameworks at
class load time.

**What this means in practice:**

- No `@Traced` annotations on your code
- No `opentelemetry-sdk` dependency in `pom.xml`
- No `TracerProvider` bean configuration
- No `MDC.put("traceId", ...)` calls in your logging code

You simply add `-javaagent:/otel/opentelemetry-javaagent.jar` to the JVM startup command.

### What the Agent Auto-Instruments

| Framework / Library | What Gets Instrumented | Pillar |
|---------------------|----------------------|--------|
| **Spring Web MVC** | Incoming HTTP requests become trace spans | Traces |
| **Spring WebFlux** | Reactive HTTP requests become trace spans | Traces |
| **Spring Cloud Gateway** | Route matching and forwarding become trace spans | Traces |
| **RestTemplate** | Outgoing HTTP calls become child spans with context propagation | Traces |
| **WebClient** | Outgoing reactive HTTP calls become child spans | Traces |
| **Apache Kafka Producer** | Message publish becomes a span; trace context injected into headers | Traces |
| **Apache Kafka Consumer** | Message consume becomes a linked span | Traces |
| **JDBC** | SQL queries become spans with query text | Traces |
| **Logback / Log4j2** | Log lines exported as OTEL log records with traceId/spanId | Logs |
| **JVM Runtime** | Heap, GC, threads, class loading become OTEL metrics | Metrics |

> **Key insight:** One agent covers all three pillars for all the frameworks we use.
> No per-framework configuration needed.

---

## Our Architecture

### High-Level Data Flow

```
+================================================================================+
|                           FTGO Application Layer                               |
|                                                                                |
|   +-------------+   +--------------+   +-------------------+                   |
|   | api-gateway |-->| order-service|-->| restaurant-service|                   |
|   |   (8090)    |   |   (8080)     |   |     (8081)        |                   |
|   +-------------+   +--------------+   +-------------------+                   |
|                            |                                                   |
|                      +-----+------+------+                                     |
|                      |            |      |                                     |
|               +------+-----+ +---+----+ +--------+-----+                      |
|               | accounting | |kitchen | |  delivery     |                      |
|               |  (8083)    | | (8084) | |   (8085)      |                      |
|               +------------+ +---+----+ +--------------+                       |
|                                  |                                             |
|                             Kafka (9092)                                       |
|                                  |                                             |
|                        +---------+--------+                                    |
|                        | notification-svc |                                    |
|                        |     (8082)       |                                    |
|                        +------------------+                                    |
|                                                                                |
|   [Each service runs with the OTEL Java Agent attached]                        |
|   [Agent emits traces + metrics + logs via OTLP protocol]                      |
+====================================|=========================================+
                                     |
                              OTLP (HTTP :4318)
                                     |
                      +--------------+--------------+
                      |       OTEL Collector        |
                      |   (Receive - Process - Export)|
                      |                             |
                      |   receivers:  OTLP          |
                      |   processors: batch,        |
                      |               resource      |
                      |   exporters:                |
                      |     traces  --> Tempo       |
                      |     metrics --> Prometheus  |
                      |     logs    --> Loki        |
                      +-------+-------+-------+----+
                              |       |       |
                    +---------+  +----+  +----+---------+
                    |            |            |          |
             +------+------+ +--+-------+ +--+-------+  |
             |    Tempo    | |Prometheus| |   Loki   |  |
             |  (traces)   | | (metrics)| |  (logs)  |  |
             |  port 3200  | | port 9090| | port 3100|  |
             +------+------+ +--+-------+ +--+-------+  |
                    |            |            |          |
                    +------------+------------+          |
                                 |                       |
                      +----------+----------+            |
                      |      Grafana        |            |
                      |    port 3001        |            |
                      |                     |            |
                      |  Unified dashboard  |            |
                      |  for all 3 pillars  |            |
                      |                     |            |
                      |  Trace <--> Logs    |            |
                      |  Logs  <--> Trace   |            |
                      |  Metrics --> Traces |            |
                      +---------------------+            |
```

### OTEL Collector Pipeline Detail

```
+-----------------------------------------------------------------------+
|                        OTEL Collector                                  |
|                                                                       |
|  RECEIVERS            PROCESSORS              EXPORTERS               |
|  +----------------+   +------------------+                            |
|  |     OTLP       |   |     batch        |   +------------------+    |
|  |  gRPC  :4317   |-->|  timeout: 5s     |-->| otlp/tempo       |--> Tempo (:4317)
|  |  HTTP  :4318   |   |  batch: 1024     |   +------------------+    |
|  +----------------+   |                  |                            |
|                       |     resource     |   +------------------+    |
|                       |  namespace: ftgo |-->| prometheus       |--> Prometheus scrapes :8889
|                       |  action: upsert  |   +------------------+    |
|                       |                  |                            |
|                       |                  |   +------------------+    |
|                       |                  |-->| loki             |--> Loki (:3100)
|                       +------------------+   +------------------+    |
|                                                                       |
|  PIPELINES:                                                           |
|    traces:   otlp --> [batch, resource] --> otlp/tempo                |
|    metrics:  otlp --> [batch, resource] --> prometheus                 |
|    logs:     otlp --> [batch, resource] --> loki                       |
+-----------------------------------------------------------------------+
```

### Docker Compose Service Map (16 Containers)

```
docker compose up --build
|
+-- INFRASTRUCTURE
|   +-- kafka                  (9092)          Message broker
|   +-- eureka-server          (8761)          Service registry
|   +-- config-server          (8888)          Centralized config
|
+-- OBSERVABILITY (NEW)
|   +-- otel-collector         (4317, 4318, 8889)   Telemetry pipeline
|   +-- tempo                  (3200)                Trace storage
|   +-- loki                   (3100)                Log storage
|   +-- prometheus             (9090)                Metrics storage
|   +-- grafana                (3001)                Unified UI
|
+-- APPLICATION SERVICES (OTEL Java Agent attached)
|   +-- order-service          (8080)          Orchestrator
|   +-- restaurant-service     (8081)          Restaurant CRUD
|   +-- notification-service   (8082)          Kafka consumer
|   +-- accounting-service     (8083)          Payment auth
|   +-- kitchen-service        (8084)          Kafka producer
|   +-- delivery-service       (8085)          Delivery mgmt
|   +-- api-gateway            (8090)          Entry point
|
+-- FRONTEND
    +-- ftgo-web               (3000)          Next.js SPA
```

---

## The Tool Stack

| Tool | Role | Port(s) | Replaces | Why We Chose It |
|------|------|---------|----------|-----------------|
| **OTEL Java Agent** (v2.11.0) | Auto-instruments all 7 Java services | -- | Brave/Micrometer + manual code in pom.xml | Zero code changes; bytecode instrumentation |
| **OTEL Collector** (v0.114.0) | Central telemetry pipeline: receive, process, export | 4317 (gRPC), 4318 (HTTP), 8889 (metrics) | Direct SDK exports to each backend | Single endpoint; backend-agnostic; batching/buffering |
| **Tempo** (v2.6.1) | Distributed trace storage and query | 3200 | Zipkin | Native OTLP support; minimal resource usage; Grafana integration |
| **Loki** (v3.3.2) | Log aggregation (index labels only, not full text) | 3100 | Elasticsearch + Kibana | 10x less memory than ES; label-based indexing; Grafana native |
| **Prometheus** (v2.54.1) | Time-series metrics storage and query | 9090 | Nothing (new capability!) | Industry standard; PromQL; Grafana native |
| **Grafana** (v11.4.0) | Unified dashboard for all 3 pillars | 3001 | Zipkin UI + Kibana (2 separate UIs) | One UI for everything; built-in cross-pillar correlation |

---

## What Changed from Iteration 10

### The Key Point: Zero Java Code Changes

Not a single `.java` file was modified. No dependencies added to any `pom.xml`. No
annotations. No configuration classes. No bean definitions.

**Everything** was done via:
- Dockerfile modifications (2 lines per service)
- docker-compose.yml environment variables (7 per service)
- 5 new YAML configuration files

### 1. Dockerfiles (7 Services Modified)

Two lines added to each of the 7 application service Dockerfiles:

**Before (Iteration 10):**

```dockerfile
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**After (Iteration 12):**

```dockerfile
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Download OpenTelemetry Java Agent for auto-instrumentation
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.11.0/opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar

EXPOSE 8080
ENTRYPOINT ["java", "-javaagent:/otel/opentelemetry-javaagent.jar", "-jar", "app.jar"]
```

| Line | What It Does |
|------|-------------|
| `ADD https://...opentelemetry-javaagent.jar` | Downloads the OTEL agent JAR into `/otel/` inside the container at build time |
| `-javaagent:/otel/opentelemetry-javaagent.jar` | Tells the JVM to load the agent at startup for bytecode instrumentation |

> **Note:** `eureka-server/Dockerfile` and `config-server/Dockerfile` were **NOT** modified.
> These infrastructure services are not part of the business request flow and do not need
> observability instrumentation.

### 2. docker-compose.yml

**5 new infrastructure services** added (otel-collector, tempo, loki, prometheus, grafana).

**7 OTEL environment variables** added to each application service, plus a dependency on
`otel-collector`.

**Sample service block (order-service):**

```yaml
order-service:
  build: ./order-service
  ports:
    - "8080:8080"
  environment:
    SPRING_PROFILES_ACTIVE: docker
    SPRING_CONFIG_IMPORT: optional:configserver:http://config-server:8888
    # --- OTEL configuration (NEW) ---
    OTEL_SERVICE_NAME: order-service
    OTEL_EXPORTER_OTLP_ENDPOINT: http://otel-collector:4318
    OTEL_EXPORTER_OTLP_PROTOCOL: http/protobuf
    OTEL_LOGS_EXPORTER: otlp
    OTEL_METRICS_EXPORTER: otlp
    OTEL_TRACES_EXPORTER: otlp
    OTEL_RESOURCE_ATTRIBUTES: service.namespace=ftgo
  depends_on:
    kafka:
      condition: service_healthy
    config-server:
      condition: service_healthy
    otel-collector:                    # <-- NEW dependency
      condition: service_started
```

**Environment variable reference:**

| Variable | Value | Purpose |
|----------|-------|---------|
| `OTEL_SERVICE_NAME` | `order-service` | Identifies this service in all telemetry data |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://otel-collector:4318` | Where to send telemetry (Collector HTTP endpoint) |
| `OTEL_EXPORTER_OTLP_PROTOCOL` | `http/protobuf` | Use HTTP transport with protobuf encoding |
| `OTEL_LOGS_EXPORTER` | `otlp` | Export logs via OTLP to the Collector |
| `OTEL_METRICS_EXPORTER` | `otlp` | Export metrics via OTLP to the Collector |
| `OTEL_TRACES_EXPORTER` | `otlp` | Export traces via OTLP to the Collector |
| `OTEL_RESOURCE_ATTRIBUTES` | `service.namespace=ftgo` | Tags all telemetry with namespace for filtering |

> These variables are read directly by the OTEL Java Agent. No Spring configuration needed.

### 3. Configuration Files (5 New Files)

| File | Purpose | Key Details |
|------|---------|-------------|
| `otel-collector-config.yaml` | OTEL Collector pipeline configuration | Defines receivers (OTLP), processors (batch, resource), exporters (Tempo, Prometheus, Loki) |
| `tempo-config.yaml` | Grafana Tempo trace storage | Local filesystem backend; receives traces via OTLP gRPC on :4317 |
| `loki-config.yaml` | Grafana Loki log storage | TSDB schema v13; label-only indexing; filesystem storage |
| `prometheus.yml` | Prometheus scrape configuration | Scrapes otel-collector:8889 every 15 seconds |
| `grafana/provisioning/datasources/datasources.yaml` | Pre-configures Grafana datasources | Tempo, Prometheus, Loki with cross-linking enabled |

### 4. config-repo/application.properties

Two lines added to expose actuator endpoints for all services:

```properties
# Actuator -- expose health and Prometheus endpoints
management.endpoints.web.exposure.include=health,info,prometheus
management.endpoint.health.show-details=always
```

### Summary of All Changes

| Files Changed | What Changed | Lines Added |
|--------------|-------------|-------------|
| 7 Dockerfiles (order, restaurant, accounting, kitchen, notification, delivery, api-gateway) | Added OTEL agent download + javaagent flag | ~2 per file (14 total) |
| `docker-compose.yml` | Added 5 observability containers + 7 OTEL env vars per service | ~120 lines |
| `config-repo/application.properties` | Added actuator endpoint exposure | 2 lines |
| `otel-collector-config.yaml` (NEW) | Collector pipeline: receivers, processors, exporters | ~45 lines |
| `tempo-config.yaml` (NEW) | Tempo trace storage config | ~18 lines |
| `loki-config.yaml` (NEW) | Loki log storage config | ~28 lines |
| `prometheus.yml` (NEW) | Prometheus scrape targets | ~10 lines |
| `grafana/provisioning/datasources/datasources.yaml` (NEW) | Grafana datasource auto-provisioning | ~45 lines |
| **Total** | **13 files modified/created** | **~280 lines** |

> **Zero `.java` files changed. Zero `pom.xml` files changed.**

---

## How the Data Flows (End to End)

Here is exactly what happens when you make a single API call:

```bash
curl http://localhost:8090/api/restaurants
```

### Step-by-Step Walkthrough

```
Step   What Happens                                           Where
-----  ----------------------------------------------------   -------------------------

 1     curl sends HTTP GET to localhost:8090                   Your terminal

 2     api-gateway receives the request                        api-gateway container
       OTEL Agent creates ROOT SPAN:
         traceId = abc123, spanId = span-1
         name = "GET /api/restaurants"

 3     api-gateway routes to restaurant-service                api-gateway --> restaurant-svc
       OTEL Agent propagates trace context via HTTP headers:
         traceparent: 00-abc123-span-1-01

 4     restaurant-service receives the request                 restaurant-service container
       OTEL Agent creates CHILD SPAN:
         traceId = abc123, spanId = span-2, parentId = span-1
         name = "GET /restaurants"

 5     restaurant-service queries H2 database                  restaurant-service container
       OTEL Agent creates CHILD SPAN:
         traceId = abc123, spanId = span-3, parentId = span-2
         name = "SELECT * FROM restaurant"

 6     Application logs are emitted (Logback)                  restaurant-service container
       OTEL Agent injects traceId + spanId into each log line
       OTEL Agent exports log records via OTLP

 7     JVM metrics are collected by OTEL Agent                 restaurant-service container
       heap_used, gc_duration, thread_count, etc.
       OTEL Agent exports metrics via OTLP

 8     Response flows back:                                    restaurant-svc --> api-gateway
       restaurant-service --> api-gateway --> curl
       Spans are marked with duration and status

 9     All 7 services continuously send telemetry              All containers --> OTEL Collector
       via OTLP HTTP to otel-collector:4318

10     OTEL Collector RECEIVES telemetry via OTLP              otel-collector container

11     OTEL Collector PROCESSES: batches data (up to           otel-collector container
       5s or 1024 items), adds service.namespace=ftgo

12     OTEL Collector EXPORTS:                                 otel-collector --> backends
         Traces  --> Tempo     (OTLP gRPC on :4317)
         Metrics --> Prometheus (exposed on :8889, scraped)
         Logs    --> Loki      (HTTP push to :3100)

13     Tempo stores the trace (all spans for abc123)           tempo container

14     Prometheus scrapes metrics from Collector every 15s     prometheus container

15     Loki indexes log labels and stores log content          loki container

16     Grafana queries all three backends on demand            grafana container (port 3001)
       You see the trace waterfall, metrics graphs,
       and log lines -- all correlated by traceId
```

---

## How to Run

### Prerequisites

- Docker Desktop (or Docker Engine + Docker Compose v2)
- At least **8 GB RAM** allocated to Docker (the full stack runs 16 containers)
- Required ports must be free (see [Port Reference](#port-reference))

### Start Everything

```bash
cd iteration-12-observability
docker compose up --build
```

First build takes **5-10 minutes** (Maven downloads, Docker image pulls). Subsequent starts
are faster due to Docker layer caching.

### Startup Order

The containers start in a controlled dependency order:

| Phase | Containers | Waits For |
|-------|-----------|-----------|
| 1 | kafka, eureka-server | -- |
| 2 | config-server | eureka-server healthy |
| 3 | tempo, loki, prometheus | -- |
| 4 | otel-collector | tempo, loki |
| 5 | All 7 application services | config-server healthy + otel-collector started |
| 6 | grafana | prometheus, tempo, loki |
| 7 | ftgo-web | api-gateway |

The system is ready when all services are registered in Eureka at http://localhost:8761.

### Stop Everything

```bash
docker compose down
```

To also remove persisted data (traces, metrics, logs, Grafana state):

```bash
docker compose down -v
```

---

## Accessing the UIs

| UI | URL | Credentials | Purpose |
|----|-----|-------------|---------|
| **Grafana** | [http://localhost:3001](http://localhost:3001) | admin / admin (or anonymous -- auto-login enabled) | Unified observability: traces, metrics, logs, correlation |
| **Prometheus** | [http://localhost:9090](http://localhost:9090) | None | Raw PromQL metric queries and scrape target status |
| **Eureka** | [http://localhost:8761](http://localhost:8761) | None | Service registry -- verify all services are registered |
| **Config Server** | [http://localhost:8888](http://localhost:8888) | None | Centralized configuration REST API |
| **FTGO Web** | [http://localhost:3000](http://localhost:3000) | None | Application UI (consumer, restaurant, courier views) |

> **Grafana is your primary tool.** It provides access to Tempo (traces), Prometheus
> (metrics), and Loki (logs) from a single interface.

---

## Hands-On Exercises

### Exercise 1: Explore Traces in Grafana (Tempo)

**Goal:** Find and read a distributed trace spanning multiple services.

**Step 1 -- Generate traffic:**

```bash
# Create a restaurant
curl -X POST http://localhost:8090/api/restaurants \
  -H "Content-Type: application/json" \
  -d '{"name":"Pizza Palace","address":"123 Main St","menuItems":[{"name":"Margherita","price":12.99}]}'

# Create an order
curl -X POST http://localhost:8090/api/orders \
  -H "Content-Type: application/json" \
  -d '{"restaurantId":1,"consumerId":1,"items":[{"menuItemId":1,"quantity":2}]}'

# List restaurants (simple GET)
curl http://localhost:8090/api/restaurants
```

**Step 2 -- Open Grafana:** Navigate to [http://localhost:3001](http://localhost:3001)

**Step 3 -- Open Explore:** Click the hamburger menu (top left) and select **Explore**

**Step 4 -- Select Tempo:** In the datasource dropdown (top left), select **Tempo**

**Step 5 -- Search for traces:** Change query type to **Search** tab. In the **Service Name**
dropdown, select `api-gateway`. Click **Run query**.

**Step 6 -- Read the waterfall:** Click on any trace to expand it. You will see:

```
api-gateway       GET /api/restaurants        ############################  250ms
  restaurant-svc    GET /restaurants          ########################      220ms
    JDBC              SELECT * FROM restaurant ####                          15ms
```

Each bar is a **span**. Click a span to see:
- Service name, operation name, duration
- HTTP method, URL, status code
- Any error messages or exceptions

---

### Exercise 2: Explore Logs in Grafana (Loki)

**Goal:** Search logs across all services using Loki's label-based queries.

**Step 1 -- Open Explore:** In Grafana, go to **Explore**

**Step 2 -- Select Loki:** In the datasource dropdown, select **Loki**

**Step 3 -- Query all logs from a service:**

```logql
{service_name="order-service"}
```

Click **Run query**. You will see all log lines from order-service, newest first.

**Step 4 -- Search for errors across all services:**

```logql
{service_name=~".+"} |= "ERROR"
```

**Step 5 -- Filter by traceId:**

If you found a traceId from Exercise 1 (e.g., `abc123def456`):

```logql
{service_name=~".+"} |= "abc123def456"
```

This shows **all log lines from all services** for that single request.

---

### Exercise 3: Explore Metrics in Grafana (Prometheus)

**Goal:** Query JVM and HTTP metrics for any service.

**Step 1 -- Open Explore:** In Grafana, go to **Explore**

**Step 2 -- Select Prometheus:** In the datasource dropdown, select **Prometheus**

**Step 3 -- Try these queries one at a time:**

**JVM Heap Memory (all services):**

```promql
jvm_memory_used_bytes{area="heap"}
```

**HTTP Request Rate (requests/second for order-service):**

```promql
rate(http_server_request_duration_seconds_count{service_name="order-service"}[5m])
```

**HTTP p95 Latency (order-service):**

```promql
histogram_quantile(0.95, rate(http_server_request_duration_seconds_bucket{service_name="order-service"}[5m]))
```

**Active JVM Threads (all services):**

```promql
jvm_thread_count
```

**Step 4 -- Switch to Graph view:** Click the graph/chart icon to see metrics over time
instead of as an instant value.

---

### Exercise 4: Trace-to-Log Correlation (The Money Shot)

**Goal:** Navigate from a trace to its logs and back -- the key capability that separate
tools cannot provide.

**Direction 1: Trace --> Logs**

1. Go to **Explore** --> **Tempo** --> **Search** --> find a trace with multiple spans
2. Click on the trace to open the waterfall view
3. Look for the **Logs for this span** link or the **Logs** tab in the trace detail panel
4. Grafana jumps to **Loki**, pre-filtered by the `traceId`
5. You now see **all log lines from all services** for that exact request

**Direction 2: Logs --> Trace**

1. Go to **Explore** --> **Loki**
2. Query: `{service_name="order-service"}`
3. Find an interesting log line
4. If the log line contains a traceId, click the **Tempo** link/button next to it
5. Grafana jumps to **Tempo** and shows the full distributed trace

**Direction 3: Metrics --> Trace (via Exemplars)**

1. Go to **Explore** --> **Prometheus**
2. Query a histogram metric with exemplars enabled
3. If exemplar data points appear on the graph, click one
4. Grafana jumps to **Tempo** showing the specific trace for that data point

> This cross-pillar navigation is what makes unified observability so powerful.
> You never have to copy-paste IDs between tools again.

---

## Useful Queries Reference

### Tempo (TraceQL)

| Query | What It Finds |
|-------|--------------|
| `{resource.service.name="order-service"}` | All traces from order-service |
| `{resource.service.name="api-gateway" && span.http.response.status_code=500}` | Failed requests at the gateway |
| `{resource.service.name="order-service" && duration>500ms}` | Slow requests (>500ms) |
| `{span.http.route="/api/orders" && duration>1s}` | Slow order creation requests |
| `{resource.service.namespace="ftgo"}` | All traces in the FTGO namespace |

### Loki (LogQL)

| Query | What It Finds |
|-------|--------------|
| `{service_name="order-service"}` | All logs from order-service |
| `{service_name=~".+"}  \|= "ERROR"` | Errors across all services |
| `{service_name=~".+"}  \|= "traceId=abc123"` | All logs for a specific trace |
| `{service_namespace="ftgo"}  \|= "timeout"` | Timeout messages across FTGO |
| `{service_name="order-service"}  \| logfmt  \| level="ERROR"` | Parsed error logs from order-service |
| `{service_name="kitchen-service"}  \|= "Kafka"` | Kafka-related logs from kitchen-service |
| `sum(rate({service_name=~".+"}  \|= "ERROR" [5m]))` | Error log rate across all services |

### Prometheus (PromQL)

#### JVM Metrics

| Query | What It Shows |
|-------|--------------|
| `jvm_memory_used_bytes{area="heap"}` | Heap memory used per service |
| `jvm_memory_committed_bytes{area="heap"}` | Heap memory committed per service |
| `rate(jvm_gc_duration_seconds_sum[5m])` | GC pause time (seconds per second) |
| `jvm_thread_count` | Active threads per service |
| `jvm_classes_loaded_classes` | Number of loaded classes |

#### HTTP Metrics

| Query | What It Shows |
|-------|--------------|
| `sum by (service_name) (rate(http_server_request_duration_seconds_count[5m]))` | Request rate per service (req/s) |
| `histogram_quantile(0.95, sum by (service_name, le) (rate(http_server_request_duration_seconds_bucket[5m])))` | p95 latency per service |
| `histogram_quantile(0.99, sum by (service_name, le) (rate(http_server_request_duration_seconds_bucket[5m])))` | p99 latency per service |
| `sum by (service_name) (rate(http_server_request_duration_seconds_count{http_response_status_code=~"5.."}[5m]))` | 5xx error rate per service |
| `histogram_quantile(0.95, sum by (http_route, le) (rate(http_server_request_duration_seconds_bucket{service_name="order-service"}[5m])))` | p95 latency by endpoint |

#### Kafka Metrics

| Query | What It Shows |
|-------|--------------|
| `kafka_consumer_records_lag_max` | Max consumer lag (messages behind) |
| `rate(kafka_producer_record_send_total[5m])` | Producer send rate |

---

## OTEL vs Previous Approach (Comparison)

| Aspect | Before (Zipkin + ELK Stack) | After (OTEL + Grafana Stack) |
|--------|---------------------------|------------------------------|
| **Traces** | Zipkin with Brave/Micrometer | OTEL Agent auto-instrumentation |
| **Logs** | Elasticsearch + Filebeat + Kibana | OTEL Agent --> Collector --> Loki |
| **Metrics** | Nothing -- no solution | OTEL Agent --> Collector --> Prometheus |
| **Java code changes** | Add micrometer-tracing-bridge-brave + zipkin-reporter to pom.xml, configure beans | Zero -- agent only, configured via env vars |
| **Number of SDKs/agents** | Brave SDK for traces + Filebeat sidecar for logs | One OTEL Java Agent for all 3 pillars |
| **Number of UIs** | 3 (Zipkin UI, Kibana, none for metrics) | 1 (Grafana) |
| **Trace-to-log correlation** | Manual: copy traceId from Zipkin, paste into Kibana | Built-in: click from trace to logs and back |
| **Metrics-to-trace correlation** | Not possible | Built-in: click exemplar to jump to trace |
| **Query languages** | Zipkin UI (limited), KQL (Kibana) | TraceQL, LogQL, PromQL -- consistent Grafana UX |
| **Memory footprint** | Elasticsearch alone: 2+ GB heap | Tempo + Loki + Prometheus together: under 1 GB |
| **Log storage approach** | Full-text indexing (expensive) | Label-only indexing (10x cheaper storage) |
| **Vendor lock-in** | Zipkin wire format, Elastic license changes | OTEL is CNCF standard; export to any vendor |
| **Backend switching** | Rewrite instrumentation code | Change Collector config file only |
| **Production adoption trend** | ELK declining, Zipkin niche | OTEL growing rapidly; CNCF graduated |
| **Wire protocol** | Zipkin format + Beats protocol | OTLP (universal standard) |

---

## Port Reference

| Port | Service | Protocol | Purpose |
|------|---------|----------|---------|
| 3000 | ftgo-web | HTTP | Next.js application UI |
| 3001 | Grafana | HTTP | Unified observability dashboard (traces, metrics, logs) |
| 3100 | Loki | HTTP | Log aggregation API |
| 3200 | Tempo | HTTP | Trace query API |
| 4317 | OTEL Collector | gRPC | OTLP receiver (gRPC) -- used by Tempo |
| 4318 | OTEL Collector | HTTP | OTLP receiver (HTTP) -- used by application services |
| 8080 | order-service | HTTP | Order management REST API |
| 8081 | restaurant-service | HTTP | Restaurant management REST API |
| 8082 | notification-service | HTTP | Notification REST API + Kafka consumer |
| 8083 | accounting-service | HTTP | Accounting REST API |
| 8084 | kitchen-service | HTTP | Kitchen REST API + Kafka producer |
| 8085 | delivery-service | HTTP | Delivery REST API + Kafka producer |
| 8090 | api-gateway | HTTP | Spring Cloud Gateway (entry point for all API calls) |
| 8761 | eureka-server | HTTP | Service registry dashboard and API |
| 8888 | config-server | HTTP | Centralized configuration REST API |
| 8889 | OTEL Collector | HTTP | Prometheus metrics exporter (scraped by Prometheus) |
| 9090 | Prometheus | HTTP | Metrics storage and PromQL query UI |
| 9092 | Kafka | TCP | Message broker |

---

## Architecture Decision Records

### ADR 1: Why OpenTelemetry over Alternatives?

**Decision:** Use OpenTelemetry for all instrumentation.

**Alternatives considered:**
- **Micrometer + Brave:** Spring-native, but traces only; no unified logs/metrics; requires pom.xml changes
- **Datadog/New Relic agents:** Proprietary; vendor lock-in; expensive at scale
- **AWS X-Ray SDK:** AWS-only; no logs/metrics integration

**Rationale:**
- CNCF graduated project (same maturity as Kubernetes)
- Covers all 3 pillars with a single agent
- Zero code changes via bytecode instrumentation
- Vendor-neutral: switch backends by changing a config file, not application code
- Industry momentum: adopted by all major cloud providers and observability vendors

### ADR 2: Why Grafana Stack over ELK?

**Decision:** Use Grafana + Tempo + Loki + Prometheus instead of Elasticsearch + Kibana + Zipkin.

**Alternatives considered:**
- **ELK Stack (Elasticsearch + Logstash + Kibana):** Established but resource-hungry
- **Datadog / Splunk:** Proprietary SaaS; expensive; not suitable for local dev

**Rationale:**
- **Unified UI:** One Grafana instance for traces, metrics, and logs (vs. 3 separate UIs)
- **Cross-correlation:** Built-in linking between pillars (click from trace to logs)
- **Resource efficiency:** Loki indexes labels only (vs. Elasticsearch full-text indexing); 10x less memory
- **Cost:** All components are open source under Apache 2.0 or AGPLv3

### ADR 3: Why Tempo over Jaeger?

**Decision:** Use Grafana Tempo for trace storage.

**Alternatives considered:**
- **Jaeger:** CNCF graduated; mature; own UI
- **Zipkin:** Simple; established; own UI

**Rationale:**
- Native integration with Grafana (no separate trace UI needed)
- Accepts OTLP directly (Jaeger requires an adapter for some features)
- Lower resource usage (no indexing required; traces stored as-is)
- Service graph and node graph built into Grafana via Tempo

### ADR 4: Why Loki over Elasticsearch?

**Decision:** Use Grafana Loki for log aggregation.

**Alternatives considered:**
- **Elasticsearch:** Full-text search; established; used in earlier iteration
- **CloudWatch Logs:** AWS-only

**Rationale:**
- **Label-based indexing** vs. full-text indexing: 10x less storage and memory
- Elasticsearch requires 2+ GB heap minimum; Loki runs comfortably with 256 MB
- LogQL is simpler than KQL for most microservice log queries
- Native Grafana integration with trace-to-log correlation
- No need for full-text search in typical microservice debugging (filter by service + grep)

---

## Key Takeaways

1. **Zero code changes.** The OTEL Java Agent provides complete auto-instrumentation via the
   `-javaagent` JVM flag. No dependencies in `pom.xml`, no annotations, no configuration
   classes. This is the single most important point of this iteration.

2. **One agent, three pillars.** A single OpenTelemetry Java Agent captures traces, metrics,
   AND logs from every service. Previously this required Brave SDK + Filebeat sidecar +
   nothing for metrics -- three tools for incomplete coverage.

3. **One UI, full correlation.** Grafana provides a single interface to query Tempo (traces),
   Prometheus (metrics), and Loki (logs) with built-in cross-linking. Click from a trace to
   its logs, from a log to its trace, from a metric spike to the traces that caused it.
   This workflow is impossible with fragmented tools.

4. **The Collector is the key abstraction.** The OTEL Collector decouples services from
   backends. Services send OTLP to one endpoint. The Collector routes traces to Tempo,
   metrics to Prometheus, and logs to Loki. Switching backends means changing a YAML file,
   not application code.

5. **Infrastructure-only changes scale.** Everything was configured via Dockerfiles (2 lines
   per service), environment variables (7 per service), and 5 YAML files. Adding a new
   service to the observability stack requires only the same 2 Dockerfile lines and 7 env
   vars -- no coordination with the observability team.

6. **OpenTelemetry is the industry standard.** OTEL is a CNCF graduated project with support
   from every major cloud provider and observability vendor. The same instrumentation works
   whether you export to Grafana, Datadog, New Relic, Splunk, or AWS X-Ray. This eliminates
   vendor lock-in and future-proofs your observability investment.

---

> _End of Iteration 12 training module._
>
> _Next: Iteration 13 will explore distributed transactions using the Saga pattern._
