# Iteration 9 Simple Demo: Service Discovery with Eureka

> **Goal**: Teach service discovery concepts (dynamic registration, name-based routing, elimination of hardcoded URLs) using a 4-service app before showing the full FTGO system with 8 services + Eureka.
>
> **Duration**: ~30 minutes
>
> **Pre-requisites**: Docker Desktop installed, iteration-8 API Gateway demo completed

---

## The Services

| Service | Port | Purpose |
|---------|------|---------|
| **eureka-server** | 8761 | Service registry — all services register here |
| **greeting-service** | 9001 | Returns a greeting message + hostname |
| **time-service** | 9002 | Returns current time; calls greeting-service **via Eureka** |
| **api-gateway** | 9000 | Routes requests using **`lb://`** (Eureka-resolved) |

### What Changed from Iteration 8

| What | Iteration 8 (Hardcoded) | Iteration 9 (Eureka) |
|------|--------------------------|----------------------|
| time-service → greeting-service | `@Value("${greeting.service.url}")` → `http://localhost:9001` | `http://greeting-service/greeting` (Eureka resolves) |
| Gateway → greeting-service | `${greeting-service.uri}` → `http://localhost:9001` | `lb://greeting-service` |
| Gateway → time-service | `${time-service.uri}` → `http://localhost:9002` | `lb://time-service` |
| Docker overrides (gateway) | 2 URIs (`greeting-service.uri`, `time-service.uri`) | 1 eureka URL |
| Docker overrides (time-service) | 1 URI (`greeting.service.url`) | 1 eureka URL |
| **Total hardcoded URLs** | **5** (across 4 config files) | **0** (Eureka handles all) |

---

## Opening (2 min)

**Story to tell:**

> "In iteration 8, we solved the multiple-ports problem with an API Gateway. But look at our config files — we still have hardcoded URLs everywhere. `http://greeting-service:9001`, `http://time-service:9002`."
>
> "What if greeting-service moves to port 9005? What if we want to run 3 instances of it for load balancing? We'd have to update every config file that references it."
>
> "What if services could just **announce themselves** — 'I'm greeting-service, I'm at this address' — and other services could **look them up** by name? That's service discovery."

---

## Act 1: The Registry — Eureka Dashboard (5 min)

### Step 1 — Start everything

```bash
cd simple-demos/iteration-9-service-discovery
docker compose up --build
```

Wait for all four services to start. Eureka server starts first (healthcheck), then the other three register.

### Step 2 — Open the Eureka dashboard

Open your browser to: **http://localhost:8761**

> **Key moment**: "This is the Eureka dashboard. Look under 'Instances currently registered with Eureka' — you should see three services: GREETING-SERVICE, TIME-SERVICE, and API-GATEWAY. Each one registered itself automatically."

Point out:
- **Application name** — matches `spring.application.name` in each service's config
- **Status** — UP means the service is healthy and available
- **AMIs / Availability Zones** — ignore these, they're AWS concepts (Eureka was originally built by Netflix for AWS)

### How It Works

Show `eureka-server/src/main/resources/application.properties`:

```properties
server.port=8761
spring.application.name=eureka-server

# Standalone mode — don't try to register with itself
eureka.client.register-with-eureka=false
eureka.client.fetch-registry=false
```

Show `eureka-server/src/main/java/com/demo/eureka/EurekaServerApplication.java`:

```java
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication { ... }
```

> "That's it. One annotation (`@EnableEurekaServer`) and three properties. The dashboard, the registry, the health checks — all built in."

---

## Act 2: Service Registration — One Dependency, One Property (5 min)

### Step 3 — Show how a service registers

Open `greeting-service/pom.xml` and highlight the new dependency:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>
```

Open `greeting-service/src/main/resources/application.properties`:

```properties
server.port=9001
spring.application.name=greeting-service

eureka.client.service-url.defaultZone=http://localhost:8761/eureka/
```

> **Explain:**
> - **One dependency** (`eureka-client`) — tells Spring Boot "I'm a Eureka client"
> - **One property** (`defaultZone`) — tells the client where to find the registry
> - **`spring.application.name`** — this becomes the service's name in the registry. We already had this from iteration 8!
>
> "That's all it takes to register. No annotations needed on the main class. No code changes to greeting-service at all — the controller is identical to iteration 8."

### Docker Profile

Show `greeting-service/src/main/resources/application-docker.properties`:

```properties
eureka.client.service-url.defaultZone=http://eureka-server:8761/eureka/
```

> "In Docker, `localhost` doesn't work between containers — so we override with the Docker service name `eureka-server`. Compare this to iteration 8's Docker override, which had hardcoded service URLs. Now it's just one Eureka URL."

---

## Act 3: Discovery in Action — `@LoadBalanced` RestTemplate (8 min)

### Step 4 — The key change: how time-service calls greeting-service

**Iteration 8** — `TimeController.java` had:

```java
@Value("${greeting.service.url}")
private String greetingServiceUrl;

// ... used as:
restTemplate.getForObject(greetingServiceUrl + "/greeting", Map.class);
```

**Iteration 9** — `TimeController.java` now has:

```java
// No @Value! No injected URL!
restTemplate.getForObject("http://greeting-service/greeting", Map.class);
```

> **Key moment**: "Look at that URL — `http://greeting-service/greeting`. There's no port number. `greeting-service` isn't a hostname — it's the **Eureka service name**. The `@LoadBalanced` RestTemplate intercepts this call and asks Eureka: 'Where is greeting-service right now?' Eureka responds with the actual host and port."

### The Magic: `@LoadBalanced`

Show `TimeApplication.java`:

```java
@Bean
@LoadBalanced
public RestTemplate restTemplate() {
    return new RestTemplate();
}
```

> **Explain:**
> - **Without `@LoadBalanced`**: `http://greeting-service/greeting` would fail — there's no DNS entry for "greeting-service" on your laptop
> - **With `@LoadBalanced`**: Spring intercepts the call, looks up "greeting-service" in the Eureka registry, gets back the real address (e.g., `192.168.1.5:9001`), and makes the actual HTTP call
> - This is **client-side load balancing** — the calling service picks the target, not a central proxy

### Step 5 — Verify it works

```bash
curl localhost:9002/time/with-greeting | jq
```

```json
{
  "currentTime": "2025-01-15 14:30:22",
  "host": "f7e8d9c0b1a2",
  "greeting": {
    "message": "Hello from Greeting Service!",
    "host": "a1b2c3d4e5f6"
  }
}
```

> "time-service found greeting-service through Eureka, called it, and got the response. No hardcoded URL anywhere in the code or config."

### Discussion

> **Ask the class:**
> - "What happens if greeting-service crashes and restarts on a different port?" _(It re-registers with Eureka. time-service's next call looks up the new address automatically.)_
> - "What if we run 3 instances of greeting-service?" _(All 3 register with Eureka. `@LoadBalanced` picks one using round-robin.)_

---

## Act 4: Gateway with `lb://` — No More Hardcoded URIs (5 min)

### Step 6 — Show the gateway route changes

**Iteration 8** routes:

```properties
greeting-service.uri=http://localhost:9001
time-service.uri=http://localhost:9002

spring.cloud.gateway.routes[0].uri=${greeting-service.uri}
spring.cloud.gateway.routes[1].uri=${time-service.uri}
```

**Iteration 9** routes:

```properties
spring.cloud.gateway.routes[0].uri=lb://greeting-service
spring.cloud.gateway.routes[1].uri=lb://time-service
```

> **Explain:**
> - **`lb://`** stands for "load-balanced" — it tells Spring Cloud Gateway to resolve the service name through Eureka
> - No more `${greeting-service.uri}` property placeholders. No more Docker overrides for service URLs.
> - The gateway just says "route to greeting-service" and Eureka figures out where it is

### Step 7 — Verify gateway routing

```bash
# All through port 9000
curl localhost:9000/greeting | jq
curl localhost:9000/time | jq
curl localhost:9000/time/with-greeting | jq
curl localhost:9000/hello | jq
```

### Step 8 — Watch the gateway logs

```bash
docker compose logs -f api-gateway
```

Make some requests and watch the log output:

```
→ GET /greeting → http://172.18.0.3:9001/greeting | 200 | 45ms
→ GET /time → http://172.18.0.4:9002/time | 200 | 12ms
→ GET /hello → http://172.18.0.3:9001/greeting | 200 | 8ms
```

> "Notice the target URLs in the logs — they show actual IP addresses, not service names. That's Eureka resolution in action. The gateway said `lb://greeting-service`, Eureka resolved it to `172.18.0.3:9001`."

### Docker Override Comparison

**Iteration 8** `application-docker.properties` (api-gateway):

```properties
greeting-service.uri=http://greeting-service:9001
time-service.uri=http://time-service:9002
```

**Iteration 9** `application-docker.properties` (api-gateway):

```properties
eureka.client.service-url.defaultZone=http://eureka-server:8761/eureka/
```

> "Two service URLs replaced by one Eureka URL. And this scales — whether you have 3 services or 30, the Docker override is always just one Eureka URL."

---

## Act 5: What We Eliminated — Before/After (3 min)

Draw this on the whiteboard or show the comparison:

### Hardcoded URLs Eliminated

| File | Iteration 8 | Iteration 9 |
|------|-------------|-------------|
| `time-service/application.properties` | `greeting.service.url=http://localhost:9001` | _(removed)_ |
| `time-service/application-docker.properties` | `greeting.service.url=http://greeting-service:9001` | `eureka...=http://eureka-server:8761/eureka/` |
| `api-gateway/application.properties` | `greeting-service.uri=http://localhost:9001` | _(removed)_ |
| `api-gateway/application.properties` | `time-service.uri=http://localhost:9002` | _(removed)_ |
| `api-gateway/application-docker.properties` | `greeting-service.uri=http://greeting-service:9001` | `eureka...=http://eureka-server:8761/eureka/` |
| `api-gateway/application-docker.properties` | `time-service.uri=http://time-service:9002` | _(removed)_ |
| **Total hardcoded URLs** | **5** | **0** |

### What We Added

| Addition | Where | Lines of code |
|----------|-------|---------------|
| Eureka server | New service | ~15 lines (pom.xml + 1 class + 3 properties) |
| `eureka-client` dependency | Each service's pom.xml | 4 lines each |
| `eureka.client.service-url` | Each service's properties | 1 line each |
| `@LoadBalanced` | time-service's RestTemplate bean | 1 annotation |
| `lb://` prefix | Gateway route URIs | Replace `${property}` with `lb://name` |

> "A small amount of setup — one new service, one dependency per service, one property per service — and we eliminated ALL hardcoded URLs. Every service finds every other service through the registry."

---

## Bridge to FTGO (2 min)

> "We built service discovery with 3 services + Eureka. The FTGO system has 8 services + Eureka — but uses the exact same patterns."

| Pattern | Simple Demo | FTGO |
|---------|-------------|------|
| `@EnableEurekaServer` | Same | Same |
| `eureka-client` dependency | 3 services | 8 services |
| `@LoadBalanced RestTemplate` | time-service | order-service (calls 4 services) |
| `lb://` gateway routes | 3 routes | 7 routes |
| Service names as URLs | `http://greeting-service/...` | `http://restaurant-service/...` |

> "The only difference is scale. The concepts and code are identical."

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
| Hardcoded URLs break when services move | Eureka registry — services register themselves | Service Registration |
| Clients need to know host:port of every service | `@LoadBalanced` RestTemplate resolves names via Eureka | Client-Side Discovery |
| Gateway routes need hardcoded URIs | `lb://service-name` resolves through Eureka | Load-Balanced Routing |
| Docker overrides multiply with each service | One Eureka URL replaces all service URLs | Configuration Simplification |
| Scaling requires config changes | Multiple instances register; client picks one | Load Balancing |

---

## Discussion Questions

1. **Single Point of Failure**: "Eureka is the registry for everything. What if it crashes?" _(Services cache the registry locally. Short outages are fine. For production, run multiple Eureka instances that replicate to each other.)_

2. **Health Checks**: "How does Eureka know a service is still alive?" _(Services send heartbeats every 30 seconds. If Eureka doesn't hear from a service for 90 seconds, it removes it from the registry.)_

3. **Client-Side vs Server-Side Discovery**: "The `@LoadBalanced` RestTemplate picks the target instance. What's the alternative?" _(Server-side discovery — a load balancer sits between services. Kubernetes does this with its built-in DNS and Services. Trade-off: simpler clients vs. extra infrastructure.)_

4. **DNS vs Eureka**: "Docker Compose already gives us DNS — `greeting-service` resolves to the container IP. Why do we need Eureka?" _(DNS doesn't handle multiple instances, health-aware routing, or dynamic registration. Eureka gives you all three.)_

5. **Configuration**: "We still have `eureka.client.service-url.defaultZone` hardcoded. What if the Eureka server moves?" _(Leads to centralized configuration — iteration 10, Spring Cloud Config Server)_

---

## Quick Reference: Useful Commands

```bash
# Build and start everything
docker compose up --build

# Start in background (detached)
docker compose up --build -d

# View Eureka dashboard
open http://localhost:8761

# View gateway logs (see Eureka-resolved targets)
docker compose logs -f api-gateway

# Test direct access
curl localhost:9001/greeting | jq
curl localhost:9002/time | jq
curl localhost:9002/time/with-greeting | jq

# Test via gateway (lb:// resolved)
curl localhost:9000/greeting | jq
curl localhost:9000/time | jq
curl localhost:9000/time/with-greeting | jq

# Test path rewrite
curl localhost:9000/hello | jq

# Stop everything
docker compose down
```
