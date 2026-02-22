# Iteration 3: Extract Accounting Service + All 6 Fault Tolerance Patterns

## What Changed

| Component | Before (Iteration 2) | After (Iteration 3) |
|-----------|----------------------|----------------------|
| Payment logic | Local `AccountingService` in monolith | Standalone `accounting-service` on port 8083 |
| Payment data | `payment` table in monolith's H2 | Separate H2 database in accounting-service |
| Communication | Direct method call | REST via `AccountingServiceClient` |
| Failure handling | None (in-process, can't fail) | **All 6 MicroProfile Fault Tolerance patterns** |

## Architecture

```
                                    +---------------------+
                                    |  Restaurant Service  |
                           REST     |     (port 8081)      |
                        +---------->|  H2: restaurant DB   |
                        |           +---------------------+
+--------------------+  |
|   FTGO Monolith    |  |           +---------------------+
|   (port 8080)      |  |  Kafka    | Notification Service |
|                    |  +---------->|     (port 8082)      |
|   Modules remaining:|  |  async    |  H2: notification DB |
|   - Order          |  |           +---------------------+
|   - Kitchen        |  |
|   - Delivery       |  |  REST +   +---------------------+
|                    |  |  Circuit  |  Accounting Service  |
|   H2: ftgo DB      |  +--Breaker->|     (port 8083)      |
|   (order, kitchen, |              |  H2: accounting DB   |
|    delivery, courier)|             +---------------------+
+--------------------+
         |
    Kafka cluster
    (9092, 9093, 9094)
```

## The 6 MicroProfile Fault Tolerance Patterns

MicroProfile Fault Tolerance 4.0 defines 6 patterns for building resilient microservices. Spring doesn't implement the MicroProfile spec directly, but provides equivalent functionality through Resilience4j and Spring's own features.

### Pattern Overview

| # | MicroProfile Annotation | Spring/Resilience4j Equivalent | Applied To | Why It Fits |
|---|------------------------|-------------------------------|------------|-------------|
| 1 | `@Timeout` | `RestTemplate` connect/read timeout | Both service client constructors | Prevent hanging on slow/unresponsive services |
| 2 | `@Retry` | `@Retry` (Resilience4j) | `RestaurantServiceClient.getRestaurant()` | Idempotent GET — retry transient failures |
| 3 | `@Fallback` | `fallbackMethod` parameter | `RestaurantServiceClient.getAllRestaurants()` → empty list | Non-critical browsing — degrade gracefully |
| 4 | `@CircuitBreaker` | `@CircuitBreaker` (Resilience4j) | Both service clients | Fail fast when downstream service is down |
| 5 | `@Bulkhead` | `@Bulkhead` (Resilience4j) | `RestaurantServiceClient.getAllRestaurants()` | High-traffic browsing — limit concurrency |
| 6 | `@Asynchronous` | `@Async` (Spring) | `OrderEventPublisher.publishOrderCreated()` | Fire-and-forget Kafka — don't block response |

### Resilience4j Annotation Ordering

When multiple annotations are stacked on a method, Resilience4j applies them in this fixed order (outermost to innermost):

```
Retry → CircuitBreaker → RateLimiter → TimeLimiter → Bulkhead → [Your Method]
```

This means:
- **Retry** wraps CircuitBreaker — retries only happen when the circuit is CLOSED
- **CircuitBreaker** wraps Bulkhead — circuit counts bulkhead rejections as failures
- **Bulkhead** is closest to the method — limits actual concurrent executions

---

### Pattern 1: @Timeout

**Problem:** Without timeouts, a slow/hanging service blocks the caller's thread for the default TCP timeout (~30 seconds). Under load, this exhausts the thread pool and cascades the failure.

**Solution:** Set explicit `connectTimeout` and `readTimeout` on `RestTemplate`.

**Applied to:** Both `RestaurantServiceClient` and `AccountingServiceClient` constructors.

```java
SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
factory.setConnectTimeout(connectTimeout);   // 2 seconds
factory.setReadTimeout(readTimeout);         // 3 seconds
this.restTemplate = new RestTemplate(factory);
```

**Config:**
```properties
restaurant-service.connect-timeout=2000
restaurant-service.read-timeout=3000
accounting-service.connect-timeout=2000
accounting-service.read-timeout=3000
```

---

### Pattern 2: @Retry

**Problem:** Transient network failures (brief connectivity blips, DNS hiccups) cause unnecessary errors even though the service is healthy.

**Solution:** Automatically retry failed calls with exponential backoff.

**Applied to:** `RestaurantServiceClient.getRestaurant()` — an idempotent GET.

```java
@Retry(name = "restaurantService", fallbackMethod = "getRestaurantFallback")
@CircuitBreaker(name = "restaurantService", fallbackMethod = "getRestaurantFallback")
public Restaurant getRestaurant(Long id) { ... }
```

**Config:**
```properties
resilience4j.retry.instances.restaurantService.max-attempts=3
resilience4j.retry.instances.restaurantService.wait-duration=500ms
resilience4j.retry.instances.restaurantService.enable-exponential-backoff=true
resilience4j.retry.instances.restaurantService.exponential-backoff-multiplier=2
```

**Why NOT retry `authorizePayment()`?**
POST to charge payment is NOT idempotent — retrying could **double-charge** the customer! Only retry operations that are safe to repeat (idempotent GETs, reads, lookups).

---

### Pattern 3: @Fallback

**Problem:** When a downstream service is unavailable, the default behavior is a 500 error page. For non-critical features, we can do better.

**Solution:** Provide a fallback method that returns a sensible default value.

**Applied to:** `RestaurantServiceClient.getAllRestaurants()` — returns an empty list.

```java
private List<Restaurant> getAllRestaurantsFallback(Throwable t) {
    log.warn(">>> FALLBACK: Restaurant service unavailable, returning empty list");
    return List.of();  // UI shows "no restaurants available" instead of 500
}
```

**Fallback strategies differ by criticality:**

| Method | Fallback Strategy | Why |
|--------|-------------------|-----|
| `getAllRestaurants()` | Return empty list | Browsing is optional — degrade gracefully |
| `getMenuItemsByIds()` | Throw exception | Can't create order without prices |
| `authorizePayment()` | Throw exception | Can't approve order without payment |

---

### Pattern 4: @CircuitBreaker

**Problem:** When a service is down, every request waits for the timeout (3s), consuming threads. Under load, this cascading failure can bring down the entire monolith.

**Solution:** After N consecutive failures, "open" the circuit — fail immediately without even trying to call the service.

**Applied to:** All methods on both `RestaurantServiceClient` and `AccountingServiceClient`.

```java
@CircuitBreaker(name = "restaurantService", fallbackMethod = "getAllRestaurantsFallback")
public List<Restaurant> getAllRestaurants() { ... }
```

**Circuit Breaker states:**
```
     +----------+    failures exceed     +---------+
     | CLOSED   |----threshold (50%)--->|  OPEN   |
     | (normal) |                       |(fail fast|
     +----^-----+                       +----+----+
          |                                  |
          |    success in               wait 15s
          |    half-open                     |
          |                            +----v-----+
          +----------------------------| HALF_OPEN |
                                       | (testing) |
                                       +-----------+
```

**Config:**
```properties
resilience4j.circuitbreaker.instances.restaurantService.sliding-window-size=5
resilience4j.circuitbreaker.instances.restaurantService.failure-rate-threshold=50
resilience4j.circuitbreaker.instances.restaurantService.wait-duration-in-open-state=15s
resilience4j.circuitbreaker.instances.restaurantService.permitted-number-of-calls-in-half-open-state=2
resilience4j.circuitbreaker.instances.restaurantService.minimum-number-of-calls=3
```

---

### Pattern 5: @Bulkhead

**Problem:** A single slow endpoint can consume all available threads, starving other endpoints. If `getAllRestaurants()` is slow, even the ordering and kitchen endpoints stop responding.

**Solution:** Limit the number of concurrent calls to a specific method, isolating its thread usage.

**Applied to:** `RestaurantServiceClient.getAllRestaurants()` — high-traffic browsing endpoint.

```java
@Bulkhead(name = "restaurantService", fallbackMethod = "getAllRestaurantsFallback")
@CircuitBreaker(name = "restaurantService", fallbackMethod = "getAllRestaurantsFallback")
public List<Restaurant> getAllRestaurants() { ... }
```

**Config:**
```properties
resilience4j.bulkhead.instances.restaurantService.max-concurrent-calls=5
resilience4j.bulkhead.instances.restaurantService.max-wait-duration=500ms
```

When the 6th concurrent request arrives, it waits up to 500ms. If a slot doesn't free up, the bulkhead rejects it and the fallback returns an empty list.

---

### Pattern 6: @Asynchronous

**Problem:** Kafka publishing (~50-200ms) blocks the HTTP thread. The user waits for Kafka acknowledgment before seeing their order confirmation.

**Solution:** Run the Kafka publish on a background thread so the HTTP response returns immediately.

**Applied to:** `OrderEventPublisher.publishOrderCreated()`.

```java
@Async("eventPublisherExecutor")
public void publishOrderCreated(OrderCreatedEvent event) {
    log.info(">>> Publishing on thread [{}]", Thread.currentThread().getName());
    // Kafka publish happens here — on "event-publisher-1" thread, not "http-nio-8080-exec-1"
}
```

**AsyncConfig:**
```java
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean(name = "eventPublisherExecutor")
    public Executor eventPublisherExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("event-publisher-");
        executor.initialize();
        return executor;
    }
}
```

**Why NOT @Async on authorizePayment()?**
Payment is synchronous — we need the result (AUTHORIZED/FAILED) before approving the order. Making it async would mean approving orders without confirmed payment.

---

## When NOT to Apply Each Pattern

| Pattern | Don't Apply When... | Example |
|---------|---------------------|---------|
| @Timeout | The operation has no external I/O | Local database queries with connection pooling |
| @Retry | The operation is NOT idempotent | `POST /payments/authorize` — retry = double-charge! |
| @Fallback | The result is critical for correctness | `getMenuItemsByIds()` — can't order without prices |
| @CircuitBreaker | Failures are expected and normal | Health check endpoints that intentionally return errors |
| @Bulkhead | The method is on the critical path with low traffic | `authorizePayment()` — rejecting payments loses revenue |
| @Asynchronous | You need the result before proceeding | `authorizePayment()` — must know if payment succeeded |

## Patterns Taught — Summary

### Anti-Corruption Layer (ACL)

`AccountingServiceClient` translates between the monolith's internal model and the accounting-service's REST API. The monolith still calls `authorizePayment(orderId, amount, method)` — the same method signature as before. Under the hood, the ACL builds a JSON request and calls the external service.

### Sync REST vs Async Kafka — When to Use Which?

| | Restaurant (Iter 1) | Notification (Iter 2) | Accounting (Iter 3) |
|---|---|---|---|
| Communication | Sync REST | Async Kafka | Sync REST + Circuit Breaker |
| Why? | Need data immediately | Fire-and-forget | Need confirmation but handle failure |
| If service is down? | Retry + Circuit Breaker | Order succeeds (notification lost) | Circuit breaker trips, order rejected |
| Patterns applied | Timeout, Retry, Fallback, CircuitBreaker, Bulkhead | Async | Timeout, CircuitBreaker |

## Key Files

### Accounting Service (NEW — port 8083)

| File | Purpose |
|------|---------|
| `AccountingServiceApplication.java` | Spring Boot entry point |
| `Payment.java` | JPA entity (owns payment data) |
| `PaymentStatus.java` | Enum: PENDING, AUTHORIZED, FAILED, REFUNDED |
| `PaymentRepository.java` | Spring Data JPA repository |
| `PaymentGateway.java` | Interface for payment processing |
| `MockStripePaymentGateway.java` | Simulates Stripe with 500ms delay |
| `AccountingService.java` | Business logic (authorize, refund) |
| `AccountingController.java` | REST API: POST /api/payments/authorize |
| `PaymentRequest.java` | Request DTO |

### Modified Monolith

| File | Change |
|------|--------|
| `AccountingServiceClient.java` | REST proxy with `@CircuitBreaker` + timeout |
| `RestaurantServiceClient.java` | REST proxy with `@Retry`, `@CircuitBreaker`, `@Bulkhead`, `@Fallback`, timeout |
| `OrderEventPublisher.java` | `@Async` for fire-and-forget Kafka publishing |
| `AsyncConfig.java` | **NEW** — `@EnableAsync` + custom thread pool executor |
| `PaymentResponse.java` | DTO for deserializing accounting-service response |
| `OrderService.java` | Uses `AccountingServiceClient` instead of local `AccountingService` |
| `application.properties` | Full Resilience4j config for all patterns |
| `pom.xml` | `resilience4j-spring-boot3` + `spring-boot-starter-aop` |

## How to Run

### Prerequisites
- Kafka cluster running (brokers on 9092, 9093, 9094)
- Java 17+, Maven

### Start Services (in order)

```bash
# Terminal 1: Start Kafka cluster (if not already running)
cd kafka && ./start-cluster.sh

# Terminal 2: Restaurant Service (port 8081)
cd iteration-3-accounting-service/restaurant-service
mvn spring-boot:run

# Terminal 3: Notification Service (port 8082)
cd iteration-3-accounting-service/notification-service
mvn spring-boot:run

# Terminal 4: Accounting Service (port 8083)
cd iteration-3-accounting-service/accounting-service
mvn spring-boot:run

# Terminal 5: FTGO Monolith (port 8080)
cd iteration-3-accounting-service/ftgo-monolith
mvn spring-boot:run
```

### Verify

```bash
# Check accounting service is up
curl http://localhost:8083/api/payments/order/1

# Place an order via the monolith API
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "consumerId": 1,
    "consumerName": "Alice",
    "consumerContact": "9876543210",
    "restaurantId": 1,
    "deliveryAddress": "123 Main St",
    "paymentMethod": "CREDIT_CARD",
    "items": [{"menuItemId": 1, "quantity": 2}]
  }'

# Check payment was created in accounting service
curl http://localhost:8083/api/payments/order/1
```

## Exercises: Test Each Pattern

### Exercise 1: @Timeout

**Goal:** Verify that slow services don't hang the monolith.

1. In `restaurant-service`, add a `Thread.sleep(5000)` to the `GET /api/restaurants/{id}` endpoint
2. Try placing an order from the monolith
3. **Expected:** The request fails after ~3 seconds (the `readTimeout`) instead of hanging for 30+ seconds
4. Check monolith logs for `SocketTimeoutException`
5. Remove the `Thread.sleep` when done

### Exercise 2: @Retry

**Goal:** See retry with exponential backoff in action.

1. Start all services normally
2. Place an order — it succeeds
3. Kill restaurant-service
4. Quickly restart restaurant-service (within 2 seconds)
5. Place another order immediately
6. **Expected:** The first attempt fails, but retry #2 or #3 succeeds transparently
7. Check monolith logs for retry attempts (you'll see multiple `Fetching restaurant` log lines)

### Exercise 3: @Fallback

**Goal:** See graceful degradation vs. 500 errors.

1. Kill restaurant-service
2. Visit `http://localhost:8080/consumer/restaurants` in the browser
3. **Expected:** The page shows an empty restaurant list (not a 500 error page)
4. Check monolith logs for: `FALLBACK: Restaurant service unavailable, returning empty list`
5. Now try placing an order (which calls `getMenuItemsByIds`)
6. **Expected:** This FAILS with an error — the fallback throws because we can't order without prices

### Exercise 4: @CircuitBreaker

**Goal:** See the circuit breaker trip and recover.

1. Kill restaurant-service
2. Refresh `http://localhost:8080/consumer/restaurants` 4+ times quickly
3. **Expected after 3 failures:** Circuit opens — subsequent requests fail INSTANTLY (no 3-second timeout)
4. Check logs for circuit breaker state changes
5. Restart restaurant-service and wait 15 seconds (`wait-duration-in-open-state`)
6. Refresh the page — circuit moves to HALF_OPEN, then CLOSED after success
7. **Expected:** Restaurants appear again

### Exercise 5: @Bulkhead

**Goal:** See concurrency limiting in action.

1. In `restaurant-service`, add `Thread.sleep(3000)` to `GET /api/restaurants`
2. Open 6+ browser tabs and refresh `http://localhost:8080/consumer/restaurants` simultaneously
3. **Expected:** First 5 requests wait (up to 3s for the slow response). The 6th request gets the fallback immediately (empty list) because the bulkhead is full
4. Check logs for bulkhead rejection messages
5. Remove the `Thread.sleep` when done

### Exercise 6: @Async

**Goal:** Verify that Kafka publishing doesn't block the HTTP response.

1. In `OrderEventPublisher`, add `Thread.sleep(3000)` before `kafkaTemplate.send()`
2. Place an order via the API
3. **Expected:** The order response returns IMMEDIATELY (not after 3 seconds)
4. Check logs — the publish happens on `event-publisher-1` thread, not `http-nio-8080-exec-*`
5. Remove the `Thread.sleep` when done

### Discussion Points

1. **Why not use Kafka for payments like we did for notifications?**
   - Payment is synchronous: we need to know if it succeeded before approving the order
   - Notification is fire-and-forget: the order succeeds even if the SMS fails

2. **What's the difference between no circuit breaker (iteration 1) and with one (iteration 3)?**
   - Iteration 1: When restaurant-service is down, every request hangs for the TCP timeout (~30s)
   - Iteration 3: Circuit breaker trips after 3 failures, subsequent requests fail fast (<1ms)

3. **Why does `getAllRestaurants()` have a bulkhead but `authorizePayment()` doesn't?**
   - Restaurant browsing is high-traffic and non-critical — limiting it protects other endpoints
   - Payment is low-traffic and critical — rejecting payments means losing revenue

4. **What happens if @Retry and @CircuitBreaker are both on the same method?**
   - Retry wraps CircuitBreaker (outer → inner). If the circuit is OPEN, retry won't even attempt.
   - The retry only fires when the circuit is CLOSED or HALF_OPEN.

5. **Why use a custom thread pool for @Async instead of the default?**
   - Default `SimpleAsyncTaskExecutor` creates a new thread per invocation (unbounded!)
   - A bounded pool (`core=2, max=5, queue=25`) prevents thread exhaustion under load
   - Named threads (`event-publisher-*`) make logs and thread dumps easier to debug

## Compare With Previous Iterations

```bash
# See what changed in the monolith since iteration 2
diff -r iteration-2-notification-service/ftgo-monolith/src iteration-3-accounting-service/ftgo-monolith/src
```

## What's Next?

**Iteration 4: Extract Kitchen Service** — Domain Events + Cross-module decoupling via Kafka. The kitchen module has tighter coupling (order status updates flow back), making it a more complex extraction.
