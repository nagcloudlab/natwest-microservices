# Iteration 8: API Gateway (Spring Cloud Gateway)

## What Problem Does This Solve?

After iteration 7, the `ftgo-web` frontend connects directly to **6 backend services** via 6 separate URLs:

```yaml
# iteration-7 docker-compose.yml — ftgo-web environment
ORDER_SERVICE_URL: http://order-service:8080
RESTAURANT_SERVICE_URL: http://restaurant-service:8081
NOTIFICATION_SERVICE_URL: http://notification-service:8082
ACCOUNTING_SERVICE_URL: http://accounting-service:8083
KITCHEN_SERVICE_URL: http://kitchen-service:8084
DELIVERY_SERVICE_URL: http://delivery-service:8085
```

This means:
- The frontend must know about **every service's hostname and port** — tight coupling
- Adding a new service means updating the frontend config
- There's no centralized place for cross-cutting concerns (logging, CORS, rate limiting)
- If a service changes its port, the frontend breaks

## What Changed

| Component | Before (Iteration 7) | After (Iteration 8) |
|-----------|----------------------|----------------------|
| ftgo-web env vars | 6 service URLs | 1 gateway URL (`API_GATEWAY_URL`) |
| Request routing | ftgo-web routes to individual services | ftgo-web routes everything to gateway |
| Cross-cutting concerns | None centralized | Gateway handles logging, CORS |
| New service added | — | `api-gateway` (port 8090) on WebFlux/Netty |

## Architecture

```
Before (Iteration 7):                     After (Iteration 8):

ftgo-web ──→ order-service:8080           ftgo-web ──→ api-gateway:8090 ──→ order-service:8080
         ──→ restaurant-service:8081                                    ──→ restaurant-service:8081
         ──→ notification-service:8082                                  ──→ notification-service:8082
         ──→ accounting-service:8083                                    ──→ accounting-service:8083
         ──→ kitchen-service:8084                                       ──→ kitchen-service:8084
         ──→ delivery-service:8085                                      ──→ delivery-service:8085
(6 URLs)                                  (1 URL)
```

```
docker-compose up
├── kafka (single broker, KRaft, port 9092)
├── restaurant-service (port 8081)
├── accounting-service (port 8083)
├── kitchen-service (port 8084) ──depends_on──> kafka
├── notification-service (port 8082) ──depends_on──> kafka
├── delivery-service (port 8085) ──depends_on──> kafka
├── order-service (port 8080) ──depends_on──> kafka, restaurant, accounting, kitchen, delivery
├── api-gateway (port 8090) ──depends_on──> all 6 services        ← NEW
└── ftgo-web (port 3000) ──depends_on──> api-gateway              ← CHANGED
```

## Key Files

| File | Action | Purpose |
|------|--------|---------|
| `api-gateway/pom.xml` | CREATE | Maven project: spring-cloud-starter-gateway |
| `api-gateway/.../ApiGatewayApplication.java` | CREATE | Spring Boot main class |
| `api-gateway/.../LoggingFilter.java` | CREATE | Global request/response logging |
| `api-gateway/.../application.properties` | CREATE | 7 routes + CORS + actuator |
| `api-gateway/.../application-docker.properties` | CREATE | Docker hostname URI overrides |
| `api-gateway/Dockerfile` | CREATE | Multi-stage build |
| `api-gateway/.dockerignore` | CREATE | Build context exclusions |
| `docker-compose.yml` | MODIFY | Add api-gateway, simplify ftgo-web |
| `ftgo-web/lib/proxy.ts` | MODIFY | 6 URLs → 1 gateway URL |

## How It Works

### Gateway Route Table

The gateway routes requests based on URL path prefixes:

| Incoming Path | Target Service | Notes |
|---------------|---------------|-------|
| `/api/orders/**` | `order-service:8080` | Pass-through |
| `/api/restaurants/**` | `restaurant-service:8081` | Pass-through |
| `/api/notifications/**` | `notification-service:8082` | Pass-through |
| `/api/payments/**` | `accounting-service:8083` | Pass-through |
| `/api/kitchen/**` | `kitchen-service:8084` | Pass-through |
| `/api/deliveries/**` | `delivery-service:8085` | Pass-through |
| `/api/couriers/**` | `delivery-service:8085` | **RewritePath** → `/api/deliveries/couriers/**` |

### Path-Based Routing

When a request arrives at the gateway (e.g., `GET /api/orders/1`), Spring Cloud Gateway:

1. Matches the path against configured route predicates
2. Finds the matching route (`/api/orders/**` → `order-service:8080`)
3. Forwards the request to `http://order-service:8080/api/orders/1`
4. Returns the response to the caller

The request path is preserved — the backend service sees the same `/api/orders/1` path it would see with a direct call.

### Path Rewriting (Couriers Route)

The `/api/couriers` path is a special case. The delivery service exposes couriers under `/api/deliveries/couriers`, but the frontend uses `/api/couriers` for cleaner URLs. The gateway rewrites the path:

```
GET /api/couriers           → GET http://delivery-service:8085/api/deliveries/couriers
GET /api/couriers/courier-1 → GET http://delivery-service:8085/api/deliveries/couriers/courier-1
```

This is configured with Spring Cloud Gateway's `RewritePath` filter:

```properties
spring.cloud.gateway.routes[6].filters[0]=RewritePath=/api/couriers(?<segment>/?.*), /api/deliveries/couriers${segment}
```

### Simplified Frontend Proxy

The `ftgo-web` proxy was simplified from a 6-URL service map to a single gateway URL:

```typescript
// Before (iteration 7): 6 URLs, one per service
const SERVICE_URLS: Record<string, string> = {
  orders: process.env.ORDER_SERVICE_URL || "http://localhost:8080",
  restaurants: process.env.RESTAURANT_SERVICE_URL || "http://localhost:8081",
  // ... 4 more
};

// After (iteration 8): 1 URL for everything
const GATEWAY_URL = process.env.API_GATEWAY_URL || "http://localhost:8090";

export function getServiceUrl(_service: string): string {
  return GATEWAY_URL;
}
```

No changes were needed to any of the 19 API route files. They all construct paths like `${getServiceUrl("orders")}/api/orders/${id}` — the gateway routes based on the `/api/orders` prefix.

### Global Logging Filter

The `LoggingFilter` is a Spring Cloud Gateway `GlobalFilter` that logs every routed request:

```
→ GET /api/restaurants → http://restaurant-service:8081/api/restaurants | 200 | 12ms
→ POST /api/orders → http://order-service:8080/api/orders | 201 | 45ms
```

Each log line shows: HTTP method, incoming path, target URL, response status, and duration. This provides centralized visibility into all API traffic.

### Spring Cloud Gateway Reactive Stack

Spring Cloud Gateway is built on **Spring WebFlux** and **Netty**, not the traditional Spring MVC / Tomcat stack. This is why the `pom.xml` includes `spring-cloud-starter-gateway` but **not** `spring-boot-starter-web` — including both would cause a conflict.

WebFlux uses a non-blocking, reactive programming model (Project Reactor). This makes the gateway efficient at handling many concurrent connections, since it doesn't allocate a thread per request like the Servlet model.

## Running the System

### Start Everything

```bash
cd iteration-8-api-gateway
docker compose up --build
```

### Test Gateway Routing

```bash
# Direct gateway calls:
curl http://localhost:8090/api/restaurants
curl http://localhost:8090/api/orders
curl http://localhost:8090/api/couriers      # path rewrite to /api/deliveries/couriers

# Gateway health check:
curl http://localhost:8090/actuator/health
```

### Test via UI

Open http://localhost:3000 and follow the same E2E flow — the frontend now routes through the gateway instead of directly to services:

1. **Consumer view** — Browse restaurants, view menu, place an order
2. **Restaurant view** — Accept ticket, mark as preparing, mark as ready
3. **Courier view** — Assign courier, pick up, deliver
4. **Order tracking** — Watch status progress: `PENDING` → `APPROVED` → `PREPARING` → `READY_FOR_PICKUP` → `PICKED_UP` → `DELIVERED`

### Check Gateway Logs

```bash
docker compose logs -f api-gateway
```

You'll see every request logged with method, path, target, status, and duration.

### Stop Everything

```bash
docker compose down
```

## Teaching Concepts

### API Gateway Pattern

An API Gateway is a single entry point for all client requests to a microservices system. Instead of clients knowing about and connecting to each service individually, they send all requests to the gateway, which routes them to the correct backend. This is one of the most common microservices patterns, used by Netflix (Zuul/Spring Cloud Gateway), Amazon (API Gateway), and Kong.

### Path-Based Routing

The gateway inspects the URL path to determine which backend service should handle the request. This is the simplest routing strategy — each service "owns" a path prefix (`/api/orders`, `/api/restaurants`, etc.). More complex strategies include header-based routing, query parameter routing, or weighted routing for canary deployments.

### Path Rewriting

Sometimes the external API structure doesn't match the internal service structure. Path rewriting lets the gateway present a clean external API while mapping to different internal paths. In our case, `/api/couriers` maps to `/api/deliveries/couriers` on the delivery service.

### Cross-Cutting Concerns

The gateway is the ideal place for concerns that apply to all requests:
- **Logging** — centralized request/response logging (implemented here)
- **CORS** — centralized CORS configuration (implemented here)
- **Rate limiting** — protect backend services from abuse
- **Authentication** — verify tokens before requests reach services
- **Circuit breaking** — fail fast when backend services are down

### Reactive Programming (WebFlux/Netty)

Spring Cloud Gateway uses a non-blocking I/O model. Traditional Spring MVC allocates one thread per request (thread-per-request model). WebFlux uses event loops — a small number of threads handle many concurrent connections by never blocking. This is ideal for a gateway, which spends most of its time waiting for backend responses.
