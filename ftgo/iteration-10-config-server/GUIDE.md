# Iteration 10: Centralized Configuration (Spring Cloud Config Server)

## What Problem Does This Solve?

After iteration 9, each service has its own `application.properties` and `application-docker.properties`. There is **massive config duplication** across 7 services:

```
eureka.client.service-url.defaultZone  — duplicated in 14 files (7 services × 2 profiles)
spring.datasource.driver-class-name    — duplicated in 6 services
spring.datasource.username/password    — duplicated in 6 services
spring.jpa.hibernate.ddl-auto          — duplicated in 6 services
spring.jpa.show-sql / format_sql       — duplicated in 6 services
spring.h2.console.enabled/path         — duplicated in 6 services
spring.kafka.bootstrap-servers         — duplicated in 4 services + 4 docker overrides
spring.kafka.producer/consumer config  — duplicated in 4 services
```

This means:
- Changing a shared setting (e.g., Eureka URL, Kafka brokers) means editing **multiple files** across **multiple services**
- There is no single source of truth for shared configuration
- Environment-specific overrides (dev, docker, staging, prod) are scattered across individual services
- Risk of config drift — one service gets updated, others don't

## What Changed

| Component | Before (Iteration 9) | After (Iteration 10) |
|-----------|----------------------|----------------------|
| Config location | Each service has 15-60 lines of local config | Each service has 2 lines (name + config.import) |
| Shared settings | Duplicated across 6-14 files | Single `application.properties` in config-repo |
| Docker overrides | Duplicated across 4-7 files | Single `application-docker.properties` in config-repo |
| Service-specific | Mixed with shared config | Separate `{service-name}.properties` files |
| New service | config-server (port 8888) | Serves config to all services on startup |

## Architecture

```
Before (Iteration 9):                          After (Iteration 10):

7 services × application.properties             config-server (port 8888)
  (each has eureka, DB, JPA, Kafka, etc.)          └── config-repo/
7 services × application-docker.properties              ├── application.properties (shared)
  (each overrides eureka, kafka)                        ├── application-docker.properties (shared docker)
                                                        ├── order-service.properties
~100 lines of duplicated config                         ├── restaurant-service.properties
                                                        ├── accounting-service.properties
                                                        ├── kitchen-service.properties
                                                        ├── notification-service.properties
                                                        ├── delivery-service.properties
                                                        ├── api-gateway.properties
                                                        └── api-gateway-docker.properties

Each service's local application.properties:    Each service's local application.properties:
  15-60 lines of config                           spring.application.name=order-service
                                                  spring.config.import=optional:configserver:...
                                                  (2 lines)
```

```
Startup order:
  eureka-server (8761) ─┐
                        ├──> config-server (8888) ──> all 7 services
  kafka ────────────────┘                              (fetch config on startup)
```

```
docker-compose up
├── kafka (port 9092)
├── eureka-server (port 8761)
├── config-server (port 8888) ← NEW — depends_on eureka-server
├── restaurant-service (port 8081) ──depends_on──> config-server
├── accounting-service (port 8083) ──depends_on──> config-server
├── kitchen-service (port 8084) ──depends_on──> kafka, config-server
├── notification-service (port 8082) ──depends_on──> kafka, config-server
├── delivery-service (port 8085) ──depends_on──> kafka, config-server
├── order-service (port 8080) ──depends_on──> kafka, config-server
├── api-gateway (port 8090) ──depends_on──> config-server
└── ftgo-web (port 3000) ──depends_on──> api-gateway
```

## Key Files

| File | Action | Purpose |
|------|--------|---------|
| `config-server/pom.xml` | CREATE | `spring-cloud-config-server` + eureka-client + actuator |
| `config-server/.../ConfigServerApplication.java` | CREATE | `@EnableConfigServer` main class |
| `config-server/.../application.properties` | CREATE | Port 8888, native profile, config-repo location |
| `config-server/.../application-docker.properties` | CREATE | Eureka docker URL |
| `config-server/Dockerfile` | CREATE | Multi-stage build |
| `config-server/.dockerignore` | CREATE | Build exclusions |
| `config-repo/application.properties` | CREATE | Shared config (eureka, H2, JPA, Kafka defaults) |
| `config-repo/application-docker.properties` | CREATE | Shared docker overrides (eureka, kafka) |
| `config-repo/order-service.properties` | CREATE | Port, DB, Resilience4j, Kafka consumer group |
| `config-repo/restaurant-service.properties` | CREATE | Port, DB, seed data |
| `config-repo/accounting-service.properties` | CREATE | Port, DB |
| `config-repo/kitchen-service.properties` | CREATE | Port, DB |
| `config-repo/notification-service.properties` | CREATE | Port, DB, Kafka consumer group |
| `config-repo/delivery-service.properties` | CREATE | Port, DB, seed data |
| `config-repo/api-gateway.properties` | CREATE | Port, routes, CORS, actuator |
| `config-repo/api-gateway-docker.properties` | CREATE | CORS docker override |
| All 7 service `pom.xml` | MODIFY | Add `spring-cloud-starter-config` |
| All 7 service `application.properties` | MODIFY | Strip to 2 lines (name + config.import) |
| All 7 service `application-docker.properties` | MODIFY | Strip to 1 line (config.import docker URL) |
| `docker-compose.yml` | MODIFY | Add config-server, update depends_on |

## How It Works

### Config Server (Native Profile)

The config server is a Spring Boot application with `@EnableConfigServer`. It serves configuration from a `config-repo/` directory on the classpath using the `native` profile (no Git repository needed):

```java
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication { ... }
```

```properties
server.port=8888
spring.profiles.active=native
spring.cloud.config.server.native.search-locations=classpath:/config-repo
eureka.client.service-url.defaultZone=http://localhost:8761/eureka/
```

### Config Resolution Order

When a service (e.g., `order-service` with profile `docker`) starts and contacts the config server, it receives configuration merged from multiple files in this priority order (highest wins):

1. `order-service-docker.properties` (service-specific + profile-specific) — highest priority
2. `order-service.properties` (service-specific)
3. `application-docker.properties` (shared + profile-specific)
4. `application.properties` (shared defaults) — lowest priority

This means:
- **Shared defaults** (`application.properties`) apply to all services — eureka, H2, JPA, Kafka
- **Shared docker overrides** (`application-docker.properties`) override hostnames for all services in Docker
- **Service-specific** files (`order-service.properties`) set port, DB name, Resilience4j, etc.
- **Service+profile** files (`api-gateway-docker.properties`) handle service-specific Docker overrides

### Config Client (Each Service)

Each service includes `spring-cloud-starter-config` and has a minimal `application.properties`:

```properties
spring.application.name=order-service
spring.config.import=optional:configserver:http://localhost:8888
```

On startup, the service:
1. Reads its `spring.application.name` (this tells the config server which service-specific file to serve)
2. Contacts the config server at the URL specified in `spring.config.import`
3. Receives its merged configuration (shared + service-specific + profile-specific)
4. Uses the received properties as if they were local

The `optional:` prefix ensures the service can still start without the config server (e.g., for local development).

### Docker Profile Override

Each service's `application-docker.properties` overrides the config server URL for Docker networking:

```properties
spring.config.import=optional:configserver:http://config-server:8888
```

In Docker, the config server is reachable at `config-server:8888` (Docker DNS), not `localhost:8888`.

### Config Server REST API

The config server exposes a REST API that returns configuration for any service/profile combination:

```bash
# Get order-service default config
curl http://localhost:8888/order-service/default

# Get restaurant-service docker config
curl http://localhost:8888/restaurant-service/docker

# Get shared config (no specific service)
curl http://localhost:8888/application/default
```

## Running the System

### Start Everything

```bash
cd iteration-10-config-server
docker compose up --build
```

### Check Eureka Dashboard

Open http://localhost:8761 — you should see all 8 services registered (including config-server):
- `CONFIG-SERVER`
- `ORDER-SERVICE`
- `RESTAURANT-SERVICE`
- `NOTIFICATION-SERVICE`
- `ACCOUNTING-SERVICE`
- `KITCHEN-SERVICE`
- `DELIVERY-SERVICE`
- `API-GATEWAY`

### Check Config Server

```bash
# Verify config server serves config for each service
curl http://localhost:8888/order-service/default
curl http://localhost:8888/restaurant-service/docker
curl http://localhost:8888/api-gateway/default
```

### Test Gateway Routing (Same as Before)

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
4. **Order tracking** — Watch status progress: `PENDING` -> `APPROVED` -> `PREPARING` -> `READY_FOR_PICKUP` -> `PICKED_UP` -> `DELIVERED`

### Stop Everything

```bash
docker compose down
```

## Teaching Concepts

### Centralized Configuration Pattern

In a microservices architecture, configuration management becomes complex as the number of services grows. Common settings (database drivers, messaging brokers, service discovery URLs) are duplicated across every service. The Centralized Configuration pattern solves this by:

1. **Single source of truth**: All configuration lives in one place (the config-repo)
2. **Environment-specific overrides**: Profile-based files (e.g., `application-docker.properties`) handle environment differences
3. **Service-specific config**: Each service gets its own file for unique settings (port, database name)
4. **No config duplication**: Shared settings are defined once and inherited by all services

### Native vs. Git Backend

Spring Cloud Config Server supports multiple backends:

- **Native** (classpath/filesystem): Configuration files are stored locally. Simple, no external dependencies. This is what we use here.
- **Git**: Configuration is stored in a Git repository. Provides versioning, audit trail, and the ability to update config without redeploying the config server.
- **Vault**: For sensitive configuration (secrets, credentials). Integrates with HashiCorp Vault.

We use `native` because it keeps everything self-contained — the config files are part of the config-server's JAR, deployed as one unit.

### `optional:` Prefix

The `optional:` prefix in `spring.config.import=optional:configserver:http://localhost:8888` is critical for local development. Without it, the service would **fail to start** if the config server is unreachable. With `optional:`, the service falls back gracefully — it simply starts without the remote config. This allows developers to run individual services locally without starting the config server first.

### Config Server vs. Environment Variables

You might wonder: why not just use environment variables in Docker Compose? Environment variables work well for a few overrides, but they don't scale:

- Config files support hierarchical property merging (shared + service-specific + profile-specific)
- Config server provides a REST API to inspect configuration
- Config can be updated centrally without changing Docker Compose
- The same config-repo works across all environments (dev, docker, staging, prod)

### Startup Order

Config server must start after Eureka (so it can register itself) and before all other services (so they can fetch their configuration). In Docker Compose, this is enforced with `depends_on` + `healthcheck`:

```yaml
config-server:
  depends_on:
    eureka-server:
      condition: service_healthy
  healthcheck:
    test: curl -f http://localhost:8888/actuator/health || exit 1

all-other-services:
  depends_on:
    config-server:
      condition: service_healthy
```

The `service_healthy` condition ensures each service waits for the dependency to be fully ready, not just started.
