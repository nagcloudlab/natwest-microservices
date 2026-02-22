# Iteration 7: Containerize with Docker Compose

## What Problem Does This Solve?

After iteration 6, running the full system requires **8+ terminals** with manual startup coordination:

- 3 Kafka brokers (KRaft cluster)
- 6 Java microservices (`mvn spring-boot:run` each)
- 1 Next.js frontend (`npm run dev`)

Getting the startup order right is tedious and error-prone. Docker Compose replaces all of this with a single command.

## What Changed

| Component | Before (Iteration 6) | After (Iteration 7) |
|-----------|----------------------|----------------------|
| Kafka | Local 3-broker KRaft cluster | Single Docker container (`apache/kafka:3.7.0`) |
| Java services | `mvn spring-boot:run` x 6 terminals | 6 Docker containers with multi-stage builds |
| Next.js frontend | `npm run dev` in terminal | Docker container with `npm run dev` |
| Service URLs | `localhost:port` hardcoded | Container hostnames via Spring profiles |
| Startup | Manual, 8+ terminals | `docker-compose up` (single command) |
| Shutdown | Kill each process | `docker-compose down` (single command) |

## Architecture

```
docker-compose up
├── kafka (single broker, KRaft, port 9092)
├── restaurant-service (port 8081)
├── accounting-service (port 8083)
├── kitchen-service (port 8084) ──depends_on──> kafka
├── notification-service (port 8082) ──depends_on──> kafka
├── delivery-service (port 8085) ──depends_on──> kafka
├── order-service (port 8080) ──depends_on──> kafka, restaurant, accounting, kitchen, delivery
└── ftgo-web (port 3000) ──depends_on──> order-service
```

All containers share a Docker network. Services reference each other by container name (e.g., `http://restaurant-service:8081`) instead of `localhost`.

## Key Files Added

| File | Purpose |
|------|---------|
| `docker-compose.yml` | Orchestrates all 8 containers |
| `*/Dockerfile` | Multi-stage builds (Java) / dev build (Node.js) |
| `*/.dockerignore` | Exclude build artifacts from Docker context |
| `*/application-docker.properties` | Container hostname URL overrides |

## How It Works

### Docker Networking

When Docker Compose starts, it creates a shared network. Each container gets a hostname matching its service name. Instead of `localhost:8081`, services use `restaurant-service:8081`.

### Spring Profiles for Docker Config

Rather than modifying existing `application.properties`, each service that needs URL overrides gets an `application-docker.properties` file. Docker Compose activates it with:

```yaml
environment:
  SPRING_PROFILES_ACTIVE: docker
```

Spring Boot merges properties from both files, with the profile-specific file taking precedence. This means the same code runs locally (with `localhost`) or in Docker (with container hostnames).

**Services with Docker profiles:**
- `order-service` — overrides 4 REST URLs + Kafka bootstrap servers
- `delivery-service` — overrides kitchen-service URL + Kafka
- `kitchen-service` — overrides Kafka bootstrap servers
- `notification-service` — overrides Kafka bootstrap servers

**Services without Docker profiles (no outbound calls):**
- `restaurant-service`
- `accounting-service`

### Next.js Environment Variables

The `ftgo-web` frontend already reads service URLs from environment variables (with `localhost` fallbacks). Docker Compose passes container hostnames:

```yaml
environment:
  ORDER_SERVICE_URL: http://order-service:8080
  RESTAURANT_SERVICE_URL: http://restaurant-service:8081
  # ... etc
```

### Health Checks and Dependency Ordering

Kafka has a health check that verifies the broker is ready:

```yaml
healthcheck:
  test: /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
  interval: 10s
  timeout: 5s
  retries: 5
```

Services that depend on Kafka use `condition: service_healthy` to wait for Kafka to be fully ready before starting. Services without Kafka dependencies start immediately.

### Multi-Stage Docker Builds

Each Java service uses a two-stage Dockerfile:

1. **Build stage** (`maven:3.9-eclipse-temurin-17`) — downloads dependencies, compiles, packages the JAR
2. **Runtime stage** (`eclipse-temurin:17-jre`) — copies only the JAR, runs it

This keeps final images small (JRE only, no Maven/JDK/source code).

## Running the System

### Start Everything

```bash
cd iteration-7-docker
docker-compose up --build
```

The `--build` flag rebuilds images if source code changed. On first run, it downloads base images and builds all services (this takes a few minutes).

### Start in Background

```bash
docker-compose up --build -d
```

### View Logs

```bash
# All services
docker-compose logs -f

# Single service
docker-compose logs -f order-service

# Multiple services
docker-compose logs -f order-service kitchen-service
```

### Stop Everything

```bash
docker-compose down
```

### Rebuild a Single Service

```bash
docker-compose up --build order-service
```

## End-to-End Test

Once `docker-compose up` shows all services are ready, open http://localhost:3000 and follow the same flow as iteration 6:

1. **Consumer view** — Browse restaurants, view menu, place an order
2. **Restaurant view** — Accept ticket, mark as preparing, mark as ready
3. **Courier view** — Assign courier, pick up, deliver
4. **Order tracking** — Watch status progress: `PENDING` → `APPROVED` → `PREPARING` → `READY_FOR_PICKUP` → `PICKED_UP` → `DELIVERED`

## Teaching Concepts

### Containerization
Each service runs in its own isolated container with its own filesystem, network stack, and process space. The Dockerfile defines exactly how to build and run the service — making it reproducible on any machine with Docker installed.

### Docker Networking
Docker Compose creates a bridge network where containers discover each other by service name. This replaces `localhost` with DNS-based service discovery — the same pattern used in production orchestrators like Kubernetes.

### Health Checks
The `healthcheck` directive lets Docker monitor container health. Combined with `depends_on: condition: service_healthy`, this ensures services start in the correct order — Kafka must be fully ready before any Kafka-dependent service starts.

### Configuration Management
Spring profiles (`application-docker.properties`) demonstrate the Twelve-Factor App principle of environment-specific configuration. The same application code runs in different environments by swapping configuration, not code.

### Multi-Stage Builds
Separating build and runtime stages is a Docker best practice. The build stage includes Maven and JDK (hundreds of MB), but the final image only contains the JRE and the application JAR — resulting in smaller, more secure images.
