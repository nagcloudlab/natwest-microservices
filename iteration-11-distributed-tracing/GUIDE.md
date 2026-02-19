# Iteration 11: Distributed Tracing (Zipkin + Micrometer Tracing)

## What Problem Does This Solve?

After iteration 10, we have 7 business services + config-server + eureka-server. A single user action like "create order" flows through **5 services**: api-gateway -> order-service -> restaurant-service, accounting-service, kitchen-service, delivery-service -> notification-service (via Kafka). When something goes wrong, there is **no way to trace a request** across these services.

```
Without tracing:

api-gateway log:  "POST /api/orders - 201 Created"
order-service log: "Creating order for consumer 1"
kitchen-service log: "Ticket created for order 42"
notification-service log: "Notification sent"

Which log entries belong to the same user request?
How long did each step take?
Where did the failure happen?
No way to tell.
```

**Problem:** Debugging in a microservices architecture requires correlating logs and timing across multiple services. Without distributed tracing, finding why an order failed means manually checking logs in 5+ services.

**Solution:** Add Zipkin as a trace collector and Micrometer Tracing (Brave bridge) to all 7 business services. Each request gets a unique `traceId` that propagates across HTTP calls (RestTemplate) and Kafka messages. All spans are reported to Zipkin, which provides a UI to visualize the full request flow.

## What Changed

| Component | Before (Iteration 10) | After (Iteration 11) |
|-----------|----------------------|----------------------|
| Tracing | None — logs are isolated per service | Every request gets a `traceId` that propagates across all services |
| Log correlation | Cannot correlate logs across services | Log lines include `[service-name,traceId,spanId]` prefix |
| Visualization | No way to see request flow | Zipkin UI shows full waterfall of spans |
| Kafka messages | No trace context propagated | Trace context propagated through Kafka headers |
| New infrastructure | — | Zipkin server (port 9411) |
| Dependencies added | — | `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` + `actuator` in all 7 services |
| Java code changes | — | **None** — fully auto-configured by Spring Boot |

## Architecture

```
Before (Iteration 10):                     After (Iteration 11):

Requests flow through services             Same flow, but every request gets a traceId
but there's no correlation between          that propagates across ALL services:
log entries from different services.
                                            api-gateway [traceId=abc, spanId=1]
api-gateway -> order-service                   -> order-service [traceId=abc, spanId=2]
  -> restaurant-service                             -> restaurant-service [traceId=abc, spanId=3]
  -> accounting-service                             -> accounting-service [traceId=abc, spanId=4]
  -> kitchen-service                                -> kitchen-service [traceId=abc, spanId=5]
  -> delivery-service                               -> delivery-service [traceId=abc, spanId=6]
  -> notification-service (Kafka)                   -> notification-service [traceId=abc, spanId=7] (via Kafka)

No correlation. No timing.                 All spans -> Zipkin (port 9411)
Can't see the full picture.                Full request waterfall visualization.
```

```
docker-compose up
├── kafka (port 9092)
├── eureka-server (port 8761)
├── zipkin (port 9411) <-- NEW — trace collector & UI
├── config-server (port 8888) <-- depends_on eureka-server
├── restaurant-service (port 8081) ──reports traces──> zipkin
├── accounting-service (port 8083) ──reports traces──> zipkin
├── kitchen-service (port 8084) ──reports traces──> zipkin
├── notification-service (port 8082) ──reports traces──> zipkin
├── delivery-service (port 8085) ──reports traces──> zipkin
├── order-service (port 8080) ──reports traces──> zipkin
├── api-gateway (port 8090) ──reports traces──> zipkin
└── ftgo-web (port 3000)
```

## Key Files

| File | Action | Purpose |
|------|--------|---------|
| `docker-compose.yml` | MODIFY | Add zipkin container (port 9411) |
| `config-repo/application.properties` | MODIFY | Add tracing + Kafka observation + actuator config |
| `config-repo/application-docker.properties` | MODIFY | Add Zipkin docker URL override |
| `config-repo/api-gateway.properties` | MODIFY | Remove actuator line (moved to shared config) |
| `order-service/pom.xml` | MODIFY | Add actuator + tracing deps |
| `restaurant-service/pom.xml` | MODIFY | Add actuator + tracing deps |
| `accounting-service/pom.xml` | MODIFY | Add actuator + tracing deps |
| `kitchen-service/pom.xml` | MODIFY | Add actuator + tracing deps |
| `notification-service/pom.xml` | MODIFY | Add actuator + tracing deps |
| `delivery-service/pom.xml` | MODIFY | Add actuator + tracing deps |
| `api-gateway/pom.xml` | MODIFY | Add tracing deps only (already has actuator) |

**Total: 11 modified files, 0 new files. Zero Java code changes — only dependencies and configuration.**

## How It Works

### Distributed Tracing Concepts

- **Trace**: The entire journey of a request across all services. Identified by a unique `traceId`.
- **Span**: A single unit of work within a trace (e.g., one HTTP call, one Kafka send). Each span has a unique `spanId` and references the `traceId` it belongs to.
- **Parent span**: Spans form a tree. When order-service calls restaurant-service, the order-service span is the parent of the restaurant-service span.

### What Gets Instrumented (Automatically)

When `micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`, and `spring-boot-starter-actuator` are on the classpath, Spring Boot 3.x auto-configures tracing for:

1. **Incoming HTTP requests** (server spans) — via Servlet/WebFlux filter
2. **Outgoing RestTemplate calls** (client spans) — via `ClientHttpRequestInterceptor` auto-registered by Micrometer
3. **KafkaTemplate sends** (producer spans) — via `ProducerInterceptor` (requires `spring.kafka.template.observation-enabled=true`)
4. **@KafkaListener receives** (consumer spans) — via listener observation wrapper (requires `spring.kafka.listener.observation-enabled=true`)

The existing `@LoadBalanced RestTemplate` beans in order-service and delivery-service are automatically instrumented — **no code changes needed**.

### Trace Propagation

Trace context is propagated via HTTP headers (`traceparent`, `b3`) and Kafka message headers. When order-service calls restaurant-service via RestTemplate, the tracing interceptor automatically adds trace headers to the outgoing request. Restaurant-service reads these headers and creates a child span under the same trace.

For Kafka, the same mechanism works through message headers — the `traceId` travels with the Kafka message from producer to consumer.

### Configuration

All tracing configuration is centralized in the config-repo (served by config-server):

**`config-repo/application.properties`** (shared by all services):
```properties
# Trace 100% of requests (dev/training; production would use 0.1 or lower)
management.tracing.sampling.probability=1.0

# Report spans to Zipkin
management.zipkin.tracing.endpoint=http://localhost:9411/api/v2/spans

# Add [service-name,traceId,spanId] to every log line
logging.pattern.correlation=[${spring.application.name:},%mdc{traceId:-},%mdc{spanId:-}]

# Enable trace propagation through Kafka (disabled by default in Spring Boot 3.2.x)
spring.kafka.listener.observation-enabled=true
spring.kafka.template.observation-enabled=true

# Expose health endpoint for all services
management.endpoints.web.exposure.include=health
```

**`config-repo/application-docker.properties`** (Docker override):
```properties
# In Docker, Zipkin is reachable at zipkin:9411 (Docker DNS)
management.zipkin.tracing.endpoint=http://zipkin:9411/api/v2/spans
```

### Why No Code Changes?

Spring Boot 3.x uses Micrometer Observation API as its core instrumentation layer. When you add the tracing bridge (Brave) and reporter (Zipkin) to the classpath alongside actuator:

1. `ObservationAutoConfiguration` creates an `ObservationRegistry`
2. `MicrometerTracingAutoConfiguration` bridges observations to traces
3. `BraveAutoConfiguration` configures Brave as the tracing implementation
4. `ZipkinAutoConfiguration` configures the Zipkin reporter
5. Various auto-configurations add observation interceptors to RestTemplate, Kafka, etc.

All of this happens through Spring Boot's auto-configuration — zero manual wiring needed.

### Why Actuator Is Required

`spring-boot-starter-actuator` is required because it provides the `ObservationRegistry` bean that Micrometer Tracing hooks into. Without actuator, the observation infrastructure is not auto-configured and tracing will not work.

### Zipkin Server

Zipkin runs as a standalone container (`openzipkin/zipkin:3`). It:
- Receives spans via HTTP POST to `/api/v2/spans`
- Stores traces in memory (for dev; production would use Elasticsearch or Cassandra)
- Provides a web UI at port 9411 for searching and visualizing traces
- Has no dependencies — services report traces asynchronously (fire-and-forget)

Services tolerate Zipkin being unavailable — traces are simply dropped without affecting service behavior.

## Running the System

### Start Everything

```bash
cd iteration-11-distributed-tracing
docker compose up --build
```

### Check Eureka Dashboard

Open http://localhost:8761 — you should see all 8 services registered (including config-server).

### Check Zipkin UI

Open http://localhost:9411 — the Zipkin UI should be running.

### Create an Order (Triggers Multi-Service Flow)

```bash
curl -X POST http://localhost:8090/api/orders \
  -H "Content-Type: application/json" \
  -d '{"consumerId":1,"restaurantId":1,"lineItems":[{"menuItemId":"item-1","name":"Chicken Tikka Masala","quantity":2,"price":11.99}],"deliveryAddress":"123 Main St","paymentMethod":"CARD"}'
```

### View Traces in Zipkin

1. Open http://localhost:9411
2. Click **"Run Query"** (or select a service name from the dropdown)
3. Click on a trace to see the full waterfall visualization
4. You should see spans for: `api-gateway` -> `order-service` -> `restaurant-service`, `accounting-service`, `kitchen-service`, `delivery-service`
5. Kitchen/delivery Kafka events should also appear as spans flowing to `notification-service`

### Check Log Correlation

```bash
docker compose logs order-service | grep traceId
```

Log lines should include the `[service-name,traceId,spanId]` prefix, allowing you to correlate logs across services using the same `traceId`.

### Full E2E Flow via UI

Open http://localhost:3000 and follow the same flow:

1. **Consumer view** -- Browse restaurants, view menu, place an order
2. **Restaurant view** -- Accept ticket, mark as preparing, mark as ready
3. **Courier view** -- Assign courier, pick up, deliver
4. **Zipkin** -- After each step, check Zipkin to see the traces

### Stop Everything

```bash
docker compose down
```

## Teaching Concepts

### Why Distributed Tracing Matters

In a monolith, a debugger or a single log file tells you everything. In microservices, a single user action crosses process boundaries — each service has its own logs, its own process, its own timing. Distributed tracing solves the fundamental question: **"What happened to this request?"**

Without tracing:
- A timeout in order creation could be caused by any of 5 downstream services
- You would need to manually correlate timestamps across 5+ log files
- Kafka-based async flows are invisible — you can't see if a message was produced, consumed, or lost

With tracing:
- One `traceId` connects all log entries and spans for a single request
- Zipkin shows a waterfall view with exact timing for each service call
- You can immediately see which service is slow or failing

### Sampling Probability

`management.tracing.sampling.probability=1.0` means 100% of requests are traced. This is appropriate for development and training, but in production:
- Tracing every request adds overhead (HTTP calls to Zipkin, memory for spans)
- Production systems typically use `0.01` (1%) to `0.1` (10%)
- Critical paths can be force-sampled regardless of the probability setting

### Brave vs. OpenTelemetry

Spring Boot 3.x supports two tracing bridges:
- **Brave** (`micrometer-tracing-bridge-brave`): Mature, lightweight, Zipkin-native. Used here.
- **OpenTelemetry** (`micrometer-tracing-bridge-otel`): Vendor-neutral standard, supports OTLP protocol, wider ecosystem.

Both work with Zipkin. We use Brave for simplicity — fewer dependencies and no additional configuration.

### Kafka Observation Properties

The `spring.kafka.listener.observation-enabled` and `spring.kafka.template.observation-enabled` properties are **critical** and **disabled by default** in Spring Boot 3.2.x. Without them:
- Kafka producers send messages without trace headers
- Kafka consumers create new traces instead of continuing the parent trace
- The trace "breaks" at the Kafka boundary — you can't see the connection between the producer and consumer

Enabling these properties ensures trace context is propagated through Kafka message headers, creating a continuous trace from HTTP request through Kafka to the consumer.
