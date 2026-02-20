# Iteration 11 Simple Demo: Distributed Tracing with Zipkin

> **Goal**: Teach distributed tracing — see the full journey of a request across multiple services. When `curl localhost:9000/time/with-greeting` flows through api-gateway -> time-service -> greeting-service, trainees see all 3 hops as a single trace in Zipkin's waterfall UI.
>
> **Duration**: ~25 minutes
>
> **Pre-requisites**: Docker Desktop installed, iteration-10 Centralized Config demo completed

---

## The Services

| Service | Port | Purpose |
|---------|------|---------|
| **zipkin** | 9411 | **NEW** -- Distributed trace collector & waterfall UI |
| **eureka-server** | 8761 | Service registry (unchanged from iteration 10) |
| **config-server** | 8888 | Centralized configuration (tracing deps added) |
| **greeting-service** | 9001 | Returns a greeting message + hostname |
| **time-service** | 9002 | Returns current time; calls greeting-service via Eureka |
| **api-gateway** | 9000 | Routes requests using `lb://` (Eureka-resolved) |

### What Changed from Iteration 10

| What | Iteration 10 | Iteration 11 |
|------|-------------|--------------|
| Tracing | None -- logs isolated per service | Every request gets `traceId` propagated across all services |
| Log correlation | Cannot connect logs across services | `[service-name,traceId,spanId]` in every log line |
| Visualization | No way to see request flow | Zipkin UI shows waterfall of spans |
| Infrastructure | 5 services | 6 services (+ Zipkin on port 9411) |
| Dependencies added | -- | `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` in 4 services |
| Config added | -- | 4 lines in shared `application.properties` + 1 line Docker override |
| Java code changes | -- | **None** -- fully auto-configured by Spring Boot |

---

## Opening (2 min)

**Story to tell:**

> "In iteration 10, we centralized all our config with Config Server. But there's still a problem we haven't solved. Look at the logs from 3 services:"
>
> - api-gateway logs: `-> GET /time/with-greeting -> http://172.18.0.4:9002/time/with-greeting | 200 | 45ms`
> - time-service logs: `GET /time/with-greeting - 200`
> - greeting-service logs: `GET /greeting - 200`
>
> "Which log lines belong to the same user request? How long did each hop take? Where did the failure happen?"
>
> "With 3 services, you can eyeball it. With 30 services, you can't. We need **distributed tracing**."

---

## Act 1: The Problem -- Isolated Logs (3 min)

### Step 1 -- Start everything

```bash
cd simple-demos/iteration-11-distributed-tracing
docker compose up --build
```

Wait for all six services to start. Startup order: zipkin (immediate) + eureka -> config-server -> greeting + time -> api-gateway.

### Step 2 -- Generate some traffic

```bash
curl localhost:9000/time/with-greeting | jq
```

### Step 3 -- Show the isolated logs

```bash
docker compose logs api-gateway | tail -5
docker compose logs time-service | tail -5
docker compose logs greeting-service | tail -5
```

> **Key question**: "These 3 log entries are from the same request. But how would you know? There's no shared identifier. In production with hundreds of requests per second, you can't match them up."

---

## Act 2: Zipkin Setup -- One Container, Zero Config (3 min)

### Step 4 -- Open the Zipkin UI

Open: **http://localhost:9411**

> "Zipkin is a distributed trace collector. It receives trace data from our services and shows a waterfall visualization. The Zipkin server itself needs **zero configuration** -- just a Docker image."

Show `docker-compose.yml` -- the Zipkin service:

```yaml
zipkin:
  image: openzipkin/zipkin:3
  ports:
    - "9411:9411"
```

> "That's the entire server setup. One image, one port. No database, no config -- it stores traces in memory (fine for development). Services report traces asynchronously -- if Zipkin is down, traces are silently dropped. No impact on service availability."

---

## Act 3: Client Setup -- Two Dependencies, Zero Code Changes (5 min)

### Step 5 -- Show what changed in the POM files

Open any service's `pom.xml` (e.g., `greeting-service/pom.xml`) -- highlight the **two new dependencies**:

```xml
<!-- Distributed Tracing (Zipkin) -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>
<dependency>
    <groupId>io.zipkin.reporter2</groupId>
    <artifactId>zipkin-reporter-brave</artifactId>
</dependency>
```

> **Explain:**
> - **`micrometer-tracing-bridge-brave`** -- bridges Spring Boot's observation API to the Brave tracing library. Brave creates traces and spans.
> - **`zipkin-reporter-brave`** -- reports those spans to a Zipkin server.
> - Both are **added to all 4 services** (config-server, greeting-service, time-service, api-gateway).
> - Combined with `spring-boot-starter-actuator` (already present), Spring Boot **auto-configures everything**. Zero Java code changes.

### Step 6 -- Show the shared config

Show `config-repo/application.properties` -- the new tracing lines:

```properties
# Distributed Tracing (Zipkin)
management.tracing.sampling.probability=1.0
management.zipkin.tracing.endpoint=http://localhost:9411/api/v2/spans
logging.pattern.correlation=[${spring.application.name:},%mdc{traceId:-},%mdc{spanId:-}]
```

> **Explain each line:**
> - **`sampling.probability=1.0`** -- trace 100% of requests. In production you'd use 0.01-0.1 (1-10%) to reduce overhead. For demo, we trace everything.
> - **`zipkin.tracing.endpoint`** -- where to send spans. For local development, `localhost:9411`.
> - **`logging.pattern.correlation`** -- adds `[service-name,traceId,spanId]` prefix to every log line. This is how you grep logs by traceId.

### Centralized Config Payoff

> "Notice where these 3 lines live -- in the **shared** `application.properties` in the Config Server. All 4 services pick this up automatically. We didn't edit 4 separate config files -- we edited **one**. That's the payoff of centralized config from iteration 10."

Show `config-repo/application-docker.properties` -- one Docker override:

```properties
management.zipkin.tracing.endpoint=http://zipkin:9411/api/v2/spans
```

> "In Docker, Zipkin's hostname is `zipkin` (the container name), not `localhost`. One override in the shared Docker properties."

---

## Act 4: See It Work -- Zipkin Waterfall (5 min)

### Step 7 -- Generate a multi-service request

```bash
curl localhost:9000/time/with-greeting | jq
```

This request flows: **client -> api-gateway -> time-service -> greeting-service -> time-service -> api-gateway -> client**

### Step 8 -- View the trace in Zipkin

1. Open **http://localhost:9411**
2. Click **"Run Query"** (no filters needed)
3. You should see a trace -- click on it

> **Key moment**: "This is the waterfall. You can see the entire request journey:"
>
> ```
> api-gateway         |==================================|  45ms
> time-service          |============================|      38ms
> greeting-service           |==============|               12ms
> ```
>
> "Three spans, one trace. The api-gateway received the request, forwarded it to time-service. Time-service called greeting-service. You can see exactly how long each hop took."

### Step 9 -- Try a simple request

```bash
curl localhost:9000/greeting | jq
```

Go back to Zipkin, click "Run Query" again:

> "This trace has 2 spans -- api-gateway -> greeting-service. No time-service involved. Zipkin shows you exactly which services were part of each request."

### Step 10 -- Try direct access

```bash
curl localhost:9002/time/with-greeting | jq
```

> "When you bypass the gateway and call time-service directly, the trace still shows 2 spans: time-service -> greeting-service. Tracing works at every level, not just through the gateway."

---

## Act 5: Log Correlation -- grep by traceId (5 min)

### Step 11 -- Show correlated logs

```bash
curl localhost:9000/time/with-greeting | jq
```

Now check the logs:

```bash
docker compose logs | grep -E "\[.*,.*,.*\]" | tail -10
```

> **Key moment**: "See the prefix on every log line? `[api-gateway,65abc123def456,1a2b3c4d5e6f]` -- that's `[service-name,traceId,spanId]`. The **traceId is the same** across all 3 services for the same request."

### Step 12 -- grep by traceId

Pick a traceId from the output above, then:

```bash
docker compose logs | grep "65abc123def456"
```

> "Every log line from every service that participated in this request. In production with centralized logging (ELK, Datadog), you'd search by traceId and instantly see the full journey."

### How It Works

> "When api-gateway forwards a request to time-service, it automatically adds a `traceparent` HTTP header containing the traceId. Time-service reads that header and continues the same trace. When time-service calls greeting-service, it passes the same traceId forward. That's **trace context propagation** -- and it's 100% automatic with the tracing libraries."

---

## Act 6: What We Added -- Before/After (3 min)

### The Complete Delta from Iteration 10

**Dependencies** (same 2 lines added to 4 `pom.xml` files):
```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>
<dependency>
    <groupId>io.zipkin.reporter2</groupId>
    <artifactId>zipkin-reporter-brave</artifactId>
</dependency>
```

**Shared config** (3 lines added to `config-repo/application.properties`):
```properties
management.tracing.sampling.probability=1.0
management.zipkin.tracing.endpoint=http://localhost:9411/api/v2/spans
logging.pattern.correlation=[${spring.application.name:},%mdc{traceId:-},%mdc{spanId:-}]
```

**Docker override** (1 line added to `config-repo/application-docker.properties`):
```properties
management.zipkin.tracing.endpoint=http://zipkin:9411/api/v2/spans
```

**Docker Compose** (1 new container):
```yaml
zipkin:
  image: openzipkin/zipkin:3
  ports:
    - "9411:9411"
```

**Java code changes**: **Zero.**

> "That's the entire change. Two Maven dependencies, four config lines, one Docker container. And now you can see every request's journey through your entire system."

### Actuator Moved to Shared Config

> "One bonus improvement: we moved `management.endpoints.web.exposure.include=health` from `api-gateway.properties` to the shared `application.properties`. Now **all** services expose their health endpoint, not just the gateway. This was already needed for Docker healthchecks -- centralizing it was overdue."

---

## Bridge to FTGO (2 min)

> "We added tracing to 3 services. The FTGO system has 8 services -- but uses the exact same pattern."

| Pattern | Simple Demo | FTGO |
|---------|-------------|------|
| Zipkin container | `openzipkin/zipkin:3` | Same |
| Tracing deps | 2 deps x 4 services | 2 deps x 7 services |
| Shared tracing config | 3 lines | Same 3 lines |
| Java code changes | None | None |
| Trace propagation | HTTP (RestTemplate) | HTTP + **Kafka** |

> "The FTGO system also traces through Kafka messages -- when order-service publishes an event and kitchen-service consumes it, the trace continues. That requires two extra config lines (`spring.kafka.listener.observation-enabled=true` and `spring.kafka.template.observation-enabled=true`). Same principle -- just config, no code."

---

## Cleanup

```bash
# Stop everything
docker compose down

# Remove images (optional, frees disk space)
docker compose down --rmi all
```

---

## Summary: What We Learned

| Problem | Solution | Concept |
|---------|----------|---------|
| Can't connect logs across services | Every request gets a unique `traceId` | Distributed Tracing |
| Can't see request flow timing | Zipkin waterfall shows each hop | Trace Visualization |
| Can't grep logs across services | `[service-name,traceId,spanId]` log prefix | Log Correlation |
| Needed code changes for tracing | Two dependencies + auto-configuration | Zero-Code Instrumentation |
| Config spread across services | Tracing config in one shared file | Centralized Config Payoff |

---

## Discussion Questions

1. **Sampling**: "We set sampling to 1.0 (100%). Why not do that in production?" _(Each trace adds overhead -- creating spans, sending them to Zipkin. At 1000 requests/second, that's 1000 traces. You'd typically sample 1-10% in production and trace 100% only for specific users or error paths.)_

2. **Trace vs. Span**: "What's the difference?" _(A **trace** is the entire journey of a request through the system -- it has one unique traceId. A **span** is one hop -- one service's processing of that request. A trace contains multiple spans. In our `/time/with-greeting` example: 1 trace, 3 spans.)_

3. **Auto-Instrumentation**: "How does tracing work with zero code changes?" _(Spring Boot auto-configures `ObservationRegistry` (from actuator). The tracing bridge registers an observer that creates spans. RestTemplate is automatically instrumented to propagate trace headers. The gateway does the same for forwarded requests. All automatic when the libraries are on the classpath.)_

4. **Brave vs. OpenTelemetry**: "We're using Brave (Zipkin's library). There's also OpenTelemetry. What's the difference?" _(OpenTelemetry is a vendor-neutral standard -- it can export to Zipkin, Jaeger, Datadog, etc. Brave is Zipkin-specific. Spring Boot supports both via micrometer-tracing. For learning, Brave + Zipkin is simpler. For production, OpenTelemetry gives you vendor flexibility.)_

5. **Centralized Config Payoff**: "What would have happened without Config Server?" _(You'd add the 3 tracing config lines to **each service's** `application.properties` -- 4 copies. Change the Zipkin URL? Update 4 files. With Config Server, you update one file.)_

---

## Quick Reference: Useful Commands

```bash
# Build and start everything (6 services)
docker compose up --build

# Start in background
docker compose up --build -d

# View Eureka dashboard (4 services registered)
open http://localhost:8761

# View Zipkin UI
open http://localhost:9411

# Test direct access
curl localhost:9001/greeting | jq
curl localhost:9002/time | jq
curl localhost:9002/time/with-greeting | jq

# Test via gateway
curl localhost:9000/greeting | jq
curl localhost:9000/time | jq
curl localhost:9000/time/with-greeting | jq
curl localhost:9000/hello | jq

# View correlated logs
docker compose logs | grep -E "\[.*,.*,.*\]" | tail -20

# grep by a specific traceId
docker compose logs | grep "<paste-traceId-here>"

# View gateway logs
docker compose logs -f api-gateway

# View all logs (follow mode)
docker compose logs -f

# Stop everything
docker compose down
```
