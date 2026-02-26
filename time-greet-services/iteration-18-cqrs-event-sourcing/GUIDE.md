# Iteration 18 Simple Demo: CQRS / Event Sourcing

> **Goal**: Separate read and write models using Event Sourcing and CQRS — store what happened, build multiple query views from the same event stream, and prove it works by rebuilding views from scratch.
>
> **Duration**: ~35 minutes
>
> **Pre-requisites**: Docker Desktop installed, completed iteration 11 (Kafka basics)

---

## The Services

| Service | Port | Purpose |
|---------|------|---------|
| **command-service** | 9001 | Write side — accepts greetings, appends events, publishes to Kafka |
| **query-service** | 9002 | Read side — consumes events, builds 3 materialized views |
| **Kafka** | 9092 | Event backbone — `greeting-events` topic |
| **Kafka UI** | 8080 | Visual topic/message browser |

### Architecture

```
POST /greetings           greeting-events           GET /greetings
      |                    (Kafka topic)             GET /greetings/stats
      v                                              GET /greetings/by-name/{name}
+----------------+                              +------------------+
| command-service| -----> Kafka -------->        | query-service    |
|   (port 9001)  |                              |   (port 9002)    |
+----------------+                              +------------------+
| EventStore     |                              | ReadModelStore   |
| (append-only)  |                              |  - timeline      |
| EventPublisher |                              |  - stats         |
+----------------+                              |  - by-name index |
                                                +------------------+
```

### Key Endpoints

| Endpoint | Service | Purpose |
|----------|---------|---------|
| `POST /greetings` | command (9001) | Create greeting → event stored + published |
| `GET /greetings` | command (9001) | Raw event log (source of truth) |
| `GET /greetings` | query (9002) | Timeline view (materialized read model) |
| `GET /greetings/stats` | query (9002) | Aggregated statistics |
| `GET /greetings/by-name/{name}` | query (9002) | Filtered by sender |
| `POST /greetings/rebuild` | query (9002) | Clear + replay all events from Kafka |
| Kafka UI | :8080 | Browse topics and messages visually |

---

## Opening (2 min)

**Story to tell:**

> "Imagine you need different query shapes from the same data. Order history for the customer, restaurant analytics for the owner, delivery metrics for the driver. Each needs a different data shape, different aggregations, different indexes."
>
> "Traditional approach: one database, many complex queries fighting each other. Or — store **what happened** as an immutable log of events, and build separate, optimized views from that log. If a view gets corrupted or you need a new one? Replay the events and rebuild it."
>
> "That's Event Sourcing + CQRS. Today we'll build it."

---

## Act 1: The Write Side (7 min)

### Step 1 — Start all services

```bash
cd time-greet-services/iteration-18-cqrs-event-sourcing
docker compose up --build
```

Wait for all four containers to start (Kafka → Kafka UI → command-service → query-service).

### Step 2 — Create some greetings

```bash
curl -X POST localhost:9001/greetings \
  -H "Content-Type: application/json" \
  -d '{"name":"Alice","message":"Hello everyone!"}' | jq
```

```json
{
  "eventId": "a1b2c3d4-...",
  "eventType": "GreetingCreated",
  "sequence": 1,
  "timestamp": "2025-01-15T14:30:00.000Z",
  "name": "Alice",
  "message": "Hello everyone!"
}
```

```bash
curl -X POST localhost:9001/greetings \
  -H "Content-Type: application/json" \
  -d '{"name":"Bob","message":"Hi there!"}' | jq

curl -X POST localhost:9001/greetings \
  -H "Content-Type: application/json" \
  -d '{"name":"Alice","message":"Good morning!"}' | jq
```

> **Point out**: "Each POST doesn't update a row — it **appends** a new event. We now have 3 events in the log. Nothing was overwritten, nothing was lost."

### Step 3 — View the raw event log

```bash
curl localhost:9001/greetings | jq
```

```json
[
  {
    "eventId": "...",
    "eventType": "GreetingCreated",
    "sequence": 1,
    "timestamp": "...",
    "name": "Alice",
    "message": "Hello everyone!"
  },
  {
    "eventId": "...",
    "eventType": "GreetingCreated",
    "sequence": 2,
    "timestamp": "...",
    "name": "Bob",
    "message": "Hi there!"
  },
  {
    "eventId": "...",
    "eventType": "GreetingCreated",
    "sequence": 3,
    "timestamp": "...",
    "name": "Alice",
    "message": "Good morning!"
  }
]
```

> **Point out**: "This is the **Event Store** — an append-only, immutable log. This is the source of truth. Every event has a unique ID, a sequence number, and a timestamp. In a real system, this would be persisted in Kafka or an event store database."

### Step 4 — See events in Kafka UI

Open **http://localhost:8080** in your browser. Navigate to the `greeting-events` topic.

> **Point out**: "Same 3 events, sitting in Kafka. The command-service appended to its local store AND published to Kafka. The query-service is consuming from this topic."

---

## Act 2: Three Views from One Stream (7 min)

### Step 5 — Timeline view

```bash
curl localhost:9002/greetings | jq
```

> "Same events, but this is the **query-service** on port 9002. It consumed events from Kafka and built a timeline view."

### Step 6 — Statistics view

```bash
curl localhost:9002/greetings/stats | jq
```

```json
{
  "totalGreetings": 3,
  "uniqueNames": 2,
  "names": ["Alice", "Bob"],
  "firstGreeting": "2025-01-15T14:30:00.000Z",
  "latestGreeting": "2025-01-15T14:30:10.000Z"
}
```

> **Key moment**: "Same 3 events, completely different shape. Aggregated counts, unique names, timestamps. This view was built by the **same `apply()` function** that built the timeline — one event projected into multiple views simultaneously."

### Step 7 — By-name view

```bash
curl localhost:9002/greetings/by-name/Alice | jq
```

```json
{
  "name": "Alice",
  "count": 2,
  "greetings": [
    {
      "eventId": "...",
      "sequence": 1,
      "name": "Alice",
      "message": "Hello everyone!"
    },
    {
      "eventId": "...",
      "sequence": 3,
      "name": "Alice",
      "message": "Good morning!"
    }
  ]
}
```

```bash
curl localhost:9002/greetings/by-name/Bob | jq
```

> **Point out**: "Three views — timeline, stats, by-name index — all built from the same event stream. Each is optimized for a different query pattern. That's the **C** in CQRS: Command (write) and Query (read) use separate models."

---

## Act 3: The Rebuild — Key Demo (8 min)

> "Here's the moment that makes Event Sourcing click."

### Step 8 — Destroy and rebuild

```bash
# First, verify stats are correct
curl localhost:9002/greetings/stats | jq

# Now rebuild — clears ALL read models and replays from Kafka
curl -X POST localhost:9002/greetings/rebuild | jq
```

```json
{
  "status": "rebuilt",
  "eventsReplayed": 3
}
```

### Step 9 — Verify everything is restored

```bash
# Timeline — still 3 greetings
curl localhost:9002/greetings | jq

# Stats — still correct
curl localhost:9002/greetings/stats | jq

# By-name — still works
curl localhost:9002/greetings/by-name/Alice | jq
```

> **Key moment**: "We just **destroyed** all three read models and rebuilt them perfectly from the Kafka event log. The read models are disposable — they're derived data. The event log is the source of truth."
>
> "This is the superpower of Event Sourcing. Need a new view? Write a new `apply()` function and replay. View got corrupted? Rebuild it. Need to fix a bug in the projection logic? Fix and replay."

### Step 10 — Add more events after rebuild

```bash
curl -X POST localhost:9001/greetings \
  -H "Content-Type: application/json" \
  -d '{"name":"Charlie","message":"Just joined!"}' | jq

# Stats now show 4 greetings, 3 unique names
curl localhost:9002/greetings/stats | jq
```

> **Point out**: "After the rebuild, the live consumer picks right back up. New events flow into the existing views. The system is self-healing."

---

## Act 4: Why CQRS? (5 min)

### Traditional vs CQRS

| Aspect | Traditional (single model) | CQRS + Event Sourcing |
|--------|---------------------------|----------------------|
| **Data model** | One shared model for reads + writes | Separate write (events) and read (views) models |
| **Writes** | UPDATE row (previous state lost) | APPEND event (full history preserved) |
| **Queries** | Complex JOINs, indexes fight each other | Purpose-built views, each optimized for its query |
| **Schema changes** | Migrate database, risk downtime | Add new view, replay events, no downtime |
| **Audit trail** | Add audit table, hope it stays in sync | Built-in — the event log IS the audit trail |
| **Debugging** | "What was the state at 3pm yesterday?" Hard. | Replay events up to 3pm. Done. |

### When to Use CQRS / Event Sourcing

**Good fit:**
- Systems with complex reads (dashboards, analytics, search)
- Audit requirements (finance, healthcare, compliance)
- Event-driven architectures (already have events flowing)
- Need to replay/rebuild/reprocess historical data

**Skip it when:**
- Simple CRUD with straightforward queries
- Strong consistency required between write and read
- Small team, simple domain — overhead not justified

---

## Act 5: Code Walkthrough (8 min)

### The Write Path

**`EventStore.java`** — Append-only in-memory store:

```java
@Component
public class EventStore {
    private final List<Map<String, Object>> events = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong(0);

    public Map<String, Object> append(String name, String message) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventType", "GreetingCreated");
        event.put("sequence", sequence.incrementAndGet());
        event.put("timestamp", Instant.now().toString());
        event.put("name", name);
        event.put("message", message);
        events.add(event);
        return event;
    }
}
```

> **Point out**: "No UPDATE, no DELETE. Only append. Each event gets a unique ID and a monotonically increasing sequence number. The event captures **what happened**, not the current state."

**`EventPublisher.java`** — Publishes to Kafka:

```java
public void publish(String key, Map<String, Object> event) {
    String payload = objectMapper.writeValueAsString(event);
    kafkaTemplate.send(TOPIC, key, payload);
}
```

> **Point out**: "The key is the sender's name. Kafka guarantees ordering within a partition, so all events for the same name are ordered. Same pattern we used in iteration 11."

**`CommandController.java`** — Two steps per command:

```java
@PostMapping
public Map<String, Object> createGreeting(@RequestBody Map<String, String> request) {
    // 1. Append to event store (source of truth)
    Map<String, Object> event = eventStore.append(name, message);
    // 2. Publish to Kafka (for downstream consumers)
    eventPublisher.publish(name, event);
    return event;
}
```

### The Read Path

**`ReadModelStore.java`** — One `apply()` function, three views:

```java
public void apply(Map<String, Object> event) {
    // Project into timeline view
    timeline.add(event);

    // Project into stats view
    totalCount.incrementAndGet();
    uniqueNames.add(name);

    // Project into by-name index
    byName.computeIfAbsent(name, k -> new CopyOnWriteArrayList<>()).add(event);
}
```

> **Key insight**: "This single function is the entire projection logic. One event → three views updated. If you need a fourth view (say, greetings-per-hour), you add one more line here and replay."

**`EventConsumer.java`** — Live consumption + rebuild:

```java
// Live: Kafka listener applies events as they arrive
@KafkaListener(topics = "greeting-events", groupId = "query-service")
public void onEvent(String payload) {
    Map<String, Object> event = objectMapper.readValue(payload, ...);
    readModelStore.apply(event);
}

// Rebuild: Create temporary consumer, seek to beginning, replay all
public int rebuild() {
    readModelStore.clear();
    // Create temp consumer with unique group ID
    // seekToBeginning(all partitions)
    // Replay every event through apply()
    return eventsReplayed;
}
```

> **Explain**: "The live `@KafkaListener` keeps views up-to-date in real time. The `rebuild()` method creates a **throwaway** consumer with a unique group ID, seeks to offset 0, and replays every event through the same `apply()` function. Same code path, same result."

---

## Summary

| Concept | What It Means | In This Demo |
|---------|--------------|--------------|
| **Event Sourcing** | Store events, not state. Append-only. | `EventStore` — immutable log of GreetingCreated events |
| **CQRS** | Separate models for writes and reads | command-service (write) / query-service (read) |
| **Materialized Views** | Pre-computed query-optimized projections | timeline, stats, by-name — all from same events |
| **Event Replay** | Rebuild any view from the event log | `POST /greetings/rebuild` → seek to 0, replay all |

---

## Bridge to FTGO

> "In the FTGO application, consider what Order events look like: OrderCreated, OrderApproved, OrderPickedUp, OrderDelivered. Now imagine building separate read models from that stream:"
>
> - **Order History Service** — timeline of all orders for a customer
> - **Restaurant Analytics** — order counts, revenue, popular items per restaurant
> - **Delivery Tracking** — driver assignments, ETAs, completion rates
>
> "Each is a different projection of the same event stream. If the restaurant analytics view gets a bug? Fix the projection, replay the events, done. Need a new 'monthly revenue report' view? Write a new projection, replay from the beginning."
>
> "This is how systems like Amazon, Uber, and banking platforms handle the gap between 'what happened' and 'what do I need to see right now.'"

---

## Cleanup

```bash
docker compose down
```

---

## Discussion Questions

1. **Eventual consistency**: "After posting a greeting, is it immediately visible on the query side?" _(Not guaranteed — there's a small delay as the event flows through Kafka. This is eventual consistency. For most read models, this is fine.)_

2. **Schema evolution**: "What if you add a 'language' field to greetings? How do old events get handled?" _(Old events won't have the field. Your `apply()` function must handle missing fields gracefully — this is schema evolution.)_

3. **Ordering guarantees**: "Are events always processed in order?" _(Within a Kafka partition, yes. We use the sender's name as the key, so all events for the same person are ordered. Across partitions, no global ordering.)_

4. **Storage growth**: "Events are never deleted. What happens when you have billions?" _(Kafka supports log compaction and retention policies. For long-term, you'd snapshot the event store periodically and archive older events.)_

5. **Rebuilding at scale**: "Rebuilding 3 events takes milliseconds. What about rebuilding from 10 million events?" _(It takes longer — minutes or hours. Strategies: parallel replay, snapshots at checkpoints, rebuild in a new consumer group while the old one keeps serving.)_
