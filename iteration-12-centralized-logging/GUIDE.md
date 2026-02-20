# Iteration 12: Centralized Logging (Elasticsearch + Filebeat + Kibana)

## What Problem Does This Solve?

After iteration 11, we have distributed tracing with Zipkin — we can see the timing and flow of requests across services. But what about the actual **log messages**? When an order fails, we need to read the ERROR log. Currently, we must run `docker compose logs <service>` for each of 15 containers and manually search through them.

```
Without centralized logging:

$ docker compose logs order-service | grep ERROR
  "Order creation failed for consumer 1"

$ docker compose logs kitchen-service | grep ERROR
  (nothing? maybe it's in another service?)

$ docker compose logs accounting-service | grep ERROR
  "Payment declined for order 42"

Found it! But it took checking 5 services manually.
Now imagine doing this at 3 AM in production with 50 services.
```

**Problem:** Logs are scattered across 15 containers. Finding an error means running `docker compose logs <service>` for each one and grepping manually.

**Solution:** Add the ELK stack (Elasticsearch, Filebeat, Kibana) to collect all container logs into a single searchable UI. Filebeat reads Docker's log files and ships them to Elasticsearch. Kibana provides search and dashboards. **Zero Java code changes** — Filebeat collects logs externally.

## What Changed

| Component | Before (Iteration 11) | After (Iteration 12) |
|-----------|----------------------|----------------------|
| Log access | `docker compose logs <service>` per container | All logs searchable in Kibana UI |
| Log storage | Docker json-file driver (local only) | Elasticsearch (indexed, searchable) |
| Log search | Manual grep across containers | Full-text search in Kibana Discover |
| Cross-service debugging | Search each service's logs individually | Search by traceId to see all services at once |
| New infrastructure | — | Elasticsearch (port 9200), Kibana (port 5601), Filebeat |
| Java code changes | — | **None** — logs collected externally by Filebeat |
| Config changes | — | **None** — existing logging config unchanged |

## Architecture

```
Before (Iteration 11):                     After (Iteration 12):

Logs are isolated per container.            All logs flow to a central searchable index.
Must check each service individually.       Search across ALL services in one UI.

order-service logs -> stdout                order-service logs -> stdout -> Docker json-file ─┐
kitchen-service logs -> stdout              kitchen-service logs -> stdout -> Docker json-file ─┤
accounting-service logs -> stdout           accounting-service logs -> stdout -> Docker json-file ─┤
(etc...)                                    (etc...)                                               │
                                                                                                   ▼
No central view.                            Filebeat reads all Docker log files
Can't search across services.                     │
                                                  ▼
                                            Elasticsearch (stores & indexes)
                                                  │
                                                  ▼
                                            Kibana (search UI — port 5601)
```

```
docker-compose up (15 containers)
├── kafka (port 9092)
├── eureka-server (port 8761)
├── zipkin (port 9411)
├── elasticsearch (port 9200) <-- NEW — log storage & indexing
├── kibana (port 5601) <-- NEW — log search UI
├── filebeat <-- NEW — log collector (reads Docker json-file logs)
├── config-server (port 8888)
├── restaurant-service (port 8081) ─┐
├── accounting-service (port 8083)  │
├── kitchen-service (port 8084)     ├── stdout → Docker json-file → Filebeat → Elasticsearch
├── notification-service (port 8082)│
├── delivery-service (port 8085)    │
├── order-service (port 8080)       │
├── api-gateway (port 8090)        ─┘
└── ftgo-web (port 3000)
```

## Key Files

| File | Action | Purpose |
|------|--------|---------|
| `docker-compose.yml` | MODIFY | Add elasticsearch, kibana, filebeat containers |
| `filebeat.yml` | CREATE | Filebeat config — Docker log autodiscovery, ship to ES |
| `GUIDE.md` | CREATE | This documentation |

**Total: 1 modified file, 2 new files. Zero Java code changes.**

## How It Works

### Why Filebeat Instead of Logstash?

The "ELK stack" traditionally means Elasticsearch + Logstash + Kibana. We use Filebeat instead of Logstash because:

- **Logstash** is a heavy JVM process (500MB-1GB RAM). **Filebeat** is a lightweight Go binary (~50MB RAM).
- For simple log forwarding (Docker → Elasticsearch), Logstash is overkill.
- Filebeat is part of the Elastic ecosystem — still "ELK" conceptually (E + Beats + K).
- Our machine is already running 12 containers — the lighter option matters.

### How Filebeat Collects Logs

Docker's default logging driver (`json-file`) writes every container's stdout/stderr to JSON files at:
```
/var/lib/docker/containers/<container-id>/<container-id>-json.log
```

Filebeat is configured to:
1. **Read** all Docker container log files from `/var/lib/docker/containers/*/*.log`
2. **Enrich** each log line with Docker metadata (container name, service name, labels) via `add_docker_metadata` processor
3. **Ship** logs to Elasticsearch into daily indices (`ftgo-logs-YYYY.MM.DD`)

### Filebeat Configuration (`filebeat.yml`)

```yaml
filebeat.inputs:
  - type: container                          # Docker container log input
    paths:
      - /var/lib/docker/containers/*/*.log   # All container logs

processors:
  - add_docker_metadata:                     # Enrich with container labels
      host: "unix:///var/run/docker.sock"    # Read metadata from Docker API

output.elasticsearch:
  hosts: ["http://elasticsearch:9200"]
  indices:
    - index: "ftgo-logs-%{+yyyy.MM.dd}"     # Daily indices

setup.template:
  name: "ftgo-logs"
  pattern: "ftgo-logs-*"
setup.ilm.enabled: false                     # No index lifecycle management (simplicity)
```

Key points:
- `type: container` is purpose-built for Docker logs — it parses the JSON log format and extracts the actual message
- `add_docker_metadata` uses the Docker socket to look up container labels, including `com.docker.compose.service` (the service name from docker-compose)
- Daily indices keep logs organized and make cleanup easy (delete old indices)
- ILM disabled for training simplicity

### Docker Compose Services

**Elasticsearch:**
- `discovery.type=single-node` — no cluster, minimal overhead for development
- `xpack.security.enabled=false` — no authentication for training simplicity
- `ES_JAVA_OPTS=-Xms256m -Xmx256m` — constrained heap (default 1GB is too much alongside 12 other containers)
- Healthcheck ensures Kibana and Filebeat wait for ES to be ready

**Kibana:**
- Connects to Elasticsearch via `ELASTICSEARCH_HOSTS`
- `depends_on` with healthcheck ensures it starts after ES is ready

**Filebeat:**
- Runs as `root` to read Docker log files (owned by root on the host)
- Mounts `docker.sock` read-only for container metadata lookups
- Mounts `/var/lib/docker/containers` read-only for log file access
- `depends_on` elasticsearch healthcheck ensures it doesn't start shipping before ES is ready

### How traceId Connects Tracing and Logging

From iteration 11, every log line already includes `[service-name,traceId,spanId]`:
```
[order-service,abc123def456,span789] Creating order for consumer 1
[kitchen-service,abc123def456,span012] Ticket created for order 42
[notification-service,abc123def456,span345] Notification sent for order 42
```

With centralized logging, you can now:
1. Find an error in Zipkin (e.g., a failed span with traceId `abc123def456`)
2. Go to Kibana Discover
3. Search for `abc123def456`
4. See **all log lines from all services** for that request in one view

This is the power of combining distributed tracing (iteration 11) with centralized logging (iteration 12).

## Prerequisite

Elasticsearch 8.x requires a kernel parameter to be set:

```bash
# Check current value
sysctl vm.max_map_count

# If less than 262144, set it (required for Elasticsearch)
sudo sysctl -w vm.max_map_count=262144
```

## Running the System

### Start Everything

```bash
cd iteration-12-centralized-logging
docker compose up --build
```

Wait for all 15 containers to start. Elasticsearch and Kibana take 30-60 seconds to initialize.

### Check Elasticsearch is Running

```bash
curl http://localhost:9200/_cluster/health?pretty
```

You should see `"status" : "green"` (or `"yellow"` for a single-node cluster).

### Check Kibana UI

Open http://localhost:5601 — the Kibana UI should be running.

### Create an Order (Generate Logs Across Services)

```bash
curl -X POST http://localhost:8090/api/orders \
  -H "Content-Type: application/json" \
  -d '{"consumerId":1,"restaurantId":1,"lineItems":[{"menuItemId":"item-1","name":"Chicken Tikka Masala","quantity":2,"price":11.99}],"deliveryAddress":"123 Main St","paymentMethod":"CARD"}'
```

### Set Up Kibana Index Pattern

1. Open http://localhost:5601
2. Go to **Management** -> **Stack Management** -> **Data Views** (or Index Patterns)
3. Click **Create data view**
4. Enter index pattern: `ftgo-logs-*`
5. Select `@timestamp` as the time field
6. Click **Save**

### Search Logs in Kibana

1. Go to **Discover** tab (left sidebar)
2. Select the `ftgo-logs-*` data view
3. You should see logs from all containers flowing in
4. Try these searches:
   - Filter by service: `container.labels.com_docker_compose_service: order-service`
   - Search for errors: `message: ERROR`
   - Search by traceId: paste a traceId from Zipkin to see all log lines for that request

### Full E2E Flow via UI

Open http://localhost:3000 and follow the same flow:

1. **Consumer view** — Browse restaurants, view menu, place an order
2. **Restaurant view** — Accept ticket, mark as preparing, mark as ready
3. **Courier view** — Assign courier, pick up, deliver
4. **Kibana** — After each step, check Kibana Discover for logs from all services
5. **Zipkin** — Find a traceId, then search for it in Kibana to see all log lines

### Stop Everything

```bash
docker compose down
```

## Teaching Concepts

### Why Centralized Logging Matters

In a monolith, all logs go to one file. In microservices, each service writes to its own stdout, which Docker captures into separate files. As the number of services grows, manually checking each service's logs becomes impractical.

Centralized logging solves:
- **Discovery**: "Which service logged this error?" — search all services at once
- **Correlation**: "What else happened during this request?" — search by traceId
- **Timeline**: "What happened in the last 5 minutes?" — time-based filtering in Kibana
- **Persistence**: Docker logs can be lost when containers are recreated — Elasticsearch persists them

### The Log Pipeline

```
Application (Java) -> stdout -> Docker json-file driver -> /var/lib/docker/containers/...
    -> Filebeat (reads files, adds metadata) -> Elasticsearch (indexes) -> Kibana (searches)
```

Each step is decoupled:
- Applications don't know about Filebeat — they just log to stdout (12-factor app principle)
- Filebeat doesn't know about the application — it reads Docker's standard log format
- Elasticsearch doesn't know about Docker — it receives JSON documents
- Kibana provides the search UI over whatever is in Elasticsearch

### Zero Code Changes

This iteration requires **no Java code changes** because:
- Services already log to stdout (standard practice since iteration 1)
- Docker already captures stdout to json-file logs (Docker's default behavior)
- Filebeat reads these files externally — no agent inside the containers
- The `[service-name,traceId,spanId]` pattern from iteration 11 is preserved as-is — searchable via Kibana full-text search

This is a key benefit of the stdout logging approach: you can add centralized logging infrastructure without touching any application code.

### Elasticsearch Resource Considerations

Elasticsearch is a JVM application that defaults to 1GB heap. In our constrained environment:
- We limit it to 256MB (`-Xms256m -Xmx256m`)
- Single-node mode (`discovery.type=single-node`) avoids cluster overhead
- Security disabled (`xpack.security.enabled=false`) reduces memory usage
- In production, you'd run Elasticsearch on dedicated machines with much more RAM

### Index Pattern: Daily Indices

`ftgo-logs-YYYY.MM.DD` creates a new index each day. Benefits:
- **Easy cleanup**: Delete old indices to free space (`DELETE /ftgo-logs-2024.01.01`)
- **Performance**: Smaller indices are faster to search
- **Common pattern**: This is the standard approach for time-series log data
