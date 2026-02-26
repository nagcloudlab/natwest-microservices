# Iteration 17 Simple Demo: GraphQL Gateway

> **Goal**: Show how GraphQL solves the over-fetching and multi-request problems of REST by aggregating multiple services through a single query endpoint.
>
> **Duration**: ~35 minutes
>
> **Pre-requisites**: Docker Desktop installed, completed iteration 7 (Docker basics)

---

## The Services

| Service | Port | Purpose |
|---------|------|---------|
| **greeting-service** | 9001 | Returns greeting message + hostname (unchanged from iter-7) |
| **time-service** | 9002 | Returns current time + hostname (unchanged from iter-7) |
| **graphql-gateway** | 9000 | Aggregates both services via GraphQL + REST comparison |

### Key Endpoints

| Endpoint | Purpose |
|----------|---------|
| `http://localhost:9000/graphiql` | Interactive GraphQL IDE in browser |
| `POST /graphql` (9000) | GraphQL query endpoint |
| `GET /api/combined` (9000) | REST comparison — same aggregation |
| `GET /greeting` (9001) | Greeting service (direct) |
| `GET /time` (9002) | Time service (direct) |

---

## Opening (2 min)

**Story to tell:**

> "You're building a mobile app for the FTGO food delivery platform. The order details screen needs to show: order status, restaurant name, and delivery ETA. That's **3 microservices, 3 REST calls, 3 round trips** from the mobile device."
>
> "And here's the frustrating part — the Order Service returns 15 fields, but you only need 2. The Restaurant Service returns the full menu, but you only need the name. You're **over-fetching** — downloading data you'll throw away. On a mobile connection, that matters."
>
> "What if the mobile app could say: 'Give me exactly these fields, from these services, in one request'? That's GraphQL."

---

## Act 1: The Problem — Multiple REST Calls (5 min)

### Step 1 — Start all services

```bash
cd time-greet-services/iteration-17-graphql-gateway
docker compose up --build
```

Wait for all three services to start.

### Step 2 — The current approach: multiple REST calls

To get both greeting and time data, a client currently needs **two separate calls**:

```bash
# Call 1: Get greeting (all fields)
curl localhost:9001/greeting | jq
```

```json
{
  "message": "Hello from Greeting Service!",
  "host": "a1b2c3d4e5f6"
}
```

```bash
# Call 2: Get time (all fields)
curl localhost:9002/time | jq
```

```json
{
  "currentTime": "2025-01-15 14:30:22",
  "host": "f7e8d9c0b1a2"
}
```

> **Point out**: "Two round trips. And we get ALL fields whether we need them or not. With 2 services this is fine. With 8 services and a mobile client on 3G? Not great."

---

## Act 2: GraphiQL — The Key Demo (8 min)

### Step 3 — Open GraphiQL

Open your browser to: **http://localhost:9000/graphiql**

> "This is GraphiQL (with an 'i') — an interactive IDE that comes built into Spring Boot's GraphQL support. No extra setup needed."

### Step 4 — Query just what you need

Type this query in GraphiQL:

```graphql
{
  greeting {
    message
  }
}
```

> **Key moment**: "Look at the response — we got ONLY the `message` field. No `host`. The client asked for exactly what it needed, nothing more. This is the opposite of over-fetching."

### Step 5 — Ask for more fields

```graphql
{
  greeting {
    message
    host
  }
}
```

> "Now we get both fields. The client controls the shape of the response. The server doesn't need to change."

### Step 6 — Query multiple services in one request

```graphql
{
  greeting {
    message
  }
  time {
    currentTime
  }
}
```

> **Key moment**: "ONE request. Data from TWO services. The client didn't need to know about service URLs, ports, or how many backend services exist. The gateway handled it all."

---

## Act 3: Combined Query with Timing (5 min)

### Step 7 — The combined query

```graphql
{
  combined {
    greeting {
      message
      host
    }
    time {
      currentTime
      host
    }
    fetchedIn {
      greetingMs
      timeMs
      totalMs
    }
  }
}
```

Expected response:
```json
{
  "data": {
    "combined": {
      "greeting": {
        "message": "Hello from Greeting Service!",
        "host": "a1b2c3d4e5f6"
      },
      "time": {
        "currentTime": "2025-01-15 14:30:35",
        "host": "f7e8d9c0b1a2"
      },
      "fetchedIn": {
        "greetingMs": 12,
        "timeMs": 8,
        "totalMs": 22
      }
    }
  }
}
```

> **Point out**: "One request, all data, with timing. The `fetchedIn` shows how long each backend call took. The client made ONE round trip to the gateway."

### Step 8 — Partial combined query

```graphql
{
  combined {
    greeting {
      message
    }
    fetchedIn {
      totalMs
    }
  }
}
```

> "Even from the combined query, we can pick just the fields we care about. No over-fetching."

---

## Act 4: REST vs GraphQL Comparison (5 min)

### Step 9 — REST comparison endpoint

```bash
curl localhost:9000/api/combined | jq
```

```json
{
  "greeting": {
    "message": "Hello from Greeting Service!",
    "host": "a1b2c3d4e5f6"
  },
  "time": {
    "currentTime": "2025-01-15 14:30:40",
    "host": "f7e8d9c0b1a2"
  },
  "fetchedIn": {
    "greetingMs": 15,
    "timeMs": 10,
    "totalMs": 26
  }
}
```

> **Point out**: "Same aggregation, same result. But with REST, you always get ALL fields. With GraphQL, you can ask for just `{ combined { greeting { message } } }` and save bandwidth."

### Comparison

| Aspect | Multiple REST calls | REST gateway | GraphQL gateway |
|--------|-------------------|--------------|-----------------|
| **Round trips** | 2 (one per service) | 1 | 1 |
| **Over-fetching** | Yes (all fields always) | Yes (all fields always) | No (client selects fields) |
| **Client complexity** | High (knows all URLs) | Low (one URL) | Low (one URL) |
| **Schema/docs** | External (OpenAPI) | External | Built-in (schema + GraphiQL) |

---

## Act 5: Code Walkthrough (10 min)

### The Schema — `schema.graphqls`

```graphql
type Query {
    greeting: Greeting
    time: Time
    combined: Combined
}

type Greeting { message: String!, host: String! }
type Time { currentTime: String!, host: String! }
type Combined { greeting: Greeting!, time: Time!, fetchedIn: FetchTiming! }
type FetchTiming { greetingMs: Int!, timeMs: Int!, totalMs: Int! }
```

> **Explain**: "This is the API contract. Schema-first design — you define the types and queries here, then implement resolvers in Java. Clients can explore this schema through GraphiQL."

### The Controller — `QueryController.java`

```java
@Controller
public class QueryController {

    record Greeting(String message, String host) {}
    record Time(String currentTime, String host) {}
    record FetchTiming(int greetingMs, int timeMs, int totalMs) {}
    record Combined(Greeting greeting, Time time, FetchTiming fetchedIn) {}

    @QueryMapping
    public Greeting greeting() { ... }

    @QueryMapping
    public Time time() { ... }

    @QueryMapping
    public Combined combined() { ... }
}
```

> **Point out**:
> - `@QueryMapping` — maps to the `Query` type in the schema (like `@GetMapping` for REST)
> - Java records — perfect for GraphQL types (immutable, concise, field names match schema)
> - Each method fetches from backend services and returns the record

### The Clients — `GreetingClient.java` / `TimeClient.java`

```java
@Component
public class GreetingClient {
    public Map<String, Object> getGreeting() {
        long start = System.nanoTime();
        Map<String, Object> result = restTemplate.getForObject(
                greetingServiceUrl + "/greeting", Map.class);
        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        return Map.of("data", result, "latencyMs", latencyMs);
    }
}
```

> **Explain**: "The gateway still uses REST to talk to backend services. GraphQL is the client-facing protocol. Backend services don't need to change at all — that's a major advantage."

---

## Summary

| Aspect | Multiple REST Calls | GraphQL Gateway |
|--------|-------------------|-----------------|
| **Round trips** from client | N (one per service) | 1 (always) |
| **Over-fetching** | Always all fields | Client selects exact fields |
| **Schema & docs** | Manual (OpenAPI/Swagger) | Built-in (introspection + GraphiQL) |
| **Tooling** | curl, Postman | GraphiQL IDE built-in |
| **Caching** | Easy (HTTP caching) | Harder (POST requests) |
| **File uploads** | Native | Requires workaround |
| **Best for** | Simple CRUD, caching-heavy | Mobile clients, aggregation, complex queries |

---

## Bridge to FTGO

> "Imagine the FTGO application with a GraphQL gateway. Instead of the mobile app making separate calls to Order Service, Restaurant Service, and Delivery Service, it sends one query:"
>
> ```graphql
> {
>   order(id: "abc123") {
>     status
>     restaurant { name }
>     delivery { estimatedArrival }
>   }
> }
> ```
>
> "One request. Exactly the fields the screen needs. No over-fetching. The gateway handles the fan-out to backend services."
>
> "This is the **Backend for Frontend (BFF)** pattern — and GraphQL is a natural fit for it."

---

## Cleanup

```bash
docker compose down
```

---

## Discussion Questions

1. **N+1 problem**: "If a query asks for 10 orders, each with a restaurant, does the gateway make 10 separate calls to Restaurant Service?" _(Yes — this is the N+1 problem. Solutions: DataLoader batching, or batch endpoints on backend services.)_

2. **Caching**: "REST APIs are easy to cache (GET + URL = cache key). How do you cache GraphQL?" _(Harder — POST requests, varying query shapes. Solutions: persisted queries, response caching, CDN integration.)_

3. **Authorization**: "If different users should see different fields, where do you enforce that?" _(In the resolver/controller — check permissions before returning data. Some fields may return null for unauthorized users.)_

4. **Schema evolution**: "How do you deprecate a field without breaking existing clients?" _(Mark it `@deprecated(reason: "Use newField instead")` in the schema. Clients that don't query it are unaffected.)_

5. **When NOT to use GraphQL**: "Would you use GraphQL for a simple CRUD API with one client?" _(Probably not — REST is simpler, better cached, and more widely understood. GraphQL shines with multiple clients needing different data shapes.)_
