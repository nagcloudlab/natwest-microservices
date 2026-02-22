# Iteration 10 Simple Demo: Centralized Configuration with Spring Cloud Config Server

> **Goal**: Teach centralized configuration — replace scattered, duplicated config files across services with one central configuration source. Trainees see the eureka URL duplicated 6 times across 3 services, then see it collapse to 1 shared file.
>
> **Duration**: ~25 minutes
>
> **Pre-requisites**: Docker Desktop installed, iteration-9 Service Discovery demo completed

---

## The Services

| Service | Port | Purpose |
|---------|------|---------|
| **eureka-server** | 8761 | Service registry (unchanged from iteration 9) |
| **config-server** | 8888 | **NEW** — Serves configuration to all client services |
| **greeting-service** | 9001 | Returns a greeting message + hostname |
| **time-service** | 9002 | Returns current time; calls greeting-service via Eureka |
| **api-gateway** | 9000 | Routes requests using `lb://` (Eureka-resolved) |

### What Changed from Iteration 9

| What | Iteration 9 (Scattered) | Iteration 10 (Centralized) |
|------|--------------------------|---------------------------|
| Eureka URL (localhost) | Repeated in 3 services' `application.properties` | 1 shared `config-repo/application.properties` |
| Eureka URL (Docker) | Repeated in 3 services' `application-docker.properties` | 1 shared `config-repo/application-docker.properties` |
| Gateway routes + CORS | In api-gateway's `application.properties` (13 lines) | In `config-repo/api-gateway.properties` |
| Service ports | In each service's `application.properties` | In `config-repo/{service}.properties` |
| Each service's local config | 3–13 lines per file | 2 lines: name + config import |
| Docker override per service | 1 line (eureka URL) | 1 line (config import URL) |
| **Total config lines across services** | **~30 lines across 6 files** | **6 lines across 6 files** |
| **Config duplication** | **6 eureka URL copies** | **0 duplication** |

---

## Opening (2 min)

**Story to tell:**

> "In iteration 9, we centralized service discovery with Eureka. But look at our config files — the eureka URL `http://localhost:8761/eureka/` appears in **3 separate files** (`greeting-service`, `time-service`, `api-gateway`). The Docker version appears in 3 more. That's **6 copies** of the same URL."
>
> "What if the Eureka server moves to a different port? You'd update **6 files across 3 services**. And the gateway has 13 lines of route config that no other service cares about. Every service carries its own config — scattered, duplicated, hard to manage."
>
> "What if there was **one place** for all shared config? That's Spring Cloud Config Server."

---

## Act 1: The Config Server — Browse Config as JSON (5 min)

### Step 1 — Start everything

```bash
cd simple-demos/iteration-10-centralized-config
docker compose up --build
```

Wait for all five services to start. Startup order: eureka → config-server → greeting + time → api-gateway.

### Step 2 — Browse the Config Server API

Open your browser or use curl:

```bash
curl localhost:8888/greeting-service/default | jq
```

> **Key moment**: "This is the Config Server's REST API. It returns the merged configuration for `greeting-service` in the `default` profile. You can see the port, eureka URL — everything the service needs."

```bash
curl localhost:8888/api-gateway/default | jq
```

> "Now look at api-gateway's config — it has the routes, CORS settings, eureka URL, port — all served from the Config Server."

```bash
curl localhost:8888/api-gateway/docker | jq
```

> "And with the `docker` profile — notice the eureka URL changed to `http://eureka-server:8761/eureka/`. The Config Server merges the profile-specific config on top of the defaults."

### How It Works

Show `config-server/src/main/java/com/demo/config/ConfigServerApplication.java`:

```java
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication { ... }
```

Show `config-server/src/main/resources/application.properties`:

```properties
server.port=8888
spring.application.name=config-server

# Serve config from classpath (no Git repo needed)
spring.profiles.active=native
spring.cloud.config.server.native.search-locations=classpath:/config-repo

# Eureka — register so services can discover config-server
eureka.client.service-url.defaultZone=http://localhost:8761/eureka/
```

> "One annotation (`@EnableConfigServer`), a port, and a pointer to the config files. The `native` profile means 'read from the filesystem/classpath' instead of Git. In production, you'd use a Git repo for versioning and audit trails."

---

## Act 2: The Config Repo — Naming Convention (5 min)

### Step 3 — Show the config repository

Browse `config-server/src/main/resources/config-repo/`:

```
config-repo/
├── application.properties           ← shared by ALL services
├── application-docker.properties    ← shared Docker overrides
├── greeting-service.properties      ← only for greeting-service
├── time-service.properties          ← only for time-service
└── api-gateway.properties           ← only for api-gateway
```

### The Naming Convention

> "Config Server uses a **naming convention** to match files to services. The file name matches `spring.application.name`."

```
config-repo/{service-name}.properties             ← service-specific
config-repo/{service-name}-{profile}.properties    ← service + profile
config-repo/application.properties                 ← shared defaults
config-repo/application-{profile}.properties       ← shared profile override
```

### Resolution Priority (highest → lowest)

```
1. api-gateway-docker.properties     ← service + profile (highest priority)
2. api-gateway.properties            ← service-specific
3. application-docker.properties     ← shared profile override
4. application.properties            ← shared defaults (lowest priority)
```

### Step 4 — Show shared config

Show `config-repo/application.properties`:

```properties
# Eureka — one place for the registry URL (instead of 3 copies)
eureka.client.service-url.defaultZone=http://localhost:8761/eureka/
```

Show `config-repo/application-docker.properties`:

```properties
eureka.client.service-url.defaultZone=http://eureka-server:8761/eureka/
```

> "**This is the payoff.** The eureka URL used to be in 6 files. Now it's in 2 (one per profile). Change the Eureka port? Update **one file** instead of six."

### Step 5 — Show service-specific config

Show `config-repo/greeting-service.properties`:

```properties
server.port=9001
```

Show `config-repo/api-gateway.properties`:

```properties
server.port=9000
# ... plus all route definitions, CORS, actuator config
```

> "Service-specific config goes in its own file. The greeting-service just needs a port. The api-gateway needs routes and CORS config. Each service gets exactly the config it needs — no more, no less."

---

## Act 3: Client Setup — One Dependency, One Property (5 min)

### Step 6 — Show what changed in each client service

Open `greeting-service/pom.xml` — highlight the **one new dependency**:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-config</artifactId>
</dependency>
```

Open `greeting-service/src/main/resources/application.properties`:

```properties
spring.application.name=greeting-service
spring.config.import=optional:configserver:http://localhost:8888
```

> **Explain:**
> - **`spring.application.name`** — tells the Config Server which config files to serve (matches `greeting-service.properties`)
> - **`spring.config.import`** — tells Spring Boot "before you start, fetch config from this Config Server"
> - **`optional:`** — if the Config Server is down, the service still starts (graceful degradation)
>
> "That's it. Two lines. Compare this to iteration 9 where this file had the port, eureka URL, and comments — now all of that lives in the Config Server."

### Docker Profile

Show `greeting-service/src/main/resources/application-docker.properties`:

```properties
spring.config.import=optional:configserver:http://config-server:8888
```

> "In Docker, the only override is the Config Server hostname. The eureka URL override? That's in the Config Server's shared `application-docker.properties`. This service doesn't need it locally anymore."

### The Pattern is Identical

Show that `time-service` and `api-gateway` have the **exact same pattern**:

```properties
# time-service/application.properties
spring.application.name=time-service
spring.config.import=optional:configserver:http://localhost:8888

# api-gateway/application.properties
spring.application.name=api-gateway
spring.config.import=optional:configserver:http://localhost:8888
```

> "Every client service looks the same — two lines. The Config Server knows what to serve based on the name."

---

## Act 4: Shared Config — Prove It Works (5 min)

### Step 7 — Verify the Eureka dashboard

Open: **http://localhost:8761**

> "Four services registered: greeting-service, time-service, api-gateway, and config-server. The config-server also registers with Eureka — it's a service like any other."

### Step 8 — Test the services

```bash
# Direct access
curl localhost:9001/greeting | jq
curl localhost:9002/time | jq
curl localhost:9002/time/with-greeting | jq

# Via gateway
curl localhost:9000/greeting | jq
curl localhost:9000/time | jq
curl localhost:9000/time/with-greeting | jq

# Path rewrite still works
curl localhost:9000/hello | jq
```

> "Everything works exactly the same as iteration 9. The services don't know or care that their config comes from a Config Server instead of local files. The behavior is identical — only where the config lives has changed."

### Step 9 — Prove config comes from the server

```bash
# Ask the Config Server what it serves to greeting-service
curl localhost:8888/greeting-service/default | jq '.propertySources[].source'
```

> "You can see the merged config — `greeting-service.properties` for the port, `application.properties` for the eureka URL. The Config Server merged them together."

---

## Act 5: What We Centralized — Before/After (3 min)

### Config File Comparison

**Iteration 9** — `greeting-service/application.properties` (3 lines):
```properties
server.port=9001
spring.application.name=greeting-service
eureka.client.service-url.defaultZone=http://localhost:8761/eureka/
```

**Iteration 10** — `greeting-service/application.properties` (2 lines):
```properties
spring.application.name=greeting-service
spring.config.import=optional:configserver:http://localhost:8888
```

**Iteration 9** — `api-gateway/application.properties` (13 lines):
```properties
server.port=9000
spring.application.name=api-gateway
eureka.client.service-url.defaultZone=http://localhost:8761/eureka/
spring.cloud.gateway.routes[0]...  # 8 lines of routes
spring.cloud.gateway.globalcors...  # 4 lines of CORS
management.endpoints...             # 1 line actuator
```

**Iteration 10** — `api-gateway/application.properties` (2 lines):
```properties
spring.application.name=api-gateway
spring.config.import=optional:configserver:http://localhost:8888
```

> "13 lines → 2 lines. All the route config, CORS config, eureka URL — all moved to the Config Server. The service itself just says 'my name is api-gateway, get my config from here.'"

### Duplication Eliminated

| Config | Iteration 9 (copies) | Iteration 10 (copies) |
|--------|----------------------|-----------------------|
| `eureka.client.service-url.defaultZone` (localhost) | 3 | 1 |
| `eureka.client.service-url.defaultZone` (docker) | 3 | 1 |
| **Total duplicated config** | **6 copies** | **0 copies** |

---

## Bridge to FTGO (2 min)

> "We centralized config for 3 services. The FTGO system has 8 services — but uses the exact same patterns."

| Pattern | Simple Demo | FTGO |
|---------|-------------|------|
| Config Server setup | 1 `@EnableConfigServer` | Same |
| `spring-cloud-starter-config` | 3 services | 8 services |
| `spring.config.import` | Same 2-line pattern | Same 2-line pattern |
| Shared eureka URL | 1 `application.properties` | Same |
| Service-specific config | port only | port + H2 + Kafka + JPA |
| Config repo files | 5 files | 10+ files |

> "The FTGO config repo also centralizes Kafka settings, H2 database URLs, JPA config — all shared across services. Same pattern, more config."

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
| Config duplicated across services | Config Server serves shared config | Centralized Configuration |
| Eureka URL in 6 files | One `application.properties` in config-repo | Shared Defaults |
| Gateway routes embedded in service | `api-gateway.properties` in config-repo | Service-Specific Config |
| Docker overrides scattered | One `application-docker.properties` in config-repo | Profile-Based Overrides |
| Adding a service = copy config | Just set `spring.application.name` + config import | Convention Over Configuration |

---

## Discussion Questions

1. **Chicken-and-Egg**: "Config Server depends on Eureka. But if all config is in Config Server, how does Eureka get its config?" _(Eureka doesn't use Config Server — it manages its own config. Config Server depends on Eureka, not the other way around. This is a deliberate architectural choice.)_

2. **Single Point of Failure**: "What if Config Server crashes?" _(Services only fetch config at startup. If Config Server is down, running services keep working with their cached config. New service starts will fail unless you use `optional:` prefix.)_

3. **Git Backend**: "We're using `native` profile (classpath). What's the alternative?" _(Git backend — Config Server pulls from a Git repo. You get versioning, audit trail, pull request reviews for config changes. That's the production setup.)_

4. **Security**: "Should config be encrypted?" _(Yes — database passwords, API keys should be encrypted in the config repo. Spring Cloud Config supports encryption/decryption with symmetric or asymmetric keys.)_

5. **Config Refresh**: "What if you change config without restarting?" _(Spring Cloud Config supports `/actuator/refresh` to reload config without restart. Combined with Spring Cloud Bus, you can refresh all services at once.)_

---

## Quick Reference: Useful Commands

```bash
# Build and start everything (5 services)
docker compose up --build

# Start in background
docker compose up --build -d

# View Eureka dashboard (4 services registered)
open http://localhost:8761

# Browse config served by Config Server
curl localhost:8888/greeting-service/default | jq
curl localhost:8888/time-service/default | jq
curl localhost:8888/api-gateway/default | jq
curl localhost:8888/api-gateway/docker | jq

# Test direct access
curl localhost:9001/greeting | jq
curl localhost:9002/time | jq
curl localhost:9002/time/with-greeting | jq

# Test via gateway
curl localhost:9000/greeting | jq
curl localhost:9000/time | jq
curl localhost:9000/time/with-greeting | jq
curl localhost:9000/hello | jq

# View gateway logs
docker compose logs -f api-gateway

# Stop everything
docker compose down
```
