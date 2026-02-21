# Iteration 11 -- Observability with OpenTelemetry

A simplified teaching demo: 2 services + API Gateway, fully instrumented with
OpenTelemetry. Same OTEL stack (Collector, Tempo, Loki, Prometheus, Grafana) as
the full FTGO version, but with fewer moving parts so you can focus on how the
observability pipeline works.

---

## What This Demo Shows

- **OpenTelemetry auto-instrumentation with ZERO code changes** -- a single
  Java agent JAR, attached at JVM startup, automatically captures traces, logs,
  and metrics from Spring Boot, RestTemplate, and the Netty-based Gateway.
- **Distributed tracing across 3 services** -- one request through the API
  Gateway fans out to time-service, which calls greeting-service. Tempo shows
  the full span tree.
- **Centralized logs searchable by traceId** -- every log line emitted by any
  service is shipped to Loki with the traceId attached, so you can jump from a
  trace to the exact log lines that produced it.
- **JVM and HTTP metrics** -- the OTEL agent exports `http_server_request_duration_seconds`,
  JVM memory/GC counters, and more to Prometheus via the Collector.

---

## Architecture

```
                        +-------------------+
                        |     Grafana       |  :3001
                        |  (dashboards)     |
                        +--------+----------+
                                 |
              +------------------+------------------+
              |                  |                   |
        +-----+-----+    +------+------+    +-------+------+
        |   Tempo    |    | Prometheus  |    |    Loki      |
        |  (traces)  |    |  (metrics)  |    |   (logs)     |
        +-----+------+    +------+------+    +-------+------+
              |                  |                   |
              +------------------+------------------+
                                 |
                        +--------+----------+
                        |  OTEL Collector   |  :4318 (HTTP)
                        |  receives all     |  :4317 (gRPC)
                        |  telemetry        |  :8889 (prom export)
                        +--------+----------+
                                 ^
             OTLP/HTTP from all three services
                                 |
              +------------------+------------------+
              |                  |                   |
        +-----+-----+    +------+------+    +-------+------+
        | API Gateway|    |  greeting   |    |    time      |
        |   :9000    |    |  service    |    |   service    |
        |            |    |   :9001     |    |    :9002     |
        +-----+------+    +------+------+    +-------+------+
              ^                  ^                   |
              |                  +-------------------+
         User request             RestTemplate call
         (curl)                   /time/with-greeting
                                  calls /greeting
```

**Request flow:** User --> API Gateway (:9000) --> time-service (:9002) --> greeting-service (:9001)

The OTEL Java agent on each service sends traces, metrics, and logs to the
OTEL Collector, which routes them to the three backends. Grafana queries all
three for a unified observability view.

---

## How to Run

```bash
cd simple-demos/iteration-11-observability
docker compose up --build
```

First startup takes a few minutes (Maven builds + health checks). Wait until
you see all services registered in Eureka before testing.

To stop and clean up:

```bash
docker compose down -v
```

---

## Accessing the UIs

| Service            | URL                        |
|--------------------|----------------------------|
| Grafana            | http://localhost:3001       |
| Prometheus         | http://localhost:9090       |
| Eureka             | http://localhost:8761       |
| API Gateway        | http://localhost:9000       |
| Greeting Service   | http://localhost:9001       |
| Time Service       | http://localhost:9002       |

Grafana credentials: `admin` / `admin` (or anonymous -- auto-configured).

---

## Try It

### 1. Generate traffic

```bash
# Simple greeting (Gateway -> greeting-service)
curl http://localhost:9000/greeting

# Current time (Gateway -> time-service)
curl http://localhost:9000/time

# Time + greeting (Gateway -> time-service -> greeting-service)
# This is the best one for seeing a multi-hop trace
curl http://localhost:9000/time/with-greeting
```

### 2. Find a distributed trace in Tempo

1. Open **Grafana** at http://localhost:3001.
2. Go to **Explore** (compass icon in the left sidebar).
3. Select **Tempo** as the data source.
4. Choose the **Search** tab, pick `api-gateway` as the Service Name, and click
   **Run query**.
5. Click on any trace to open it. You will see the full span chain:
   ```
   api-gateway  -->  time-service  -->  greeting-service
   ```
   Each span shows its HTTP method, URL, status code, and duration.

### 3. Jump from a trace to logs in Loki

1. From the trace view, copy the **traceId** (shown at the top of the trace
   panel).
2. Switch the data source to **Loki**.
3. Run this query, pasting your traceId:
   ```
   {service_name=~".+"} |= "<your-traceId>"
   ```
   You will see correlated log lines from all three services that participated
   in that request.

### 4. Query HTTP metrics in Prometheus

1. Switch the data source to **Prometheus**.
2. Try these queries:
   ```promql
   # Total request count by service and HTTP route
   http_server_request_duration_seconds_count

   # Average request duration for the gateway
   rate(http_server_request_duration_seconds_sum{service_name="api-gateway"}[5m])
     /
   rate(http_server_request_duration_seconds_count{service_name="api-gateway"}[5m])

   # JVM memory usage
   jvm_memory_used_bytes
   ```

---

## What Changed from Iteration 10

Iteration 10 had the same 3 services + Eureka + Config Server, but **no
observability**. Here is everything that was added:

| What                           | Change                                                                                                  |
|--------------------------------|---------------------------------------------------------------------------------------------------------|
| **Dockerfiles** (3 services)   | +2 lines each: `ADD ...opentelemetry-javaagent.jar` and `-javaagent:` flag in `ENTRYPOINT`              |
| **docker-compose.yml**         | +5 infrastructure services (otel-collector, tempo, loki, prometheus, grafana) and OTEL env vars on each service |
| **config-repo/application.properties** | Added `management.endpoints.web.exposure.include=health,info,prometheus`                        |
| **New config files**           | `otel-collector-config.yaml`, `tempo-config.yaml`, `loki-config.yaml`, `prometheus.yml`, `grafana/provisioning/datasources/datasources.yaml` |
| **Java code**                  | **ZERO changes** -- not a single line of application code was modified                                  |

### The two Dockerfile lines that enable everything

```dockerfile
# Download the OTEL Java agent (added to the runtime stage)
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.11.0/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar

# Attach the agent at JVM startup
ENTRYPOINT ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "app.jar"]
```

The `OTEL_*` environment variables in `docker-compose.yml` tell the agent where
to send telemetry:

```yaml
OTEL_SERVICE_NAME: greeting-service
OTEL_EXPORTER_OTLP_ENDPOINT: http://otel-collector:4318
OTEL_EXPORTER_OTLP_PROTOCOL: http/protobuf
OTEL_LOGS_EXPORTER: otlp
OTEL_METRICS_EXPORTER: otlp
OTEL_TRACES_EXPORTER: otlp
```

---

## How the OTEL Pipeline Works

```
Service (with Java agent)
    |
    |  OTLP/HTTP (:4318)
    v
OTEL Collector
    |
    +---> traces  --> Tempo    (:3200)
    +---> metrics --> Prometheus (:9090)  via /metrics scrape on :8889
    +---> logs    --> Loki     (:3100)
    |
Grafana (:3001) queries all three backends
```

1. The **OTEL Java agent** bytecode-instruments Spring Boot, RestTemplate, and
   Netty at class-load time. No code changes needed.
2. The agent sends all telemetry to the **OTEL Collector** over OTLP/HTTP.
3. The Collector **batches** and **routes** each signal to its backend:
   - Traces to Tempo (via OTLP/gRPC)
   - Metrics to Prometheus (via a `/metrics` scrape endpoint on port 8889)
   - Logs to Loki (via Loki's push API)
4. **Grafana** is pre-provisioned with all three data sources and has
   trace-to-logs linking configured, so you can click from a Tempo trace
   directly into the Loki logs for that traceId.

---

## Key Takeaways

- **One agent** (OTEL Java agent) captures traces, metrics, and logs with zero
  application code changes.
- **One collector** (OTEL Collector) acts as a single ingestion point and fans
  out to purpose-built backends.
- **One dashboard** (Grafana) queries Tempo, Prometheus, and Loki -- all three
  pillars of observability in one place.
- The entire observability stack is **infrastructure-only**: download an agent,
  set some environment variables, deploy some containers. Your Java code stays
  exactly the same.

---

## Troubleshooting

| Symptom                               | Fix                                                                 |
|---------------------------------------|---------------------------------------------------------------------|
| No traces in Tempo                    | Wait 30-60 seconds after sending a request; Tempo batches ingestion |
| Services not in Eureka                | Check `docker compose logs config-server` -- it must be healthy first |
| Grafana shows "No data"              | Verify the data source is correct (Tempo / Loki / Prometheus)        |
| Port conflict on 3001                 | Stop any other Grafana/app on that port, or change the mapping in `docker-compose.yml` |
