# Iteration 19 – API & Contract Design

> **Goal:** Explore the five pillars of API design — OpenAPI/Swagger documentation,
> API versioning strategies, contract-first development, RFC 9457 error handling,
> and schema evolution — using the simple greeting & time services.

> **Duration:** ~40 minutes | **Prerequisites:** Docker, curl, jq

## The Services

| Service | Port | Purpose |
|---------|------|---------|
| greeting-service | 9001 | URI versioning, header versioning, error handling |
| time-service | 9002 | Contract-first development (openapi-generator) |

## What Changed vs Previous Iterations

| Before | After | Concept |
|--------|-------|---------|
| No API docs | Swagger UI on every service | OpenAPI / springdoc |
| Single endpoint | `/api/v1/…` and `/api/v2/…` | URI versioning |
| — | `Accept: application/vnd.demo.v1+json` | Header versioning |
| Code-first controllers | YAML spec → generated interface → implementation | Contract-first |
| Generic error JSON | `application/problem+json` with RFC 9457 fields | Standardised errors |
| One response shape | V2 is a superset of V1 | Schema evolution |

---

## Opening Story (2 min)

You join NatWest's payments team.  Three squads each consume your "greeting" API.
Squad A is on V1, Squad B wants richer data, and Squad C is building a new app
that needs a formal contract before writing a single line of code.

How do you serve all three without breaking anyone?  That is what this iteration
answers — versioning, contracts, and evolution.

---

## Act 1 — Start the Services (3 min)

```bash
cd time-greet-services/iteration-19-api-design
docker compose up --build -d
```

Wait for both services to become healthy:

```bash
docker compose ps
```

You should see both services with status `healthy`.

---

## Act 2 — URI Versioning (5 min)

### V1 — Minimal response (2 fields)

```bash
curl -s localhost:9001/api/v1/greeting | jq
```

```json
{
  "message": "Hello, World!",
  "host": "abc123"
}
```

### V2 — Extended response (5 fields, superset of V1)

```bash
curl -s localhost:9001/api/v2/greeting | jq
```

```json
{
  "message": "Hello, World!",
  "host": "abc123",
  "language": "en",
  "timestamp": "2024-01-15T10:30:00Z",
  "metadata": { "version": "2", "api": "greeting" }
}
```

### Personalised greeting

```bash
curl -s localhost:9001/api/v1/greeting/Alice | jq
curl -s localhost:9001/api/v2/greeting/Alice | jq
```

> **Key point:** V1 clients keep working. V2 clients get richer data.
> Both versions coexist on the same service.

---

## Act 3 — Header Versioning (5 min)

Same endpoint, different `Accept` header selects the version:

### V1 via header

```bash
curl -s -H "Accept: application/vnd.demo.v1+json" localhost:9001/api/greeting | jq
```

### V2 via header

```bash
curl -s -H "Accept: application/vnd.demo.v2+json" localhost:9001/api/greeting | jq
```

> **Discussion:** URI versioning is simple and visible. Header versioning keeps
> URLs clean but is harder to test in a browser. Most teams start with URI
> versioning and graduate to header versioning for mature APIs.

---

## Act 4 — RFC 9457 Error Handling (5 min)

Try a name that contains digits:

```bash
curl -s localhost:9001/api/v1/greeting/R2D2 | jq
```

```json
{
  "type": "https://api.example.com/errors/greeting-not-found",
  "title": "Greeting Not Found",
  "status": 404,
  "detail": "Cannot greet 'R2D2' — name contains invalid characters",
  "timestamp": "2024-01-15T10:30:00Z",
  "service": "greeting-service"
}
```

Check the Content-Type:

```bash
curl -sI localhost:9001/api/v1/greeting/R2D2 | grep content-type
```

> `content-type: application/problem+json`

> **Key point:** RFC 9457 (Problem Details for HTTP APIs) gives every error a
> machine-readable `type` URI, a human-readable `title` and `detail`, plus
> custom extension fields (`timestamp`, `service`). Clients parse errors the
> same way regardless of which service produced them.

---

## Act 5 — Contract-First Development (10 min)

### The spec drives the code

Open `time-service/src/main/resources/openapi/combined-api.yaml` — this YAML file
*is* the contract.

During `mvn compile`, the **openapi-generator-maven-plugin** reads that file and
generates a Java interface (`CombinedApi`) in `target/generated-sources/`.

```bash
# You can see the generated interface:
find time-service/target/generated-sources -name "CombinedApi.java" 2>/dev/null
```

`TimeController implements CombinedApi` — the compiler enforces that every
operation in the spec has an implementation. If the spec adds a new endpoint
and you forget to implement it, the build fails.

### Call the combined endpoint

```bash
curl -s localhost:9002/api/v1/combined | jq
```

```json
{
  "time": "2024-01-15T10:30:00Z",
  "greeting": "Hello, World!",
  "host": "abc123"
}
```

### What happens when greeting-service is down?

```bash
docker compose stop greeting-service
curl -s localhost:9002/api/v1/combined | jq
```

The time-service still responds — it falls back to a default greeting:

```json
{
  "time": "2024-01-15T10:31:00Z",
  "greeting": "Hello, World! (greeting-service unavailable)",
  "host": "abc123"
}
```

Bring it back:

```bash
docker compose start greeting-service
```

> **Contract-first workflow:**
> 1. Write (or update) the OpenAPI YAML spec
> 2. Run `mvn compile` — the plugin generates the interface
> 3. Implement the interface in your controller
> 4. The compiler guarantees your code matches the spec

---

## Act 6 — Schema Evolution & Swagger UI (5 min)

### Schema evolution — V2 is a superset of V1

A V1 client can safely consume V2 responses by ignoring unknown fields:

```bash
# V2 response, but extract only V1 fields
curl -s localhost:9001/api/v2/greeting | jq '{message, host}'
```

Jackson (the default JSON library) ignores unknown properties by default, so
V1 clients never break when V2 adds fields.

> **Rule of thumb:** Only make *additive* changes — new optional fields,
> new endpoints, new enum values. Never remove or rename existing fields.

### Swagger UI

Open in your browser:

- **greeting-service:** [http://localhost:9001/swagger-ui.html](http://localhost:9001/swagger-ui.html)
- **time-service:** [http://localhost:9002/swagger-ui.html](http://localhost:9002/swagger-ui.html)

Explore the interactive docs — every `@Tag`, `@Operation`, `@Schema` annotation
you saw in the code is rendered here. You can execute requests directly from the
browser.

---

## Bridge to FTGO (2 min)

In a real system like FTGO:

- **OpenAPI specs** are published to a developer portal so frontend teams and
  third-party integrators can generate client SDKs automatically
- **URI versioning** lets the Order Service expose `/api/v1/orders` and
  `/api/v2/orders` side-by-side during migration
- **Contract-first** is used between teams — the spec is agreed *before*
  either side writes code, preventing integration surprises
- **RFC 9457** ensures the API gateway returns consistent error shapes regardless
  of which downstream service failed
- **Schema evolution** is critical for event-driven systems — Kafka consumers
  must tolerate new fields in events without redeployment

---

## Cleanup

```bash
docker compose down
```

---

## Summary

| Problem | Solution | Concept |
|---------|----------|---------|
| No API documentation | springdoc-openapi + Swagger UI | OpenAPI 3.0 |
| Multiple client versions | `/api/v1/…`, `/api/v2/…` | URI versioning |
| Clean URLs + versioning | `Accept: application/vnd.demo.v1+json` | Header versioning |
| Code drifts from spec | YAML spec → generated interface → implement | Contract-first |
| Inconsistent error shapes | `ProblemDetail` with type, title, detail | RFC 9457 |
| Breaking changes | V2 adds fields, V1 clients ignore them | Schema evolution |

---

## Discussion Questions

1. When would you choose URI versioning over header versioning?
   *(URI: simple, visible, easy to test; Header: clean URLs, better for mature APIs)*

2. What makes a change "breaking" vs "non-breaking"?
   *(Breaking: removing fields, renaming fields, changing types. Non-breaking: adding optional fields, new endpoints)*

3. Why is contract-first preferred in large organisations?
   *(Teams agree on the spec before coding; frontend and backend can work in parallel; the spec becomes the single source of truth)*

4. How does RFC 9457 improve on ad-hoc error JSON?
   *(Standard fields — type, title, status, detail — mean clients can parse errors uniformly across all services)*

5. What happens if you add a required field to V2 but a V1 client calls V2?
   *(V1 client ignores the new field — no breakage. But if you remove a field V1 depends on, V1 breaks.)*

---

## Quick Reference

```bash
# URI versioning
curl -s localhost:9001/api/v1/greeting | jq
curl -s localhost:9001/api/v2/greeting | jq

# Header versioning
curl -s -H "Accept: application/vnd.demo.v1+json" localhost:9001/api/greeting | jq
curl -s -H "Accept: application/vnd.demo.v2+json" localhost:9001/api/greeting | jq

# Personalised greeting
curl -s localhost:9001/api/v1/greeting/Alice | jq

# Error handling (RFC 9457)
curl -s localhost:9001/api/v1/greeting/R2D2 | jq

# Contract-first combined endpoint
curl -s localhost:9002/api/v1/combined | jq

# Swagger UI
open http://localhost:9001/swagger-ui.html
open http://localhost:9002/swagger-ui.html
```
