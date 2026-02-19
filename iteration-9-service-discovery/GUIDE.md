# Iteration 9: Service Discovery (Eureka)

## What Problem Does This Solve?

After iteration 8, the system has **11 hardcoded service URLs** across 6 config files, plus 11 Docker-profile overrides in 3 more files:

```properties
# api-gateway/application.properties — 6 hardcoded URIs
order-service.uri=http://localhost:8080
restaurant-service.uri=http://localhost:8081
notification-service.uri=http://localhost:8082
accounting-service.uri=http://localhost:8083
kitchen-service.uri=http://localhost:8084
delivery-service.uri=http://localhost:8085

# order-service/application.properties — 4 hardcoded URLs
restaurant-service.url=http://localhost:8081
accounting-service.url=http://localhost:8083
kitchen-service.url=http://localhost:8084
delivery-service.url=http://localhost:8085

# delivery-service/application.properties — 1 hardcoded URL
kitchen-service.url=http://localhost:8084
```

Plus 11 Docker-profile overrides (e.g., `restaurant-service.url=http://restaurant-service:8081`).

This means:
- Adding a new service or changing a port means editing **multiple config files** manually
- You can't run multiple instances of a service for load balancing
- Docker Compose DNS masks the problem, but it's not a real solution
- No dynamic service discovery — services can't find each other without pre-configured hostnames and ports

## What Changed

| Component | Before (Iteration 8) | After (Iteration 9) |
|-----------|----------------------|----------------------|
| Service URLs | 11 hardcoded URLs + 11 Docker overrides | 0 hardcoded URLs, 0 Docker overrides |
| api-gateway routes | `${order-service.uri}` (property reference) | `lb://order-service` (Eureka lookup) |
| order-service clients | `@Value("${restaurant-service.url}")` + `new RestTemplate()` | Injected `@LoadBalanced RestTemplate` + `http://restaurant-service/...` |
| delivery-service client | `@Value("${kitchen-service.url}")` + `new RestTemplate()` | Injected `@LoadBalanced RestTemplate` + `http://kitchen-service/...` |
| New service | — | `eureka-server` (port 8761) — service registry + dashboard |

## Architecture

```
Before (Iteration 8):                          After (Iteration 9):

api-gateway                                    api-gateway
  routes[0].uri=http://order-service:8080        routes[0].uri=lb://order-service
  routes[1].uri=http://restaurant-service:8081   routes[1].uri=lb://restaurant-service
  ...6 hardcoded URIs                            ...all resolved via Eureka

order-service                                  order-service
  restaurant-service.url=http://...:8081         restTemplate → http://restaurant-service/...
  accounting-service.url=http://...:8083         (@LoadBalanced, discovered via Eureka)
  kitchen-service.url=http://...:8084
  delivery-service.url=http://...:8085

(11 hardcoded URLs, 11 Docker overrides)       (0 hardcoded URLs, 0 Docker overrides)
                                               + eureka-server:8761 (dashboard)
```

```
docker-compose up
├── kafka (single broker, KRaft, port 9092)
├── eureka-server (port 8761) ← NEW — service registry
├── restaurant-service (port 8081) ──depends_on──> eureka-server
├── accounting-service (port 8083) ──depends_on──> eureka-server
├── kitchen-service (port 8084) ──depends_on──> kafka, eureka-server
├── notification-service (port 8082) ──depends_on──> kafka, eureka-server
├── delivery-service (port 8085) ──depends_on──> kafka, eureka-server
├── order-service (port 8080) ──depends_on──> kafka, eureka-server
├── api-gateway (port 8090) ──depends_on──> eureka-server
└── ftgo-web (port 3000) ──depends_on──> api-gateway
```

## Key Files

| File | Action | Purpose |
|------|--------|---------|
| `eureka-server/pom.xml` | CREATE | `spring-cloud-starter-netflix-eureka-server` |
| `eureka-server/.../EurekaServerApplication.java` | CREATE | `@EnableEurekaServer` main class |
| `eureka-server/.../application.properties` | CREATE | Port 8761, standalone mode |
| `eureka-server/Dockerfile` | CREATE | Multi-stage build |
| `eureka-server/.dockerignore` | CREATE | Build exclusions |
| `order-service/.../RestTemplateConfig.java` | CREATE | `@LoadBalanced RestTemplate` bean |
| `delivery-service/.../RestTemplateConfig.java` | CREATE | `@LoadBalanced RestTemplate` bean |
| `api-gateway/application.properties` | MODIFY | Routes use `lb://`, add eureka |
| `api-gateway/application-docker.properties` | MODIFY | Remove URI overrides, add eureka docker URL |
| All 7 service `pom.xml` files | MODIFY | Add Spring Cloud BOM + eureka-client |
| All 7 service `application.properties` | MODIFY | Add eureka defaultZone |
| All 7 service `application-docker.properties` | CREATE/MODIFY | Eureka docker URL |
| 4 order-service client classes | MODIFY | Inject `RestTemplate`, use `http://service-name/...` |
| delivery-service `KitchenServiceClient` | MODIFY | Inject `RestTemplate`, use `http://kitchen-service/...` |
| `docker-compose.yml` | MODIFY | Add eureka-server, update all `depends_on` |

## How It Works

### Eureka Server (Service Registry)

The Eureka server is a standalone Spring Boot application that maintains a registry of all running service instances. It requires just two annotations and a few lines of config:

```java
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication { ... }
```

```properties
server.port=8761
eureka.client.register-with-eureka=false   # Don't register itself
eureka.client.fetch-registry=false         # No peers to fetch from
```

### Eureka Client (Service Registration)

Each service includes `spring-cloud-starter-netflix-eureka-client` and registers with Eureka on startup. The only configuration needed:

```properties
eureka.client.service-url.defaultZone=http://localhost:8761/eureka/
```

Spring Boot auto-detects the Eureka client dependency and automatically:
1. Registers the service with Eureka using `spring.application.name` as the service ID
2. Sends heartbeats every 30 seconds (default)
3. Fetches the registry from Eureka to know about other services

### `lb://` URIs (Gateway + Eureka)

In the api-gateway, route URIs changed from hardcoded URLs to load-balanced Eureka lookups:

```properties
# Before (iteration 8): hardcoded URIs
spring.cloud.gateway.routes[0].uri=${order-service.uri}

# After (iteration 9): Eureka discovery
spring.cloud.gateway.routes[0].uri=lb://order-service
```

The `lb://` scheme tells Spring Cloud Gateway to use the `ReactiveLoadBalancerClientFilter`, which:
1. Extracts the service name from the URI (`order-service`)
2. Queries the Eureka registry for instances of `order-service`
3. Picks one instance using round-robin load balancing
4. Forwards the request to that instance's actual `host:port`

### `@LoadBalanced RestTemplate` (Client-Side Load Balancing)

For the order-service and delivery-service, which make direct REST calls to other services, a `@LoadBalanced RestTemplate` bean replaces the hardcoded URL approach:

```java
// RestTemplateConfig.java — shared bean
@Bean
@LoadBalanced
public RestTemplate restTemplate() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(2000);
    factory.setReadTimeout(3000);
    return new RestTemplate(factory);
}
```

```java
// Before (iteration 8): hardcoded URL from properties
public RestaurantServiceClient(
        @Value("${restaurant-service.url}") String url, ...) {
    this.restTemplate = new RestTemplate(factory);
    this.restaurantServiceUrl = url;
}
// Usage: restTemplate.getForObject(restaurantServiceUrl + "/api/restaurants/{id}", ...);

// After (iteration 9): injected @LoadBalanced RestTemplate
public RestaurantServiceClient(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
}
// Usage: restTemplate.getForObject("http://restaurant-service/api/restaurants/{id}", ...);
```

The `@LoadBalanced` annotation adds a `LoadBalancerInterceptor` to the RestTemplate. When a request is made to `http://restaurant-service/api/restaurants/1`, the interceptor:
1. Intercepts the HTTP call before it goes out
2. Extracts the hostname (`restaurant-service`)
3. Queries the Eureka registry for instances
4. Replaces the hostname with the actual `host:port`
5. Makes the real HTTP call

This is **client-side load balancing** — the caller picks the target, not a central proxy.

### Eureka Dashboard

The Eureka server provides a web dashboard at http://localhost:8761 showing:
- All registered services and their instances
- Instance status (UP, DOWN, OUT_OF_SERVICE)
- Service health and heartbeat information
- Self-preservation mode status

### Self-Preservation Mode

Eureka has a self-preservation mode that prevents it from evicting services when it detects network issues. If the number of heartbeats drops below a threshold (85% of expected renewals), Eureka assumes the network is unreliable rather than the services being down, and keeps all registrations. This prevents cascading failures where a network partition causes Eureka to remove healthy services.

## Running the System

### Start Everything

```bash
cd iteration-9-service-discovery
docker compose up --build
```

### Check Eureka Dashboard

Open http://localhost:8761 — you should see all 7 services registered:
- `ORDER-SERVICE`
- `RESTAURANT-SERVICE`
- `NOTIFICATION-SERVICE`
- `ACCOUNTING-SERVICE`
- `KITCHEN-SERVICE`
- `DELIVERY-SERVICE`
- `API-GATEWAY`

### Test Gateway Routing (Same as Before — Now via Eureka)

```bash
curl http://localhost:8090/api/restaurants
curl http://localhost:8090/api/orders
curl http://localhost:8090/api/couriers
curl http://localhost:8090/actuator/health
```

### Test via UI

Open http://localhost:3000 and follow the same E2E flow:

1. **Consumer view** — Browse restaurants, view menu, place an order
2. **Restaurant view** — Accept ticket, mark as preparing, mark as ready
3. **Courier view** — Assign courier, pick up, deliver
4. **Order tracking** — Watch status progress: `PENDING` → `APPROVED` → `PREPARING` → `READY_FOR_PICKUP` → `PICKED_UP` → `DELIVERED`

### Check Gateway Logs

```bash
docker compose logs -f api-gateway
```

You'll see `lb://` URIs resolved to actual host:port in the forwarded requests.

### Stop Everything

```bash
docker compose down
```

## Teaching Concepts

### Service Discovery Pattern

In a microservices architecture, services need to find each other. There are two approaches:

1. **Client-side discovery**: The client queries a service registry (like Eureka) and picks an instance. This is what we implement here.
2. **Server-side discovery**: A load balancer (like AWS ALB) sits between the client and services, and the load balancer queries the registry.

Client-side discovery is simpler and avoids an extra network hop, but it couples the client to the registry.

### Eureka Server vs. Client

- **Eureka Server**: A standalone service that maintains the registry. It doesn't process business requests — it only stores and serves service instance metadata.
- **Eureka Client**: A library included in each service. On startup, it registers the service. Periodically, it sends heartbeats and fetches the latest registry.

### Client-Side Load Balancing

Traditional load balancing uses a centralized proxy (like NGINX). Client-side load balancing moves the decision to the caller. Each client holds a copy of the service registry and picks targets using a strategy (round-robin, random, weighted).

Advantages:
- No extra network hop through a proxy
- No single point of failure (the proxy)
- Each client can use different strategies

Disadvantages:
- Every client needs the load balancing logic
- Registry data may be slightly stale (cached, refreshed every 30s)

### `lb://` URI Scheme

The `lb://` scheme is specific to Spring Cloud Gateway. It tells the gateway to use its `ReactiveLoadBalancerClientFilter` instead of making a direct HTTP call. The service name after `lb://` is looked up in the Eureka registry.

### `@LoadBalanced RestTemplate`

The `@LoadBalanced` annotation is a Spring Cloud marker that triggers a `LoadBalancerInterceptor` to be added to the RestTemplate. This interceptor resolves service names to actual addresses before each request. Without this annotation, a RestTemplate call to `http://restaurant-service/api/restaurants` would fail because `restaurant-service` is not a DNS hostname — it's a Eureka service name.

### Zero Hardcoded URLs

The key outcome of this iteration is eliminating all hardcoded service URLs. Before, adding a service or changing a port required editing multiple config files. Now, a new service just needs to:
1. Include `spring-cloud-starter-netflix-eureka-client` in its `pom.xml`
2. Set `spring.application.name` (which Spring Boot does by default)
3. Point to the Eureka server URL

That's it — no other service needs to know its hostname or port.
