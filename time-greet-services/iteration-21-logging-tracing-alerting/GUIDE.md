# Iteration 21 — Structured Logging, Correlation IDs & Alerting

## Goal
Add production-grade observability to our microservices: JSON-structured logs, distributed trace correlation across service boundaries, and Prometheus + Grafana alerting on rate-limit violations.

## Duration
~45 minutes

## Services

| Service            | Port | Purpose                                        |
|--------------------|------|------------------------------------------------|
| greeting-service   | 9001 | Greeting API with rate limiter (from iter-20)  |
| time-service       | 9002 | Calls greeting-service, propagates trace context |
| Prometheus         | 9090 | Scrapes `/actuator/prometheus` from both services |
| Grafana            | 3001 | Dashboards and alerting on Prometheus data     |

## What Changed (from Iteration 20)

| File                             | Change                                                       |
|----------------------------------|--------------------------------------------------------------|
| `pom.xml` (both)                 | +logstash-logback-encoder, +micrometer-tracing-bridge-brave, +micrometer-registry-prometheus |
| `logback-spring.xml` (both)      | NEW — JSON logs in Docker, plain text locally                |
| `application.properties` (both)  | +prometheus actuator, +tracing sampling=1.0                  |
| `RestClientConfig.java` (time)   | NEW — Spring-managed RestClient for trace propagation        |
| `TimeController.java`            | Uses injected RestClient bean + structured log statements    |
| `GreetingController.java`        | Added log.info on each request                               |
| `GlobalExceptionHandler.java`    | Added log.warn on rate limit                                 |
| `prometheus.yml`                 | NEW — scrapes both services every 5s                         |
| `grafana/provisioning/`          | NEW — auto-provisions Prometheus datasource + 429 alert      |
| `docker-compose.yml`             | +prometheus, +grafana, docker profile on greeting-service     |

---

## Opening Story

> **"We shipped rate limiting in Iteration 20 — but how would we know it's firing in production?"**
>
> Three pillars of observability answer that question:
> 1. **Structured Logging** — machine-readable JSON so log aggregators can parse and index every field
> 2. **Correlation IDs** — a single traceId that follows a request across every service it touches
> 3. **Alerting** — Prometheus collects metrics, Grafana fires an alert when 429s spike
>
> All three are added as library dependencies — no sidecars, no agents, no infrastructure changes to the services themselves.

---

## Act 1 — Start the Stack

```bash
cd time-greet-services/iteration-21-logging-tracing-alerting
docker compose up --build -d
```

Wait for all four containers to become healthy:

```bash
docker compose ps
```

You should see `greeting-service`, `time-service`, `prometheus`, and `grafana` all running.

### Quick smoke test

```bash
curl -s localhost:9001/api/greeting | jq .
curl -s localhost:9002/api/time | jq .
```

---

## Act 2 — Structured JSON Logs

### What changed

We added `logstash-logback-encoder` and a `logback-spring.xml` that switches format by Spring profile:

- **`docker` profile** → JSON via `LogstashEncoder` (every log line is a JSON object)
- **Default (local dev)** → Plain text with `[traceId/spanId]` pattern

### See it in action

```bash
docker compose logs greeting-service --tail 5
```

Each line is a JSON object with fields like:

```json
{
  "@timestamp": "2024-01-15T10:30:00.123Z",
  "level": "INFO",
  "logger_name": "c.d.g.controller.GreetingController",
  "message": "Greeting request received",
  "traceId": "6a3e7f2b1c4d5e6f",
  "spanId": "a1b2c3d4e5f6"
}
```

### Discussion point

> **Why JSON logs?** Plain text is fine for `docker compose logs`, but in production you pipe logs to ELK, Splunk, or CloudWatch. Structured JSON means every field is automatically indexed and searchable — no regex parsing needed.

### Discussion point

> **Why profile-switch?** Developers running locally still get readable text. The Docker/Kubernetes environment gets machine-parseable JSON. Same code, different output.

---

## Act 3 — Correlation IDs (Distributed Tracing)

### What changed

We added `micrometer-tracing-bridge-brave` which:
1. Generates a **traceId** and **spanId** for every incoming request
2. Puts them into the **MDC** (Mapped Diagnostic Context) so Logback includes them automatically
3. Propagates them via **B3 headers** on outgoing HTTP calls

The critical piece: `RestClientConfig.java` creates a `RestClient` from Spring's auto-configured `RestClient.Builder` — which is pre-instrumented with tracing interceptors. Without this, trace headers would NOT propagate.

### See it in action

Call time-service (which calls greeting-service internally):

```bash
curl -s localhost:9002/api/time | jq .
```

Now grep both services' logs for the same traceId:

```bash
# Get the traceId from time-service logs
docker compose logs time-service --tail 5

# Search greeting-service for the same traceId
docker compose logs greeting-service | grep "<paste-traceId-here>"
```

You'll see the **same traceId** in both services — proving the trace context was propagated across the HTTP call.

### Discussion point

> **Why is RestClientConfig needed?** In iteration 20, the controller created `RestClient.builder().baseUrl(url).build()` directly. That bypasses Spring's auto-configuration, which adds the tracing interceptor. By injecting `RestClient.Builder` (which Spring instruments), we get trace propagation for free.

---

## Act 4 — Prometheus Metrics

### What changed

We added `micrometer-registry-prometheus` which exposes a `/actuator/prometheus` endpoint with all metrics in Prometheus text format. A `prometheus.yml` config scrapes both services every 5 seconds.

### See it in action

Check the raw metrics endpoint:

```bash
curl -s localhost:9001/actuator/prometheus | grep http_server_requests
```

Open Prometheus UI at **http://localhost:9090** and check:

1. **Status → Targets** — both services should show "UP"
2. **Graph** — try these PromQL queries:

```promql
# Request rate by status code
rate(http_server_requests_seconds_count[1m])

# Average response time
rate(http_server_requests_seconds_sum[1m]) / rate(http_server_requests_seconds_count[1m])

# JVM memory usage
jvm_memory_used_bytes{area="heap"}
```

---

## Act 5 — Trigger the Alert

### What changed

Grafana is provisioned with an alert rule that fires when `rate(http_server_requests_seconds_count{status="429"}[1m]) > 0`.

### Trigger it

Flood the rate limiter (which allows 5 requests per 10 seconds):

```bash
for i in $(seq 1 20); do
  echo "Request $i: $(curl -s -o /dev/null -w '%{http_code}' localhost:9001/api/greeting)"
done
```

You'll see the first 5 return `200` and the rest return `429`.

### Check the alert

1. Open Grafana at **http://localhost:3001**
2. Go to **Alerting → Alert rules** in the left sidebar
3. You should see the "Rate Limit 429 Detected" alert in **Firing** state

### Check the logs

```bash
docker compose logs greeting-service --tail 10
```

You'll see `log.warn("Rate limit exceeded: ...")` entries — structured JSON with the traceId of each rejected request.

---

## Act 6 — Explore Grafana

With anonymous admin access, you can build ad-hoc dashboards. Try the **Explore** page with these queries:

| Query | What it shows |
|-------|---------------|
| `rate(http_server_requests_seconds_count{status="200"}[1m])` | Successful request rate |
| `rate(http_server_requests_seconds_count{status="429"}[1m])` | Rate-limited request rate |
| `resilience4j_ratelimiter_available_permissions` | Available rate limiter permits |
| `jvm_memory_used_bytes{area="heap"}` | JVM heap usage per service |
| `process_cpu_usage` | CPU usage per service |

---

## Bridge to FTGO

In a real food-ordering platform:
- **Structured JSON logs** would flow to ELK/Splunk/CloudWatch for centralized search
- **Correlation IDs** would trace an order from API Gateway → Order Service → Kitchen Service → Delivery Service
- **Prometheus + Grafana** would alert on SLO violations (e.g., p99 latency > 500ms, error rate > 1%)
- You'd add **OpenTelemetry** for full distributed tracing with flame graphs (Jaeger/Tempo)

---

## Cleanup

```bash
docker compose down
```

---

## Summary

| Concept | What We Used | What It Does |
|---------|-------------|--------------|
| Structured Logging | `logstash-logback-encoder` | JSON log output with indexed fields |
| Correlation IDs | `micrometer-tracing-bridge-brave` | Auto traceId/spanId in MDC + B3 propagation |
| Metrics | `micrometer-registry-prometheus` | `/actuator/prometheus` endpoint |
| Scraping | Prometheus | Collects metrics from both services |
| Alerting | Grafana | Fires alert on 429 rate spikes |
| Profile switching | `logback-spring.xml` | JSON in Docker, plain text locally |

---

## Discussion Questions

1. **Why not use OpenTelemetry instead of Brave?** Brave is lighter and works without an agent. OTEL is the industry standard for full tracing pipelines — but adds complexity (collector, Jaeger/Tempo). For metrics + log correlation, Brave is sufficient.

2. **What happens if Prometheus goes down?** Services keep running — they just buffer metrics. When Prometheus comes back, it resumes scraping. No data is lost for the current scrape interval, but the gap won't be backfilled.

3. **How would you add log-based alerting?** Ship JSON logs to Elasticsearch, then use Kibana alerting to trigger on patterns like `level:ERROR AND service:greeting-service`. Or use Loki + Grafana for a Prometheus-native log pipeline.

4. **Why sample 100% of traces?** For this demo. In production, you'd set `management.tracing.sampling.probability=0.1` (10%) or use adaptive sampling to control overhead.

5. **What's the difference between a traceId and a spanId?** A traceId identifies the entire request chain across all services. A spanId identifies one unit of work within that chain. One trace has many spans.

---

## Quick Reference

```bash
# Start everything
docker compose up --build -d

# Check service health
curl -s localhost:9001/actuator/health | jq .
curl -s localhost:9002/actuator/health | jq .

# View structured logs
docker compose logs greeting-service --tail 10
docker compose logs time-service --tail 10

# Check Prometheus targets
open http://localhost:9090/targets

# View raw metrics
curl -s localhost:9001/actuator/prometheus | head -20

# Trigger rate limit alert
for i in $(seq 1 20); do curl -s -o /dev/null -w "%{http_code}\n" localhost:9001/api/greeting; done

# Check alert in Grafana
open http://localhost:3001/alerting/list

# Cleanup
docker compose down
```
