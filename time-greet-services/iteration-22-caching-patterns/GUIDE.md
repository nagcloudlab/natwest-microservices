# Iteration 22 — Caching Patterns

## Goal
Eliminate redundant calls from time-service to greeting-service by caching the greeting response in Redis. Learn the cache-aside pattern with TTL-based and manual eviction.

## Duration
~40 minutes

## Services

| Service            | Port | Purpose                                          |
|--------------------|------|--------------------------------------------------|
| greeting-service   | 9001 | Greeting API with rate limiter (from iter-20)    |
| time-service       | 9002 | Calls greeting-service, caches response in Redis |
| Redis              | 6379 | Cache backend (30-second TTL)                    |
| Prometheus         | 9090 | Scrapes `/actuator/prometheus` from both services|
| Grafana            | 3001 | Dashboards and alerting on Prometheus data       |

## What Changed (from Iteration 21)

| File                                | Change                                                  |
|-------------------------------------|---------------------------------------------------------|
| `time-service/pom.xml`             | +spring-boot-starter-data-redis, +spring-boot-starter-cache |
| `time-service/.../CacheConfig.java` | NEW — RedisCacheManager with 30s TTL, JSON serializer   |
| `time-service/.../GreetingClient.java` | NEW — @Cacheable greeting fetch, @CacheEvict eviction |
| `time-service/.../TimeController.java` | Uses GreetingClient + DELETE /api/time/cache endpoint |
| `time-service/application.properties` | +Redis host/port, +caches actuator endpoint           |
| `time-service/application-docker.properties` | +spring.data.redis.host=redis                  |
| `docker-compose.yml`               | +redis:7-alpine with healthcheck                        |

---

## Opening Story

> **"In Iteration 20 we added rate limiting — 5 requests per 10 seconds to greeting-service. But time-service calls greeting-service on every single request. Send 6 requests to time-service and the 6th fails. That's fragile."**
>
> The fix is simple: **cache the greeting**. The first request calls greeting-service and stores the result in Redis. The next 29 seconds of requests get the cached value instantly — no network call, no rate limiter consumption.
>
> This is the **cache-aside pattern**: check cache first, call the service only on a miss, store the result for future hits.

---

## Act 1 — Start the Stack

```bash
cd time-greet-services/iteration-22-caching-patterns
docker compose up --build -d
```

Wait for all five containers to become healthy:

```bash
docker compose ps
```

You should see `greeting-service`, `time-service`, `redis`, `prometheus`, and `grafana` all running.

### Quick smoke test

```bash
curl -s localhost:9001/api/greeting | jq .
curl -s localhost:9002/api/time | jq .
```

---

## Act 2 — Cache Miss (First Request)

```bash
curl -s localhost:9002/api/time | jq .
```

Now check time-service logs:

```bash
docker compose logs time-service --tail 5
```

You'll see:
```
Cache miss — calling greeting-service
Received greeting from greeting-service: Good morning, World!
```

And in greeting-service logs:

```bash
docker compose logs greeting-service --tail 5
```

You'll see the greeting request was received — the call actually happened.

### Discussion point

> **What is cache-aside?** The application checks the cache first. On a miss, it calls the origin service, stores the result in the cache, and returns it. Spring's `@Cacheable` annotation does this automatically — the method body only executes on a cache miss.

---

## Act 3 — Cache Hit (Second Request)

```bash
curl -s localhost:9002/api/time | jq .
```

Check time-service logs again:

```bash
docker compose logs time-service --tail 5
```

This time there is **no** "Cache miss" line — the greeting came straight from Redis. The timestamp in the response is still fresh (it's never cached), but the greeting was served from cache.

Check greeting-service logs:

```bash
docker compose logs greeting-service --tail 5
```

**No new request** — greeting-service was never called.

### Discussion point

> **Why is the time still fresh?** Only the greeting is cached. `Instant.now()` runs on every request in the controller. The `@Cacheable` annotation is on `GreetingClient.fetchGreeting()`, not on the entire endpoint.

---

## Act 4 — Inspect Redis

See what's in the cache:

```bash
docker compose exec redis redis-cli KEYS '*'
```

You'll see a key like `greetings::SimpleKey []`.

View the cached value:

```bash
docker compose exec redis redis-cli GET 'greetings::SimpleKey []'
```

You'll see the JSON-serialized greeting string — human-readable because we configured `GenericJackson2JsonRedisSerializer`.

Check the TTL remaining:

```bash
docker compose exec redis redis-cli TTL 'greetings::SimpleKey []'
```

It will show a number ≤ 30 (seconds remaining before expiry).

### Discussion point

> **Why JSON serialization?** The default Java serializer produces binary blobs that are impossible to inspect. JSON lets you `redis-cli GET` the value and see exactly what's cached — invaluable for debugging.

---

## Act 5 — TTL Expiration

Wait 30 seconds (or check TTL until it reaches 0), then curl again:

```bash
sleep 30
curl -s localhost:9002/api/time | jq .
```

Check logs:

```bash
docker compose logs time-service --tail 5
```

You'll see "Cache miss — calling greeting-service" again — the TTL expired and Spring re-fetched the greeting.

### Discussion point

> **How do you choose a TTL?** It depends on how stale the data can be. For a greeting that rarely changes, 5 minutes would be fine. For stock prices, 1 second. For user profiles, 30 seconds to 5 minutes. There's no universal answer — it's a tradeoff between freshness and load reduction.

---

## Act 6 — Manual Eviction

First, make a request to populate the cache:

```bash
curl -s localhost:9002/api/time | jq .
```

Now manually evict:

```bash
curl -s -X DELETE localhost:9002/api/time/cache | jq .
```

You'll get `{"status": "cache evicted"}`.

Verify the cache is empty:

```bash
docker compose exec redis redis-cli KEYS '*'
```

No keys. The next request will be a cache miss:

```bash
curl -s localhost:9002/api/time | jq .
docker compose logs time-service --tail 3
```

You'll see "Cache miss" again.

### Discussion point

> **When would you use manual eviction?** When the underlying data changes. If greeting-service updates its greeting message, you'd want to evict the cache immediately rather than waiting for TTL. In a real system, this could be triggered by a webhook, message queue event, or admin API.

---

## Act 7 — Caching vs Rate Limiting

This is the payoff. In iteration 20, flooding time-service with requests caused rate limit failures on greeting-service. With caching, the first request fetches and caches the greeting — all subsequent requests are served from Redis.

```bash
for i in $(seq 1 20); do
  echo "Request $i: $(curl -s -o /dev/null -w '%{http_code}' localhost:9002/api/time)"
done
```

All 20 return `200` — because only 1 actual call went to greeting-service (the cache miss). The other 19 were served from Redis.

Check greeting-service logs to confirm:

```bash
docker compose logs greeting-service --tail 5
```

Only one request was received.

### Discussion point

> **Does caching replace rate limiting?** No — they complement each other. Caching reduces load (fewer calls to the origin). Rate limiting protects the origin when caching fails (cold start, cache eviction, Redis outage). Defense in depth.

---

## Act 8 — Prometheus Cache Metrics

Spring Boot automatically exposes cache metrics via Micrometer. Check them:

```bash
curl -s localhost:9002/actuator/prometheus | grep cache
```

You'll see metrics like:
- `cache_gets_total{cache="greetings",result="hit"}` — cache hits
- `cache_gets_total{cache="greetings",result="miss"}` — cache misses
- `cache_puts_total{cache="greetings"}` — cache puts

In Prometheus UI at **http://localhost:9090**, try:

```promql
cache_gets_total{cache="greetings"}
```

### Discussion point

> **Why monitor cache metrics?** A cache with 0% hit rate is just adding latency (the Redis round-trip) with no benefit. A cache with a very high hit rate might mean the TTL is too long and data is stale. The hit/miss ratio tells you if your caching strategy is working.

---

## Bridge to FTGO

In a real food-ordering platform:
- **Menu data** would be cached with a 5-minute TTL — menus don't change every second
- **Restaurant availability** would have a shorter TTL (30s) — it changes more frequently
- **Order data** would NOT be cached — it's transactional and must be fresh
- You'd use **Redis Cluster** for high availability and **cache warming** on startup
- **Cache invalidation** would be event-driven via Kafka — when a restaurant updates its menu, a message triggers eviction across all service instances

---

## Cleanup

```bash
docker compose down
```

---

## Summary

| Concept | What We Used | What It Does |
|---------|-------------|--------------|
| Cache-aside pattern | `@Cacheable` / `@CacheEvict` | Check cache, miss, call origin, store result |
| Redis backend | `spring-boot-starter-data-redis` | Lettuce client connecting to Redis |
| TTL eviction | `RedisCacheConfiguration.entryTtl(30s)` | Auto-expire cached entries after 30 seconds |
| Manual eviction | `DELETE /api/time/cache` | Force cache clear on demand |
| JSON serialization | `GenericJackson2JsonRedisSerializer` | Human-readable cached values in Redis |
| Cache metrics | Micrometer + Prometheus | `cache_gets_total`, `cache_puts_total` |

---

## Discussion Questions

1. **What happens if Redis goes down?** The `@Cacheable` method falls through to the actual service call — the application still works, just without caching. You'd want to configure a `CacheErrorHandler` to log Redis failures gracefully rather than letting them propagate as exceptions.

2. **Cache-aside vs write-through vs write-behind?** Cache-aside (what we used) is the simplest — the application manages the cache explicitly. Write-through updates the cache on every write. Write-behind queues cache updates asynchronously. Cache-aside is the most common pattern for read-heavy workloads.

3. **Why not cache the entire `/api/time` response?** Because the time changes on every request. You only cache what's stable (the greeting) and compute what's dynamic (the timestamp) fresh each time.

4. **How would you handle cache stampede?** If the cache expires and 100 requests arrive simultaneously, all 100 call greeting-service. Solutions: lock-based fetching (`sync=true` on `@Cacheable`), probabilistic early expiration, or background refresh before TTL expires.

5. **What's the difference between local caching and distributed caching?** Local (in-memory) caching is faster but per-instance — each pod has its own cache. Distributed (Redis) caching is shared across all instances — one cache miss populates the cache for everyone. Use distributed caching when consistency across instances matters.

---

## Quick Reference

```bash
# Start everything
docker compose up --build -d

# Check service health
curl -s localhost:9001/actuator/health | jq .
curl -s localhost:9002/actuator/health | jq .

# First request (cache miss)
curl -s localhost:9002/api/time | jq .

# Second request (cache hit)
curl -s localhost:9002/api/time | jq .

# Inspect Redis cache
docker compose exec redis redis-cli KEYS '*'
docker compose exec redis redis-cli GET 'greetings::SimpleKey []'
docker compose exec redis redis-cli TTL 'greetings::SimpleKey []'

# Manual cache eviction
curl -s -X DELETE localhost:9002/api/time/cache | jq .

# Flood test (all 200s thanks to caching)
for i in $(seq 1 20); do curl -s -o /dev/null -w "%{http_code}\n" localhost:9002/api/time; done

# Cache metrics
curl -s localhost:9002/actuator/prometheus | grep cache

# Cleanup
docker compose down
```
