# Iteration 11: Unified Observability with OpenTelemetry

## What Problem Does This Solve?

After iteration 10, we have 7 microservices + an API gateway + infrastructure (Eureka, Config Server, Kafka). The application works, but we are **flying blind** in production:

### Problem 1: Logs are trapped inside containers

With 7+ services running as Docker containers, there is no practical way to read logs. You would need to run `docker logs <container>` on each one individually. When a user reports "my order failed", which of the 7 containers do you check first? You end up running:

```bash
docker logs ftgo-order-service-1
docker logs ftgo-restaurant-service-1
docker logs ftgo-kitchen-service-1
docker logs ftgo-accounting-service-1
docker logs ftgo-notification-service-1
docker logs ftgo-delivery-service-1
docker logs ftgo-api-gateway-1
```

That is 7 commands, 7 terminal windows, and no way to correlate a single request across them.

### Problem 2: No distributed tracing

A single "create order" request flows through multiple services:

```
Browser → api-gateway → order-service → restaurant-service
                                       → accounting-service
                                       → kitchen-service → (Kafka) → notification-service
                                       → delivery-service
```

When this request fails or is slow, you have no way to answer:
- Which service caused the delay?
- How long did each hop take?
- Did the request even reach the downstream service?
- Was the Kafka message published? Was it consumed?

### Problem 3: No metrics visibility

You have zero visibility into:
- **JVM health**: Heap memory usage, garbage collection pauses, thread count
- **HTTP performance**: Request latency percentiles (p50, p95, p99), error rates, throughput
- **Kafka**: Consumer lag, message processing time
- **System**: CPU usage, connection pool utilization

Without metrics, you cannot answer "is the system healthy?" until users start complaining.

### Problem 4: Previous approach was fragmented

In earlier training iterations, we explored:
- **Zipkin** for distributed tracing (iteration 11 distributed-tracing)
- **ELK stack** (Elasticsearch + Filebeat + Kibana) for centralized logging (iteration 12)
- **Nothing** for metrics

This meant:
- 3 separate tools, 3 separate UIs, 3 separate query languages
- No correlation between traces and logs (you find a slow trace in Zipkin, then manually search Kibana by timestamp)
- No metrics at all
- Each tool requires its own integration code or agent
- High resource overhead (Elasticsearch alone needs 2+ GB RAM)

---

## Solution: OpenTelemetry + Grafana Stack

### The Three Pillars of Observability

Production microservices need three types of telemetry data:

| Pillar | What It Answers | Example |
|--------|----------------|---------|
| **Traces** | What path did this request take? How long did each hop take? | Order creation took 450ms: 20ms in gateway, 200ms in order-service, 180ms in restaurant-service, 50ms in accounting-service |
| **Metrics** | Is the system healthy right now? What are the trends? | order-service p99 latency is 320ms, JVM heap is at 78%, 12 requests/second |
| **Logs** | What happened in detail at a specific point? | `ERROR OrderService: Restaurant 42 not found for order 1001` |

The key insight: these three pillars are most powerful when **correlated**. You find a slow trace, click to see the logs for that exact request, then check if the metrics show a pattern.

### OpenTelemetry (OTEL)

OpenTelemetry is the CNCF standard for collecting telemetry data. It provides:

1. **A single standard** for traces, metrics, and logs (replaces Zipkin, Prometheus client libraries, and logging agents)
2. **Auto-instrumentation** via a Java agent that requires ZERO code changes
3. **Vendor-neutral** -- the same instrumentation works with any backend (Jaeger, Zipkin, Datadog, Grafana, etc.)

### OTEL Java Agent Auto-Instrumentation

The OpenTelemetry Java Agent (`opentelemetry-javaagent.jar`) attaches to your JVM at startup via the `-javaagent` flag. It automatically instruments:

- **Spring Web MVC / WebFlux**: Every incoming HTTP request becomes a trace span
- **RestTemplate / WebClient**: Every outgoing HTTP call becomes a child span
- **Spring Cloud Gateway**: Gateway routing creates spans
- **Kafka producer/consumer**: Message publish and consume create linked spans
- **JDBC**: Database queries become spans with SQL details
- **JVM internals**: Heap, GC, threads, class loading become metrics

**Zero code changes required.** You do not add any dependency to `pom.xml`, no annotations, no configuration classes. The agent does everything by bytecode manipulation at class load time.

### OTEL Collector

The OTEL Collector is a vendor-neutral telemetry pipeline that sits between your services and your backends. It:

1. **Receives** telemetry via OTLP (OpenTelemetry Protocol) from all services
2. **Processes** data (batching, enriching with resource attributes)
3. **Exports** to the appropriate backends (Tempo for traces, Prometheus for metrics, Loki for logs)

Why not send directly from services to backends? The Collector provides:
- A single endpoint for all services (simpler configuration)
- Batching and buffering (reduces network overhead)
- The ability to switch backends without changing any service configuration

### Grafana Stack (Tempo + Prometheus + Loki + Grafana)

| Component | Role | Replaces |
|-----------|------|----------|
| **Tempo** | Distributed trace storage and query | Zipkin |
| **Prometheus** | Time-series metrics storage and query | Nothing (we had no metrics before) |
| **Loki** | Log aggregation and query | ELK (Elasticsearch + Filebeat + Kibana) |
| **Grafana** | Unified UI for all three pillars | Zipkin UI + Kibana + (no metrics UI) |

The critical advantage: Grafana provides **built-in correlation** between traces, logs, and metrics. You can click from a trace to its logs, from a log line to its trace, and from a metric spike to the traces that caused it.

---

## What Changed from Iteration 10

**ZERO Java code changes.** Everything was done via Dockerfile modifications, docker-compose environment variables, and infrastructure configuration files.

### Files Changed

| File | Action | What Changed |
|------|--------|-------------|
| `order-service/Dockerfile` | MODIFIED | Added `ADD opentelemetry-javaagent.jar` + `-javaagent` flag to ENTRYPOINT |
| `restaurant-service/Dockerfile` | MODIFIED | Added `ADD opentelemetry-javaagent.jar` + `-javaagent` flag to ENTRYPOINT |
| `accounting-service/Dockerfile` | MODIFIED | Added `ADD opentelemetry-javaagent.jar` + `-javaagent` flag to ENTRYPOINT |
| `kitchen-service/Dockerfile` | MODIFIED | Added `ADD opentelemetry-javaagent.jar` + `-javaagent` flag to ENTRYPOINT |
| `notification-service/Dockerfile` | MODIFIED | Added `ADD opentelemetry-javaagent.jar` + `-javaagent` flag to ENTRYPOINT |
| `delivery-service/Dockerfile` | MODIFIED | Added `ADD opentelemetry-javaagent.jar` + `-javaagent` flag to ENTRYPOINT |
| `api-gateway/Dockerfile` | MODIFIED | Added `ADD opentelemetry-javaagent.jar` + `-javaagent` flag to ENTRYPOINT |
| `docker-compose.yml` | MODIFIED | Added OTEL env vars to 7 services + 5 new infrastructure containers |
| `config-repo/application.properties` | MODIFIED | Added actuator endpoint exposure (`health,info,prometheus`) |
| `otel-collector-config.yaml` | NEW | OTEL Collector pipeline: receivers, processors, exporters |
| `tempo-config.yaml` | NEW | Tempo trace storage configuration |
| `loki-config.yaml` | NEW | Loki log storage configuration |
| `prometheus.yml` | NEW | Prometheus scrape config (scrapes OTEL Collector) |
| `grafana/provisioning/datasources/datasources.yaml` | NEW | Pre-configures Tempo, Prometheus, Loki in Grafana with cross-linking |

### NOT Changed (Infrastructure Services)

`eureka-server/Dockerfile` and `config-server/Dockerfile` were **not modified**. These infrastructure services do not need observability instrumentation -- they are not part of the business request flow.

### Dockerfile Change (Same for All 7 Services)

**Before (Iteration 10):**
```dockerfile
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**After (Iteration 11):**
```dockerfile
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Download OpenTelemetry Java Agent for auto-instrumentation
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.11.0/opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar

EXPOSE 8080
ENTRYPOINT ["java", "-javaagent:/otel/opentelemetry-javaagent.jar", "-jar", "app.jar"]
```

Two lines added:
1. `ADD` downloads the OTEL Java Agent JAR from GitHub releases into `/otel/` inside the container
2. `-javaagent:/otel/opentelemetry-javaagent.jar` tells the JVM to load the agent at startup

### docker-compose.yml Changes (Per Service)

**Before (Iteration 10):**
```yaml
order-service:
  build: ./order-service
  ports:
    - "8080:8080"
  environment:
    SPRING_PROFILES_ACTIVE: docker
    SPRING_CONFIG_IMPORT: optional:configserver:http://config-server:8888
  depends_on:
    kafka:
      condition: service_healthy
    config-server:
      condition: service_healthy
```

**After (Iteration 11):**
```yaml
order-service:
  build: ./order-service
  ports:
    - "8080:8080"
  environment:
    SPRING_PROFILES_ACTIVE: docker
    SPRING_CONFIG_IMPORT: optional:configserver:http://config-server:8888
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
    otel-collector:
      condition: service_started
```

Seven environment variables added, plus a dependency on `otel-collector`. These environment variables are read by the OTEL Java Agent at startup -- no Spring configuration needed.

| Environment Variable | Value | Purpose |
|---------------------|-------|---------|
| `OTEL_SERVICE_NAME` | `order-service` | Identifies this service in traces, metrics, and logs |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://otel-collector:4318` | Where to send telemetry (OTEL Collector HTTP endpoint) |
| `OTEL_EXPORTER_OTLP_PROTOCOL` | `http/protobuf` | Use HTTP transport with protobuf encoding |
| `OTEL_LOGS_EXPORTER` | `otlp` | Export logs via OTLP to the Collector |
| `OTEL_METRICS_EXPORTER` | `otlp` | Export metrics via OTLP to the Collector |
| `OTEL_TRACES_EXPORTER` | `otlp` | Export traces via OTLP to the Collector |
| `OTEL_RESOURCE_ATTRIBUTES` | `service.namespace=ftgo` | Tags all telemetry with namespace=ftgo for filtering |

### config-repo/application.properties Change

**Added (2 lines):**
```properties
# Actuator -- expose health and Prometheus endpoints
management.endpoints.web.exposure.include=health,info,prometheus
management.endpoint.health.show-details=always
```

This exposes the Spring Boot Actuator health and Prometheus endpoints for all services. The `prometheus` endpoint is useful for direct scraping if needed, though in this setup metrics flow through the OTEL Agent.

---

## Architecture

### High-Level Data Flow

```
                         FTGO Application
    ┌──────────────────────────────────────────────────────┐
    │                                                      │
    │  ┌─────────────┐  ┌──────────────────┐               │
    │  │ api-gateway  │  │  order-service   │               │
    │  │  (8090)      │  │  (8080)          │               │
    │  └──────┬───────┘  └────────┬─────────┘               │
    │         │                   │                         │
    │  ┌──────┴──────┐  ┌────────┴────────┐                │
    │  │ restaurant  │  │ accounting      │                │
    │  │  (8081)     │  │  (8083)         │                │
    │  └─────────────┘  └─────────────────┘                │
    │                                                      │
    │  ┌─────────────┐  ┌──────────────────┐               │
    │  │ kitchen     │  │ delivery         │               │
    │  │  (8084)     │  │  (8085)          │               │
    │  └──────┬──────┘  └─────────────────┘                │
    │         │ Kafka                                      │
    │  ┌──────┴──────┐                                     │
    │  │notification │                                     │
    │  │  (8082)     │                                     │
    │  └─────────────┘                                     │
    │                                                      │
    │  Each service runs with OTEL Java Agent              │
    │  (-javaagent:/otel/opentelemetry-javaagent.jar)      │
    │  Sends traces + metrics + logs via OTLP              │
    └──────────────────────┬───────────────────────────────┘
                           │
                    OTLP (HTTP :4318)
                           │
                ┌──────────┴──────────┐
                │   OTEL Collector    │
                │ (otel-collector)    │
                │                     │
                │ receivers:  OTLP    │
                │ processors: batch   │
                │ exporters:          │
                │   traces  → Tempo   │
                │   metrics → Prom    │
                │   logs    → Loki    │
                └───┬─────┬─────┬────┘
                    │     │     │
            ┌───────┘     │     └───────┐
            │             │             │
    ┌───────┴───────┐ ┌───┴────┐ ┌──────┴──────┐
    │    Tempo      │ │Prometh.│ │    Loki     │
    │  (traces)     │ │(metrics│ │   (logs)    │
    │  port 3200    │ │  9090) │ │  port 3100  │
    └───────┬───────┘ └───┬────┘ └──────┬──────┘
            │             │             │
            └─────────────┼─────────────┘
                          │
                ┌─────────┴─────────┐
                │     Grafana       │
                │   port 3001       │
                │                   │
                │ Unified UI for:   │
                │  - Traces (Tempo) │
                │  - Metrics (Prom) │
                │  - Logs (Loki)    │
                │                   │
                │ Cross-linking:    │
                │  trace → logs     │
                │  logs → trace     │
                │  metrics → traces │
                └───────────────────┘
```

### OTEL Collector Pipeline

```
    ┌─────────────────────────────────────────────────────┐
    │                  OTEL Collector                      │
    │                                                     │
    │  RECEIVERS          PROCESSORS         EXPORTERS    │
    │  ┌──────────┐      ┌───────────┐                    │
    │  │   OTLP   │      │   batch   │   ┌────────────┐  │
    │  │  gRPC    │─────>│  (5s /    │──>│ otlp/tempo │──── → Tempo (:4317)
    │  │  :4317   │      │  1024     │   └────────────┘  │
    │  │          │      │  items)   │                    │
    │  │   OTLP   │      │           │   ┌────────────┐  │
    │  │  HTTP    │─────>│  resource │──>│ prometheus │──── → Prometheus scrapes :8889
    │  │  :4318   │      │  (add     │   └────────────┘  │
    │  └──────────┘      │ namespace)│                    │
    │                    │           │   ┌────────────┐  │
    │                    │           │──>│    loki    │──── → Loki (:3100)
    │                    └───────────┘   └────────────┘  │
    │                                                     │
    │  Pipelines:                                         │
    │    traces:  otlp → batch,resource → otlp/tempo     │
    │    metrics: otlp → batch,resource → prometheus      │
    │    logs:    otlp → batch,resource → loki            │
    └─────────────────────────────────────────────────────┘
```

### Docker Compose Service Map

```
docker compose up --build
├── kafka (9092)                          Infrastructure
├── eureka-server (8761)                  Infrastructure
├── config-server (8888)                  Infrastructure
│
├── otel-collector (4317, 4318, 8889)     Observability ← NEW
├── tempo (3200)                          Observability ← NEW
├── loki (3100)                           Observability ← NEW
├── prometheus (9090)                     Observability ← NEW
├── grafana (3001)                        Observability ← NEW
│
├── restaurant-service (8081)  ──┐
├── accounting-service (8083)    │
├── kitchen-service (8084)       ├─ OTEL Java Agent attached
├── notification-service (8082)  │  (traces + metrics + logs → Collector)
├── delivery-service (8085)      │
├── order-service (8080)         │
├── api-gateway (8090)         ──┘
│
└── ftgo-web (3000)                       Frontend (no instrumentation)
```

---

## How to Run

### Prerequisites

- Docker Desktop (or Docker Engine + Docker Compose v2)
- At least **8 GB RAM** allocated to Docker (the full stack runs 15 containers)
- Ports 3000, 3001, 3100, 3200, 4317, 4318, 8080-8085, 8090, 8761, 8888, 8889, 9090, 9092 must be free

### Start Everything

```bash
cd iteration-11-observability
docker compose up --build
```

First build takes 5-10 minutes (Maven downloads, Docker image pulls). Subsequent starts are faster due to Docker layer caching.

### Wait for Health Checks

Watch the logs. The startup order is:
1. `kafka` and `eureka-server` start in parallel
2. `config-server` starts after eureka-server is healthy
3. `otel-collector`, `tempo`, `loki`, `prometheus` start in parallel
4. All 7 application services start after `config-server` is healthy and `otel-collector` has started
5. `grafana` starts after `prometheus`, `tempo`, and `loki` are running
6. `ftgo-web` starts after `api-gateway` is running

The system is ready when you see all services registered in Eureka at http://localhost:8761.

### Stop Everything

```bash
docker compose down
```

To also remove persisted data (Grafana dashboards, Prometheus metrics, Tempo traces, Loki logs):

```bash
docker compose down -v
```

---

## Accessing the UIs

| UI | URL | Credentials | Purpose |
|----|-----|-------------|---------|
| **Grafana** | http://localhost:3001 | admin / admin (or anonymous) | Unified observability: traces, metrics, logs |
| **Prometheus** | http://localhost:9090 | None | Raw PromQL metric queries |
| **Eureka** | http://localhost:8761 | None | Service registry dashboard |
| **Config Server** | http://localhost:8888 | None | Centralized config REST API |
| **FTGO Web** | http://localhost:3000 | None | Application UI (consumer/restaurant/courier views) |

---

## Try It Out

### Step 1: Generate Traffic

Open http://localhost:3000 (FTGO Web) and create some activity:

**Option A: Via the UI**
1. Go to the Consumer view, pick a restaurant, and place an order
2. Go to the Restaurant view, accept the ticket, mark as preparing, mark as ready
3. Go to the Courier view, assign a courier, pick up, deliver

**Option B: Via curl**
```bash
# Create a restaurant
curl -X POST http://localhost:8090/api/restaurants \
  -H "Content-Type: application/json" \
  -d '{"name":"Pizza Palace","address":"123 Main St","menuItems":[{"name":"Margherita","price":12.99}]}'

# Create an order (use the restaurantId from the response above)
curl -X POST http://localhost:8090/api/orders \
  -H "Content-Type: application/json" \
  -d '{"restaurantId":1,"consumerId":1,"items":[{"menuItemId":1,"quantity":2}]}'

# Check order status
curl http://localhost:8090/api/orders/1
```

Generate a few orders so there is enough telemetry data to explore.

### Step 2: Explore Distributed Traces in Grafana (Tempo)

1. Open **Grafana** at http://localhost:3001
2. Click the **hamburger menu** (three lines, top left) and select **Explore**
3. In the datasource dropdown (top left), select **Tempo**
4. Change the query type to **Search** (tab at the top)
5. In the **Service Name** dropdown, select `api-gateway` or `order-service`
6. Click **Run query**
7. You will see a list of traces. Click on any trace to expand it.

**What you will see in the trace waterfall:**

```
api-gateway       GET /api/orders           ████████████████████████████  450ms
  order-service     GET /api/orders         ██████████████████████████  420ms
    restaurant-svc    GET /restaurants/1    ██████████████  180ms
    accounting-svc    POST /accounts/auth   ████████  120ms
    kitchen-svc       POST /tickets         ██████  90ms
      kafka-produce   order-events          ██  15ms
```

Each bar is a **span**. The trace shows the full call chain, timing, and any errors. This is the same information Zipkin provided, but now integrated with logs and metrics.

### Step 3: Correlate Traces with Logs (Tempo to Loki)

1. While viewing a trace in Tempo, look for the **Logs for this span** link (or click the "Logs" tab in the trace detail panel)
2. This jumps to Loki, pre-filtered by the `traceId`
3. You will see all log lines from all services that participated in that exact request

Alternatively, search logs directly:

1. In the **Explore** view, switch the datasource to **Loki**
2. Use the label browser to filter by service: `{service_name="order-service"}`
3. Add a text filter: `{service_name="order-service"} |= "error"` (find errors)
4. If you find an interesting log line with a traceID, click the **Tempo** link to jump to the full trace

### Step 4: Explore Metrics (Prometheus)

1. In the **Explore** view, switch the datasource to **Prometheus**
2. Try these queries:

**JVM Heap Memory:**
```promql
jvm_memory_used_bytes{service_name="order-service", area="heap"}
```

**HTTP Request Rate (requests per second):**
```promql
rate(http_server_request_duration_seconds_count{service_name="order-service"}[5m])
```

**HTTP Request Latency (p95):**
```promql
histogram_quantile(0.95, rate(http_server_request_duration_seconds_bucket{service_name="order-service"}[5m]))
```

**JVM Thread Count:**
```promql
jvm_thread_count{service_name="order-service"}
```

**All Services HTTP Error Rate:**
```promql
rate(http_server_request_duration_seconds_count{http_response_status_code=~"5.."}[5m])
```

### Step 5: Cross-Pillar Correlation

This is the key advantage over separate tools. Try this workflow:

1. **Start with metrics**: In Prometheus, notice a spike in p95 latency for `order-service`
2. **Drill into traces**: Click on an exemplar point (if available) to jump to the specific trace that was slow
3. **Read the logs**: From the trace, click through to Loki to see the detailed logs for that request
4. **Root cause**: The logs might show "Connection timeout to restaurant-service", the trace confirms restaurant-service took 5 seconds, and metrics show restaurant-service GC pauses at that time

This is the **observe → hypothesize → confirm** workflow that is impossible with fragmented tools.

---

## Key Concepts

### What is OpenTelemetry?

OpenTelemetry (OTEL) is the merger of two earlier CNCF projects: OpenTracing and OpenCensus. It is now the **second most active CNCF project** (after Kubernetes). It provides:

- **Specification**: Standard definitions for traces, metrics, and logs
- **APIs and SDKs**: Libraries for every major language (Java, Go, Python, .NET, Node.js, etc.)
- **Auto-instrumentation agents**: Language-specific agents that instrument frameworks automatically
- **Collector**: A vendor-neutral telemetry pipeline
- **OTLP (OpenTelemetry Protocol)**: A standard wire protocol for transmitting telemetry

OTEL is **vendor-neutral**. The same instrumentation works whether you export to Grafana, Datadog, New Relic, Splunk, or AWS X-Ray. You configure the exporter, not the instrumentation.

### What is Auto-Instrumentation?

The OTEL Java Agent uses **bytecode manipulation** (via Java's `java.lang.instrument` API and the `-javaagent` JVM flag) to intercept method calls in well-known frameworks at class load time. This means:

- No `@Traced` annotations on your code
- No `opentelemetry-sdk` dependency in `pom.xml`
- No `TracerProvider` bean configuration
- No `MDC.put("traceId", ...)` calls in your logging

The agent recognizes and instruments:

| Framework | What Gets Instrumented |
|-----------|----------------------|
| Spring Web MVC | Incoming HTTP requests → trace spans |
| Spring WebFlux | Reactive HTTP requests → trace spans |
| Spring Cloud Gateway | Route matching and forwarding → trace spans |
| RestTemplate | Outgoing HTTP calls → child spans with trace context propagation |
| WebClient | Outgoing reactive HTTP calls → child spans |
| Apache Kafka Producer | Message publish → span with trace context in Kafka headers |
| Apache Kafka Consumer | Message consume → linked span |
| JDBC | SQL queries → spans with query text |
| Logback / Log4j2 | Log lines → exported as OTEL log records with traceId/spanId |
| JVM Runtime | Heap, GC, threads, class loading → OTEL metrics |

### OTEL Collector Pipeline: Receivers, Processors, Exporters

The Collector configuration (`otel-collector-config.yaml`) defines three pipeline stages:

**Receivers** -- How data enters the Collector:
```yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317    # gRPC endpoint
      http:
        endpoint: 0.0.0.0:4318    # HTTP endpoint (used by our services)
```

**Processors** -- How data is transformed in-flight:
```yaml
processors:
  batch:
    timeout: 5s                   # Batch data for up to 5 seconds
    send_batch_size: 1024         # Or until 1024 items accumulate
  resource:
    attributes:
      - key: service.namespace
        value: ftgo
        action: upsert            # Add namespace tag to all telemetry
```

**Exporters** -- Where data is sent:
```yaml
exporters:
  otlp/tempo:                     # Traces → Tempo via OTLP gRPC
    endpoint: tempo:4317
  prometheus:                     # Metrics → exposed on :8889 for Prometheus to scrape
    endpoint: 0.0.0.0:8889
  loki:                           # Logs → pushed to Loki via HTTP
    endpoint: http://loki:3100/loki/api/v1/push
```

**Pipelines** -- Which receiver/processor/exporter combinations to use:
```yaml
service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [batch, resource]
      exporters: [otlp/tempo]
    metrics:
      receivers: [otlp]
      processors: [batch, resource]
      exporters: [prometheus]
    logs:
      receivers: [otlp]
      processors: [batch, resource]
      exporters: [loki]
```

Each pipeline is independent. Traces flow through one path, metrics through another, logs through a third. The Collector fans out a single OTLP stream into three backend-specific outputs.

### Trace-to-Log Correlation

The OTEL Java Agent automatically injects `traceId` and `spanId` into every log line via the SLF4J MDC (Mapped Diagnostic Context). When those log lines are exported to Loki, the trace context travels with them.

In Grafana, this correlation is pre-configured in `datasources.yaml`:

**Tempo to Loki** (click from a trace to see its logs):
```yaml
- name: Tempo
  jsonData:
    tracesToLogsV2:
      datasourceUid: loki
      filterByTraceID: true
```

**Loki to Tempo** (click from a log line to see the trace):
```yaml
- name: Loki
  jsonData:
    derivedFields:
      - name: TraceID
        datasourceUid: tempo
        matcherRegex: "traceID=(\\w+)"
```

**Prometheus to Tempo** (click from a metric exemplar to see the trace):
```yaml
- name: Prometheus
  jsonData:
    exemplarTraceIdDestinations:
      - name: traceID
        datasourceUid: tempo
```

This cross-linking is what makes the Grafana stack superior to running separate tools.

### Why Grafana Stack over Zipkin + ELK?

| Concern | Zipkin + ELK | Grafana Stack (OTEL + Tempo + Loki + Prometheus) |
|---------|-------------|-----------------------------------------------|
| **Number of tools** | 5 (Zipkin, Elasticsearch, Logstash/Filebeat, Kibana, plus something for metrics) | 5 (OTEL Collector, Tempo, Loki, Prometheus, Grafana) |
| **Number of UIs** | 3 (Zipkin UI, Kibana, metrics tool UI) | 1 (Grafana) |
| **Trace-log correlation** | Manual (copy traceId from Zipkin, paste into Kibana) | Built-in (click from trace to logs and back) |
| **Metrics** | Not included, need separate tool | Prometheus built-in, correlated with traces |
| **Instrumentation** | Micrometer Tracing + Brave for traces, Filebeat sidecar for logs | Single OTEL Java Agent for all three pillars |
| **Code changes** | Add micrometer-tracing-bridge-brave + zipkin-reporter to pom.xml, configure beans | Zero -- agent-only, configured via env vars |
| **Memory footprint** | Elasticsearch alone needs 2+ GB heap | Tempo + Loki + Prometheus together use under 1 GB |
| **Log storage** | Elasticsearch indexes full text (expensive) | Loki indexes only labels (10x cheaper storage) |
| **Vendor lock-in** | Elastic license changes, Zipkin is niche | All CNCF/OSS, OTEL is the industry standard |
| **Query language** | Kibana KQL for logs, Zipkin UI for traces | LogQL (Loki), PromQL (Prometheus), TraceQL (Tempo) -- consistent Grafana UX |
| **Production adoption** | Declining (ELK) | Growing rapidly (Grafana Cloud is used by thousands of companies) |

---

## Port Reference

| Port | Service | Protocol | Purpose |
|------|---------|----------|---------|
| 3000 | ftgo-web | HTTP | Next.js application UI |
| 3001 | Grafana | HTTP | Observability dashboards (traces, metrics, logs) |
| 3100 | Loki | HTTP | Log aggregation API |
| 3200 | Tempo | HTTP | Trace query API |
| 4317 | OTEL Collector | gRPC | OTLP receiver (gRPC) |
| 4318 | OTEL Collector | HTTP | OTLP receiver (HTTP) -- used by services |
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

## Configuration File Reference

### otel-collector-config.yaml

The central pipeline configuration. Defines how telemetry flows from services to backends.

```yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318

processors:
  batch:
    timeout: 5s
    send_batch_size: 1024
  resource:
    attributes:
      - key: service.namespace
        value: ftgo
        action: upsert

exporters:
  otlp/tempo:
    endpoint: tempo:4317
    tls:
      insecure: true
  prometheus:
    endpoint: 0.0.0.0:8889
  loki:
    endpoint: http://loki:3100/loki/api/v1/push

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [batch, resource]
      exporters: [otlp/tempo]
    metrics:
      receivers: [otlp]
      processors: [batch, resource]
      exporters: [prometheus]
    logs:
      receivers: [otlp]
      processors: [batch, resource]
      exporters: [loki]
```

### tempo-config.yaml

Grafana Tempo stores traces. Configured for local filesystem storage (suitable for development; production would use S3 or GCS).

```yaml
server:
  http_listen_port: 3200

distributor:
  receivers:
    otlp:
      protocols:
        grpc:
          endpoint: 0.0.0.0:4317

storage:
  trace:
    backend: local
    local:
      path: /var/tempo/traces
    wal:
      path: /var/tempo/wal
```

### loki-config.yaml

Grafana Loki stores logs. Unlike Elasticsearch, Loki only indexes labels (service name, log level), not the full log text. This makes it dramatically cheaper to run.

```yaml
auth_enabled: false

server:
  http_listen_port: 3100

common:
  path_prefix: /loki
  storage:
    filesystem:
      chunks_directory: /loki/chunks
      rules_directory: /loki/rules
  replication_factor: 1
  ring:
    kvstore:
      store: inmemory

schema_config:
  configs:
    - from: 2020-10-24
      store: tsdb
      object_store: filesystem
      schema: v13
      index:
        prefix: index_
        period: 24h

limits_config:
  allow_structured_metadata: true
  volume_enabled: true
```

### prometheus.yml

Prometheus scrapes metrics from the OTEL Collector's Prometheus exporter endpoint every 15 seconds.

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: "otel-collector"
    static_configs:
      - targets: ["otel-collector:8889"]
```

### grafana/provisioning/datasources/datasources.yaml

Pre-configures all three datasources with cross-linking so Grafana is ready to use on first startup with no manual setup.

```yaml
apiVersion: 1

datasources:
  - name: Tempo
    type: tempo
    access: proxy
    url: http://tempo:3200
    jsonData:
      tracesToLogsV2:
        datasourceUid: loki
        filterByTraceID: true
      tracesToMetrics:
        datasourceUid: prometheus
      nodeGraph:
        enabled: true
      serviceMap:
        datasourceUid: prometheus

  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    jsonData:
      exemplarTraceIdDestinations:
        - name: traceID
          datasourceUid: tempo

  - name: Loki
    type: loki
    access: proxy
    url: http://loki:3100
    uid: loki
    jsonData:
      derivedFields:
        - name: TraceID
          datasourceUid: tempo
          matcherRegex: "traceID=(\\w+)"
          url: "$${__value.raw}"
```

---

## Comparison: What We Had vs. What We Have Now

| Capability | Before (Iterations 10 and earlier) | After (Iteration 11) |
|-----------|-----------------------------------|---------------------|
| View request flow across services | Not possible | Grafana → Tempo: full trace waterfall |
| Find why a request was slow | SSH into each container, grep logs | Trace shows exactly which service/span was slow |
| Search logs across all services | Not possible (or docker logs per container) | Grafana → Loki: `{service_name=~".+"}` searches all |
| Find logs for a specific request | Not possible | Grafana → Loki: filter by traceId |
| Monitor JVM heap memory | Not possible | Grafana → Prometheus: `jvm_memory_used_bytes` |
| Monitor HTTP latency percentiles | Not possible | Grafana → Prometheus: `histogram_quantile(0.95, ...)` |
| Monitor error rates | Not possible | Grafana → Prometheus: `rate(...{status_code=~"5.."}[5m])` |
| Correlate traces with logs | Not possible | Click from trace to logs and back |
| Java code changes required | N/A | Zero |
| Number of UIs to check | 0 (no observability) | 1 (Grafana) |

---

## Useful PromQL Queries for FTGO

Here are practical Prometheus queries you can use in Grafana to monitor the FTGO application.

### JVM Metrics

```promql
# Heap memory used per service
jvm_memory_used_bytes{area="heap"}

# GC pause time (seconds per second)
rate(jvm_gc_duration_seconds_sum[5m])

# Active threads per service
jvm_thread_count

# Classes loaded
jvm_classes_loaded_classes
```

### HTTP Metrics

```promql
# Request rate per service (requests/second)
sum by (service_name) (rate(http_server_request_duration_seconds_count[5m]))

# p95 latency per service
histogram_quantile(0.95, sum by (service_name, le) (rate(http_server_request_duration_seconds_bucket[5m])))

# Error rate (5xx responses)
sum by (service_name) (rate(http_server_request_duration_seconds_count{http_response_status_code=~"5.."}[5m]))

# Latency by endpoint
histogram_quantile(0.95, sum by (http_route, le) (rate(http_server_request_duration_seconds_bucket{service_name="order-service"}[5m])))
```

### Kafka Metrics

```promql
# Kafka consumer lag
kafka_consumer_records_lag_max

# Kafka producer send rate
rate(kafka_producer_record_send_total[5m])
```

---

## Useful LogQL Queries for FTGO

Here are practical Loki queries you can use in Grafana to search logs.

```logql
# All logs from order-service
{service_name="order-service"}

# Errors across all services
{service_name=~".+"} |= "ERROR"

# Logs for a specific traceId
{service_name=~".+"} |= "traceId=abc123def456"

# Logs from order-service containing "restaurant"
{service_name="order-service"} |= "restaurant"

# All logs in the ftgo namespace
{service_namespace="ftgo"}

# Parse and filter by log level
{service_name="order-service"} | logfmt | level="ERROR"
```

---

## Troubleshooting

### Services are not sending telemetry

1. Check if the OTEL Collector is running: `docker compose logs otel-collector`
2. Look for "Exporting failed" messages in the Collector logs
3. Verify the `OTEL_EXPORTER_OTLP_ENDPOINT` matches the Collector's address (`http://otel-collector:4318`)
4. Verify the OTEL Java Agent is loaded: service logs should show `[otel.javaagent]` lines at startup

### No traces in Tempo

1. Open Grafana → Explore → Tempo → Search with no filters → Run query
2. If empty, check: `docker compose logs tempo` for errors
3. Verify the Collector's trace pipeline exports to `tempo:4317`
4. Generate fresh traffic and wait 10-15 seconds (batching delay)

### No logs in Loki

1. Open Grafana → Explore → Loki → Label browser → check if `service_name` labels exist
2. If empty, check: `docker compose logs loki` for errors
3. Verify `OTEL_LOGS_EXPORTER=otlp` is set in docker-compose for each service
4. Check `docker compose logs otel-collector` for "loki" exporter errors

### No metrics in Prometheus

1. Open http://localhost:9090/targets -- verify the `otel-collector` target is UP
2. If the target is DOWN, check that the Collector is exposing port 8889
3. Try a basic query in Prometheus: `up` -- should return at least one result
4. Check `docker compose logs otel-collector` for "prometheus" exporter errors

### Grafana shows "No data"

1. Verify the datasource is configured: Grafana → Settings (gear icon) → Data sources
2. Click "Test" on each datasource to verify connectivity
3. Ensure you are selecting the correct time range (default is "Last 1 hour" -- if the system just started, try "Last 15 minutes")

---

## Summary

Iteration 11 adds **unified observability** to the FTGO microservices application. The key takeaways:

1. **Zero code changes**: The OTEL Java Agent provides auto-instrumentation via the `-javaagent` JVM flag. No dependencies added to `pom.xml`, no annotations, no configuration classes.

2. **Single pipeline**: The OTEL Collector receives all telemetry (traces, metrics, logs) from all services via OTLP and routes it to the appropriate backends.

3. **Three backends, one UI**: Tempo (traces), Prometheus (metrics), and Loki (logs) each store one type of telemetry. Grafana provides a single unified interface to query and correlate all three.

4. **Cross-pillar correlation**: Grafana's datasource provisioning enables clicking from a trace to its logs, from a log line to its trace, and from a metric exemplar to the trace that produced it. This is the critical capability that separate tools (Zipkin + ELK) cannot provide.

5. **Infrastructure-only changes**: Everything was configured via Dockerfiles (2 lines per service), docker-compose environment variables (7 per service), and 5 YAML configuration files. This pattern scales to any number of services without touching application code.

6. **Industry standard**: OpenTelemetry is the CNCF standard for observability. The same instrumentation works with any vendor backend (Grafana Cloud, Datadog, New Relic, AWS X-Ray). This avoids vendor lock-in.
