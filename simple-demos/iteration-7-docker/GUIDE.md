# Iteration 7 Simple Demo: Docker

> **Goal**: Teach containerization concepts using a trivial 2-service app before showing the full FTGO Docker setup.
>
> **Duration**: ~30 minutes
>
> **Pre-requisites**: Docker Desktop installed, Java 17, Maven

---

## The Services

| Service | Port | Endpoints | Purpose |
|---------|------|-----------|---------|
| **greeting-service** | 9001 | `GET /greeting` | Returns a greeting message + hostname |
| **time-service** | 9002 | `GET /time`, `GET /time/with-greeting` | Returns current time; optionally calls greeting-service |

The `host` field in each response is the key teaching tool — it will show a container ID when running in Docker vs your machine's hostname when running locally.

---

## Opening (2 min)

**Story to tell:**

> "Your teammate sends you a Slack message: _'I pulled the latest code and it won't start. I get a Java version error.'_ You check — you're on Java 17, they're on Java 11. You spend 30 minutes debugging their setup. Sound familiar?"
>
> "Or imagine this: you have 6 microservices. A new developer joins. Day 1: install Java, install Maven, install Kafka, configure environment variables, set up the database... they don't write a single line of code until day 3."
>
> "Docker solves both problems. Let's see how."

---

## Act 1: Run Locally (5 min)

### Step 1 — Start greeting-service

```bash
cd simple-demos/iteration-7-docker/greeting-service
mvn spring-boot:run
```

### Step 2 — Test it (new terminal)

```bash
curl localhost:9001/greeting | jq
```

Expected response:
```json
{
  "message": "Hello from Greeting Service!",
  "host": "your-laptop-hostname"
}
```

> **Point out**: The `host` field shows YOUR machine's hostname. Remember this — it will change later.

### Step 3 — Start time-service (another terminal)

```bash
cd simple-demos/iteration-7-docker/time-service
mvn spring-boot:run
```

### Step 4 — Test both endpoints

```bash
curl localhost:9002/time | jq
```
```json
{
  "currentTime": "2025-01-15 14:30:22",
  "host": "your-laptop-hostname"
}
```

```bash
curl localhost:9002/time/with-greeting | jq
```
```json
{
  "currentTime": "2025-01-15 14:30:25",
  "host": "your-laptop-hostname",
  "greeting": {
    "message": "Hello from Greeting Service!",
    "host": "your-laptop-hostname"
  }
}
```

> **Point out**: Both `host` fields show the same hostname — your laptop. Both services run on the same machine.

### Stop both services (Ctrl+C in each terminal)

### Problem Setup

> "That worked, but notice what we needed:
> - **2 terminal windows** (imagine 8 services...)
> - **Java 17 installed** (what if your teammate has Java 11?)
> - **Maven installed** (what if they're on a different version?)
> - We're relying on _everyone_ having the exact same setup."

---

## Act 2: Dockerfile — Packaging (10 min)

### Problem 1: "It Works on My Machine"

> "How do we guarantee the EXACT same environment — same Java version, same dependencies — everywhere? On every developer's machine, in CI/CD, in production?"

### Solution: The Dockerfile

Open `greeting-service/Dockerfile` and walk through it:

```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 9001
ENTRYPOINT ["java", "-jar", "app.jar"]
```

> **Explain each line:**
> - `FROM maven:3.9-eclipse-temurin-17 AS build` — "We start from an image that already has Java 17 and Maven. Everyone uses the same one."
> - `COPY pom.xml` + `dependency:go-offline` — "Download dependencies first. Docker caches this layer, so rebuilds are fast."
> - `COPY src` + `mvn package` — "Build the application inside the container."
> - `FROM eclipse-temurin:17-jre` — "Second stage: a fresh, smaller image with just the JRE."
> - `COPY --from=build` — "Copy only the JAR from the build stage. No source code, no Maven, no build tools."

### Live Demo: Build and Run

```bash
cd simple-demos/iteration-7-docker/greeting-service

# Build the image
docker build -t greeting-service .

# Check the image size
docker images greeting-service
```

> **Pause here**: "Notice the image size. It should be around 250-300MB. That includes the JRE and our tiny app."

```bash
# Run the container
docker run -p 9001:9001 greeting-service
```

```bash
# Test it (new terminal)
curl localhost:9001/greeting | jq
```

```json
{
  "message": "Hello from Greeting Service!",
  "host": "a1b2c3d4e5f6"
}
```

> **Key moment**: "Look at the `host` field! It's not your laptop's hostname anymore — it's a container ID. The app is running in an **isolated environment** with its own hostname, its own filesystem, its own network stack. This is Docker's isolation in action."

### Discussion: What's in the Image?

> **Ask the class:**
> - "What's inside this image?" (JRE + JAR — no source code, no Maven, no build tools)
> - "What's NOT in the image?" (Source code, test files, Maven cache, IDE config)
> - "Why does this matter?" (Security — less attack surface. Size — faster deploys.)
> - "Where does this image run identically?" (Your laptop, teammate's laptop, CI server, production)

### Problem 2: Why Two Stages?

> "Why didn't we just use a single `FROM maven` and ship the whole thing?"

Show the size difference:

```bash
# Single-stage (hypothetical): ~500-600MB (full JDK + Maven + source)
# Multi-stage (what we built): ~250-300MB (just JRE + JAR)
```

> "The build stage had Maven, JDK, source code, downloaded dependencies — easily 500MB+. We threw all that away and kept just the 5MB JAR on a slim JRE base. In production, you don't need a compiler."

### Stop the container (Ctrl+C)

---

## Act 3: Docker Compose — Orchestration (10 min)

### Problem 3: Starting Multiple Services

> "We containerized greeting-service. We could do the same for time-service. But now we need TWO `docker run` commands, with the right ports, in the right order. Imagine doing this for 8 services."

```bash
# Without Docker Compose, you'd need:
docker run -p 9001:9001 greeting-service
docker run -p 9002:9002 -e SPRING_PROFILES_ACTIVE=docker time-service
# ...imagine 8 of these
```

### Solution: docker-compose.yml

Open `docker-compose.yml` and walk through it:

```yaml
services:

  greeting-service:
    build: ./greeting-service
    ports:
      - "9001:9001"
    healthcheck:
      test: wget --quiet --tries=1 --spider http://localhost:9001/actuator/health || exit 1
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s

  time-service:
    build: ./time-service
    ports:
      - "9002:9002"
    environment:
      SPRING_PROFILES_ACTIVE: docker
    depends_on:
      greeting-service:
        condition: service_healthy
```

> **Explain each section:**
> - `build: ./greeting-service` — "Build from the Dockerfile in that directory."
> - `ports: "9001:9001"` — "Map host port to container port."
> - `healthcheck` — "Docker will periodically check if the service is actually ready (not just started). We'll come back to this."
> - `environment: SPRING_PROFILES_ACTIVE: docker` — "Activates the `docker` Spring profile for container-specific config."
> - `depends_on` with `condition: service_healthy` — "Don't start time-service until greeting-service is healthy."

### Live Demo: One Command to Rule Them All

```bash
cd simple-demos/iteration-7-docker
docker compose up --build
```

> "One command. Both services build, start in the right order, and connect to the same network. Watch the logs — you'll see greeting-service start first, pass its health check, then time-service starts."

### Test everything

```bash
curl localhost:9001/greeting | jq
curl localhost:9002/time | jq
curl localhost:9002/time/with-greeting | jq
```

> **Key moment with `/time/with-greeting`**: "Look at the response. The outer `host` is one container ID (time-service). The inner `host` inside `greeting` is a DIFFERENT container ID (greeting-service). Two isolated containers talking to each other!"

```json
{
  "currentTime": "2025-01-15 14:35:10",
  "host": "f7e8d9c0b1a2",
  "greeting": {
    "message": "Hello from Greeting Service!",
    "host": "a1b2c3d4e5f6"
  }
}
```

### Problem 4: How Did time-service Find greeting-service?

> "When we ran locally, time-service called `http://localhost:9001`. But in Docker, each container is isolated — greeting-service isn't on `localhost` from time-service's perspective. How does this work?"

Show the two property files:

**`application.properties`** (used locally):
```properties
greeting.service.url=http://localhost:9001
```

**`application-docker.properties`** (used in Docker):
```properties
greeting.service.url=http://greeting-service:9001
```

> **Explain**: "When `SPRING_PROFILES_ACTIVE=docker`, Spring loads `application-docker.properties` which overrides the URL. Instead of `localhost`, we use `greeting-service` — the **service name from docker-compose.yml**. Docker Compose creates a network where each service is reachable by its name, like a mini DNS server."

### Discussion: Docker Networking

> **Ask the class:**
> - "What acts as the DNS server inside Docker?" (Docker's built-in DNS)
> - "Can containers talk to each other by IP?" (Yes, but don't — IPs can change. Use service names.)
> - "What if you have two unrelated docker-compose projects?" (They get separate networks — isolated by default)

---

## Act 4: Health Checks & Startup Order (5 min)

### Problem 5: Race Conditions at Startup

> "What happens if time-service starts and immediately tries to call greeting-service, but greeting-service is still booting up?"

### Solution: Health Checks + `depends_on` with Conditions

Point back to the docker-compose.yml:

```yaml
greeting-service:
    healthcheck:
      test: wget --quiet --tries=1 --spider http://localhost:9001/actuator/health || exit 1
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s

time-service:
    depends_on:
      greeting-service:
        condition: service_healthy    # NOT just "service_started"!
```

> **Explain the difference:**
> - `service_started` — "Container process started." (JVM is booting, Spring isn't ready)
> - `service_healthy` — "Health check passed." (Spring is fully up, endpoint is responding)
>
> "The health check hits Spring Boot Actuator's `/actuator/health` endpoint. Only when it returns HTTP 200 does Docker consider the service healthy. Then — and only then — does time-service start."

### Discussion: Is This Enough?

> **Ask the class:**
> - "Does `depends_on` guarantee greeting-service STAYS healthy?" (No — only at startup)
> - "What if greeting-service crashes after time-service starts?" (time-service will get errors — leads to resilience patterns like Circuit Breaker, which we covered in iteration 3)
> - "Is there a better way than health checks for production?" (Readiness probes in Kubernetes — but that's a future topic)

---

## Bridge to FTGO (2 min)

> "We just containerized 2 simple services. Now imagine doing this with **8 services** — order-service, restaurant-service, notification-service, accounting-service, kitchen-service, delivery-service, Kafka, and a React frontend."

Open the FTGO Docker Compose file to show the scale:

```bash
cat ../../iteration-7-docker/docker-compose.yml
```

> "Same patterns, bigger scale:
> - **Same multi-stage Dockerfiles** — every service uses the same build pattern
> - **Same Docker networking** — services reference each other by name (e.g., `http://restaurant-service:8081`)
> - **Same `depends_on`** — Kafka must be healthy before any service that uses it
> - **Same Spring profiles** — `SPRING_PROFILES_ACTIVE: docker` overrides URLs
>
> The only new thing is Kafka as infrastructure, but even that is just another container in the Compose file."

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

| Problem | Solution | Docker Concept |
|---------|----------|----------------|
| "Works on my machine" | Dockerfile | Containerization |
| Fat images (500MB+) | Multi-stage builds | Build optimization |
| Starting 2+ services manually | docker-compose.yml | Orchestration |
| Services can't find each other | Docker networking + service names | Service discovery (basic) |
| Service starts before dependency is ready | Health checks + `depends_on` condition | Startup ordering |

---

## Discussion Questions

Use these to drive conversation and bridge to upcoming topics:

1. **Resilience**: "What happens if greeting-service crashes while time-service is running? How would you handle that?" _(Links to iteration 3's Circuit Breaker patterns)_

2. **Independent Deployment**: "If you need to update only greeting-service, do you need to rebuild time-service?" _(No — just `docker compose up --build greeting-service`)_

3. **Scaling**: "What if you get 10x traffic to greeting-service? Can you run 3 copies?" _(Yes: `docker compose up --scale greeting-service=3` — but who load-balances?)_

4. **Registries**: "You built images locally. How do you share them with your team or deploy to production?" _(Docker Hub, AWS ECR, GCP Artifact Registry — `docker push`)_

5. **Production Readiness**: "Docker Compose is great for development. Would you use it in production?" _(Usually no — leads to Kubernetes, ECS, etc.)_

6. **Configuration**: "We used Spring profiles to switch between local and Docker URLs. What if you have 8 services, each with 10 config properties?" _(Leads to centralized configuration — iteration 10)_

---

## Quick Reference: Useful Commands

```bash
# Build and start everything
docker compose up --build

# Start in background (detached)
docker compose up --build -d

# View logs
docker compose logs -f
docker compose logs -f greeting-service    # just one service

# Check service status
docker compose ps

# Stop everything
docker compose down

# Rebuild just one service
docker compose up --build greeting-service

# Check image sizes
docker images | grep -E "greeting|time"

# Inspect Docker network
docker network ls
docker network inspect iteration-7-docker_default
```
