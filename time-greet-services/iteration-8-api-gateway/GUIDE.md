# Iteration 8 Simple Demo: API Gateway

> **Goal**: Teach API Gateway concepts (single entry point, path-based routing, path rewriting, cross-cutting concerns) using a trivial 3-service app before showing the full FTGO gateway with 7 routes.
>
> **Duration**: ~30 minutes
>
> **Pre-requisites**: Docker Desktop installed, iteration-7 Docker demo completed

---

## The Services

| Service | Port | Endpoints | Purpose |
|---------|------|-----------|---------|
| **greeting-service** | 9001 | `GET /greeting` | Returns a greeting message + hostname |
| **time-service** | 9002 | `GET /time`, `GET /time/with-greeting` | Returns current time; optionally calls greeting-service |
| **api-gateway** | 9000 | Routes to the above | Single entry point for all requests |

### Gateway Route Table

| Incoming Path | Target Service | Notes |
|---|---|---|
| `/greeting` | greeting-service:9001 | Pass-through |
| `/time`, `/time/**` | time-service:9002 | Pass-through |
| `/hello` | greeting-service:9001 | RewritePath `/hello` → `/greeting` |

---

## Opening (2 min)

**Story to tell:**

> "You have 2 services on 2 ports. That's manageable. But imagine 20 services — 20 ports, 20 URLs. Your frontend needs to know every single one. Your mobile app needs to know every single one. And when you move a service to a different port? Update every client."
>
> "What if there was ONE door that clients walked through, and behind that door, requests got routed to the right service automatically?"

---

## Act 1: The Problem — Multiple Ports, Direct Access (5 min)

### Step 1 — Start the services

```bash
cd simple-demos/iteration-8-api-gateway
docker compose up --build
```

Wait for all three services to start (greeting-service and time-service must be healthy before api-gateway starts).

### Step 2 — Access services directly

```bash
curl localhost:9001/greeting | jq
```
```json
{
  "message": "Hello from Greeting Service!",
  "host": "a1b2c3d4e5f6"
}
```

```bash
curl localhost:9002/time | jq
```
```json
{
  "currentTime": "2025-01-15 14:30:22",
  "host": "f7e8d9c0b1a2"
}
```

### Problem Setup

> "This works — but notice what the client needs to know:
> - greeting-service is on **port 9001**
> - time-service is on **port 9002**
>
> Now imagine 20 microservices. Your React frontend has 20 different base URLs hardcoded. Your mobile app has 20 different base URLs. A service moves from port 8081 to 8091? Every client needs updating."

---

## Act 2: Single Entry Point — Route Everything Through Port 9000 (8 min)

### Step 3 — Access via the gateway

```bash
# Same data, ONE port
curl localhost:9000/greeting | jq
curl localhost:9000/time | jq
curl localhost:9000/time/with-greeting | jq
```

> **Key moment**: "Same responses, but ALL through port 9000. The client doesn't know — or care — which service handled the request. One URL, one port."

### How It Works

Open `api-gateway/src/main/resources/application.properties` and show the route config:

```properties
# Route 1: /greeting → greeting-service:9001 (pass-through)
spring.cloud.gateway.routes[0].id=greeting-service
spring.cloud.gateway.routes[0].uri=${greeting-service.uri}
spring.cloud.gateway.routes[0].predicates[0]=Path=/greeting

# Route 2: /time/** → time-service:9002 (pass-through)
spring.cloud.gateway.routes[1].id=time-service
spring.cloud.gateway.routes[1].uri=${time-service.uri}
spring.cloud.gateway.routes[1].predicates[0]=Path=/time,/time/**
```

> **Explain:**
> - `uri` — Where to forward the request
> - `predicates` — When to match (path pattern)
> - The gateway receives `/time/with-greeting`, matches `Path=/time/**`, and forwards it to time-service. The client only knows about port 9000.

### Discussion

> **Ask the class:**
> - "If we add a new service (say, weather-service on port 9003), what changes?" _(Add one route in the gateway config — zero client changes)_
> - "What if greeting-service moves from port 9001 to 9005?" _(Update one URI in the gateway — zero client changes)_
> - "How is this different from a traditional load balancer like Nginx?" _(Gateway is application-aware — it can inspect headers, rewrite paths, add filters. Nginx is network-level.)_

---

## Act 3: Path Rewriting — `/hello` → `/greeting` (5 min)

### Step 4 — Try the rewrite route

```bash
curl localhost:9000/hello | jq
```
```json
{
  "message": "Hello from Greeting Service!",
  "host": "a1b2c3d4e5f6"
}
```

> "Wait — there's no `/hello` endpoint on greeting-service. Try calling it directly:"

```bash
curl localhost:9001/hello
# 404 — endpoint doesn't exist!
```

> "The gateway **rewrote** the path before forwarding. The client said `/hello`, but greeting-service received `/greeting`."

### How It Works

Show the rewrite route:

```properties
# Route 3: /hello → greeting-service:9001 (rewrite /hello → /greeting)
spring.cloud.gateway.routes[2].id=hello-rewrite
spring.cloud.gateway.routes[2].uri=${greeting-service.uri}
spring.cloud.gateway.routes[2].predicates[0]=Path=/hello
spring.cloud.gateway.routes[2].filters[0]=RewritePath=/hello, /greeting
```

> **Explain:**
> - `RewritePath` transforms the URL before forwarding
> - The client sees a clean external API (`/hello`), while internally the path maps to `/greeting`
> - **Real-world use case**: In FTGO, `/api/couriers/**` gets rewritten to `/api/deliveries/couriers/**` — the courier API is part of delivery-service, but the gateway gives it its own clean URL

---

## Act 4: Cross-Cutting Concerns — LoggingFilter (7 min)

### Step 5 — Watch the gateway logs

Open a new terminal:

```bash
docker compose logs -f api-gateway
```

### Step 6 — Make some requests and watch the logs

```bash
curl localhost:9000/greeting
curl localhost:9000/time
curl localhost:9000/hello
curl localhost:9000/time/with-greeting
```

You'll see log output like:

```
→ GET /greeting → http://greeting-service:9001/greeting | 200 | 45ms
→ GET /time → http://time-service:9002/time | 200 | 12ms
→ GET /hello → http://greeting-service:9001/greeting | 200 | 8ms
→ GET /time/with-greeting → http://time-service:9002/time/with-greeting | 200 | 52ms
```

> **Key moment**: "Every request that passes through the gateway gets logged — method, path, target, status code, and duration. We didn't add logging code to greeting-service or time-service. This is a **cross-cutting concern** handled at the gateway."

### How It Works

Open `api-gateway/src/main/java/com/demo/gateway/LoggingFilter.java`:

```java
@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long start = System.currentTimeMillis();
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getURI().getPath();

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            long duration = System.currentTimeMillis() - start;
            int status = exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().value() : 0;
            URI target = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
            log.info("→ {} {} → {} | {} | {}ms", method, path, target, status, duration);
        }));
    }
}
```

> **Explain:**
> - `GlobalFilter` — runs on EVERY request through the gateway
> - Records the start time, then after the response comes back, logs the details
> - `GATEWAY_REQUEST_URL_ATTR` — Spring Cloud Gateway stores the resolved target URL here
>
> **Other cross-cutting concerns you could add here:**
> - Authentication (reject requests without valid tokens)
> - Rate limiting (max 100 requests per second per client)
> - Request ID injection (add a correlation ID header for tracing)

---

## Act 5: CORS Configuration (3 min)

Show the CORS section in `application.properties`:

```properties
spring.cloud.gateway.globalcors.cors-configurations.[/**].allowed-origins=http://localhost:3000
spring.cloud.gateway.globalcors.cors-configurations.[/**].allowed-methods=GET,POST,PUT,DELETE,OPTIONS
spring.cloud.gateway.globalcors.cors-configurations.[/**].allowed-headers=*
spring.cloud.gateway.globalcors.cors-configurations.[/**].allow-credentials=true
```

> **Explain:**
> - "Without this, a React app on `localhost:3000` can't call the gateway on `localhost:9000` — the browser blocks it (same-origin policy)."
> - "Instead of configuring CORS on every backend service, we configure it ONCE on the gateway. Another cross-cutting concern handled centrally."
> - "The `[/**]` means this applies to all paths through the gateway."

---

## Bridge to FTGO (2 min)

> "We built a gateway with 3 routes. The FTGO gateway has 7 routes — but uses the exact same patterns."

Open the FTGO gateway config to show the scale:

```bash
cat ../../iteration-8-api-gateway/api-gateway/src/main/resources/application.properties
```

> "Same patterns, bigger scale:
> - **Same route syntax** — `Path=`, `uri=`, predicates
> - **Same path rewriting** — `/api/couriers/**` → `/api/deliveries/couriers/**`
> - **Same LoggingFilter** — identical code, logs all 7 routes
> - **Same CORS config** — allows `ftgo-web` on port 3000
>
> The only difference is more routes. The concepts are identical."

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

| Problem | Solution | Gateway Concept |
|---------|----------|-----------------|
| Clients need to know every service port | Single entry point (port 9000) | Routing |
| Internal paths don't match external API | RewritePath filter | Path rewriting |
| Logging needed on every request | GlobalFilter (LoggingFilter) | Cross-cutting concerns |
| Browser blocks cross-origin requests | Gateway-level CORS config | Centralized CORS |
| Adding a new service requires client changes | Add one route in gateway config | Decoupling |

---

## Discussion Questions

Use these to drive conversation and bridge to upcoming topics:

1. **Single Point of Failure**: "The gateway routes ALL traffic. What if it crashes?" _(Run multiple instances behind a load balancer — but now you need service discovery to find them... iteration 9!)_

2. **Authentication**: "We're logging requests. Where would you add authentication?" _(Another GlobalFilter that checks JWT tokens — before the request reaches the backend service)_

3. **Rate Limiting**: "What if one client hammers the gateway with 10,000 requests/second?" _(Spring Cloud Gateway has a built-in `RequestRateLimiter` filter)_

4. **Service Discovery**: "We hardcoded `greeting-service:9001` in the config. What if the port changes, or you have 3 instances?" _(Leads to service discovery — iteration 9, Eureka)_

5. **Configuration**: "We have config in `application.properties` and `application-docker.properties`. What if you have 20 services, each with two profiles?" _(Leads to centralized configuration — iteration 10)_

---

## Quick Reference: Useful Commands

```bash
# Build and start everything
docker compose up --build

# Start in background (detached)
docker compose up --build -d

# View gateway logs only
docker compose logs -f api-gateway

# Test direct access
curl localhost:9001/greeting | jq
curl localhost:9002/time | jq

# Test via gateway
curl localhost:9000/greeting | jq
curl localhost:9000/time | jq
curl localhost:9000/time/with-greeting | jq

# Test path rewrite
curl localhost:9000/hello | jq

# Stop everything
docker compose down
```
