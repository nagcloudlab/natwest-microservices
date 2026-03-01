# Iteration 20 – Rate Limiting

> **Goal:** Protect API endpoints with Resilience4j RateLimiter so a single
> client cannot overwhelm the greeting-service — and see how a downstream
> caller (time-service) handles 429 responses gracefully.

> **Duration:** ~25 minutes | **Prerequisites:** Docker, curl, jq

## The Services

| Service | Port | Purpose |
|---------|------|---------|
| greeting-service | 9001 | Greeting endpoint protected by RateLimiter (5 req / 10 s) |
| time-service | 9002 | Calls greeting-service, handles 429 with fallback |

## What Changed vs Previous Iterations

| Before | After | Concept |
|--------|-------|---------|
| No request throttling | 5 requests per 10 s window | Resilience4j RateLimiter |
| Unlimited calls succeed | 6th call returns `429 Too Many Requests` | Back-pressure |
| No rate limiter metrics | `/actuator/ratelimiters` exposed | Observability |
| Generic error on downstream failure | Specific 429 fallback in time-service | Graceful degradation |

---

## Opening Story (2 min)

Your greeting-service has gone viral inside the bank.  Every dashboard, chatbot,
and internal tool hammers it constantly.  One misbehaving batch job sends 500
requests per second and the service falls over — taking the time-service with it.

The fix?  A **rate limiter** that caps each client to a sustainable throughput.
Legitimate traffic flows normally; abusive bursts get a clear `429` signal to
back off.

---

## Act 1 — Start the Services (3 min)

```bash
cd time-greet-services/iteration-20-rate-limiting
docker compose up --build -d
```

Wait for both services to become healthy:

```bash
docker compose ps
```

---

## Act 2 — Normal Requests (3 min)

First, verify the greeting works normally:

```bash
curl -s localhost:9001/api/greeting | jq
```

```json
{
  "message": "Hello, World!",
  "host": "abc123"
}
```

Personalised greeting:

```bash
curl -s localhost:9001/api/greeting/Alice | jq
```

```json
{
  "message": "Hello, Alice!",
  "host": "abc123"
}
```

The time-service calls greeting-service internally:

```bash
curl -s localhost:9002/api/time | jq
```

```json
{
  "time": "2024-01-15T10:30:00Z",
  "greeting": "Hello, World!",
  "host": "time-abc123"
}
```

> Everything works. Now let's break it.

---

## Act 3 — Hit the Rate Limit (5 min)

The rate limiter allows **5 requests per 10-second window**.
Send 8 rapid requests in a loop:

```bash
for i in $(seq 1 8); do
  echo "--- Request $i ---"
  curl -s -w "\nHTTP %{http_code}\n" localhost:9001/api/greeting
  echo
done
```

Requests 1–5 succeed with `200`. Requests 6–8 return:

```json
{
  "type": "https://api.example.com/errors/rate-limit-exceeded",
  "title": "Too Many Requests",
  "status": 429,
  "detail": "Rate limit exceeded — try again later",
  "timestamp": "2024-01-15T10:30:05Z",
  "service": "greeting-service"
}
```

> **Key point:** The response is RFC 9457 `application/problem+json` —
> clients get a machine-readable error with a clear type URI they can
> handle programmatically.

Wait 10 seconds, then try again — the window resets:

```bash
sleep 10
curl -s localhost:9001/api/greeting | jq
```

---

## Act 4 — Downstream Impact (5 min)

What happens to time-service when greeting-service is rate-limited?

Exhaust the greeting-service limit first:

```bash
for i in $(seq 1 6); do curl -s localhost:9001/api/greeting > /dev/null; done
```

Now call time-service:

```bash
curl -s localhost:9002/api/time | jq
```

```json
{
  "time": "2024-01-15T10:31:00Z",
  "greeting": "Hello, World! (greeting-service rate limit exceeded)",
  "host": "time-abc123"
}
```

> Time-service catches the `429`, returns a fallback greeting, and stays
> healthy itself. This is **graceful degradation** — the system bends but
> doesn't break.

---

## Act 5 — Monitor the Rate Limiter (3 min)

### Resilience4j actuator endpoint

```bash
curl -s localhost:9001/actuator/ratelimiters | jq
```

```json
{
  "rateLimiters": ["greeting"]
}
```

### Metrics

```bash
curl -s localhost:9001/actuator/metrics/resilience4j.ratelimiter.available.permissions | jq
```

This shows how many permits remain in the current window.

```bash
curl -s localhost:9001/actuator/metrics/resilience4j.ratelimiter.waiting_threads | jq
```

> In production, you'd feed these metrics into Prometheus/Grafana to alert
> when a rate limiter is consistently saturated — a signal to scale up or
> investigate the abusive client.

---

## Act 6 — Tune the Configuration (3 min)

The rate limiter is configured in `greeting-service/src/main/resources/application.properties`:

```properties
# Allow 5 requests per 10-second window
resilience4j.ratelimiter.instances.greeting.limitForPeriod=5
resilience4j.ratelimiter.instances.greeting.limitRefreshPeriod=10s
# Reject immediately when limit is hit (no waiting)
resilience4j.ratelimiter.instances.greeting.timeoutDuration=0s
```

| Property | Meaning | Production Example |
|----------|---------|-------------------|
| `limitForPeriod` | Max requests per window | 100 |
| `limitRefreshPeriod` | Window duration | 1s (= 100 req/s) |
| `timeoutDuration` | How long to block before rejecting | 500ms (queue briefly) |

> **Tip:** Setting `timeoutDuration` > 0 makes the rate limiter *queue*
> excess requests instead of rejecting them instantly. Useful when you want
> to smooth out small bursts.

---

## Bridge to FTGO (2 min)

In a real system like FTGO:

- **Order Service** rate-limits its create-order endpoint to prevent a
  single restaurant tablet from flooding the system during peak hours
- **API Gateway** applies a *global* rate limiter per API key — individual
  services add a *local* rate limiter as a second line of defence
- **Resilience4j** composes with other patterns: a Circuit Breaker detects
  sustained failures, while the RateLimiter prevents overload *before*
  failures occur
- Rate limiter metrics flow to **Prometheus** → triggers alerts when
  permit utilisation exceeds 80%

---

## Cleanup

```bash
docker compose down
```

---

## Summary

| Problem | Solution | Concept |
|---------|----------|---------|
| Unbounded request rate | `@RateLimiter(name = "greeting")` | Resilience4j RateLimiter |
| No signal to slow down | `429 Too Many Requests` + RFC 9457 body | Back-pressure |
| Downstream cascading failure | time-service catches 429, returns fallback | Graceful degradation |
| No visibility into throttling | `/actuator/ratelimiters` + metrics | Observability |
| Hard-coded limits | `application.properties` configuration | Externalised config |

---

## Discussion Questions

1. What is the difference between **rate limiting** and **circuit breaking**?
   *(Rate limiting caps throughput proactively; circuit breaking reacts to failures reactively)*

2. Where should rate limiting be applied — at the **gateway** or at each **service**?
   *(Both: gateway for per-client fairness, service for self-protection)*

3. What happens if `timeoutDuration` is set to `5s` instead of `0s`?
   *(Excess requests wait up to 5 s for a permit instead of being rejected immediately)*

4. How would you implement **per-user** rate limiting instead of a global limit?
   *(Use a custom `RateLimiterRegistry` keyed by user ID or API key)*

5. What metric would you alert on in production?
   *(High `resilience4j.ratelimiter.waiting_threads` or low `available.permissions`)*

---

## Quick Reference

```bash
# Normal request
curl -s localhost:9001/api/greeting | jq
curl -s localhost:9001/api/greeting/Alice | jq

# Time-service (calls greeting-service)
curl -s localhost:9002/api/time | jq

# Trigger rate limit (send 8 rapid requests)
for i in $(seq 1 8); do
  curl -s -w "\nHTTP %{http_code}\n" localhost:9001/api/greeting
done

# Check rate limiter state
curl -s localhost:9001/actuator/ratelimiters | jq
curl -s localhost:9001/actuator/metrics/resilience4j.ratelimiter.available.permissions | jq

# Swagger UI
open http://localhost:9001/swagger-ui.html
open http://localhost:9002/swagger-ui.html
```
