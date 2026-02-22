# Iteration 1: Extract Restaurant Service

## What You'll Learn

1. **Strangler Fig Pattern** -- The monolith and new service coexist. The monolith delegates to the new service via REST, while all other modules remain untouched.
2. **Database per Service** -- The restaurant service owns its own H2 database. No other service can access restaurant/menu_item tables directly.
3. **Synchronous REST Communication** -- The monolith calls the restaurant service using `RestTemplate`.
4. **Service Proxy Pattern** -- Same interface, different implementation. Controllers don't know the data now comes from another process.

---

## Architecture: Before & After

### Before (Monolith)

```
┌─────────────────────────────────────────────┐
│                 MONOLITH :8080              │
│                                             │
│  ┌──────────┐ ┌──────────┐ ┌──────────────┐ │
│  │  Order    │ │ Kitchen ││  Restaurant   │ │
│  │  Module   │ │  Module  │  Module       │ │
│  └────┬─────┘ └────┬─────┘ └──────┬───────┘ │
│       │             │              │        │
│  ┌────┴─────────────┴──────────────┴───────┐│
│  │         SINGLE SHARED DATABASE          ││
│  │  (orders, kitchen_tickets, restaurant,  ││
│  │   menu_item, courier, delivery, ...)    ││
│  └─────────────────────────────────────────┘│
└──────────────────────────────────────────────┘
```

### After (Iteration 1)

```
┌──────────────────────────────────┐     ┌──────────────────────────┐
│          MONOLITH :8080          │     │  RESTAURANT SERVICE :8081│
│                                  │     │                          │
│  ┌──────────┐  ┌──────────┐      │     │  ┌──────────────┐        │
│  │  Order    │ │ Kitchen  │      │     │  │  Restaurant  │        │
│  │  Module   │ │  Module  │      │     │  │  Controller  │        │
│  └────┬─────┘ └────┬─────┘       │     │  └──────┬───────┘        │
│       │             │            │     │         │                │
│  ┌────┴─────────────┴──────────┐ │     │  ┌──────┴───────┐        │
│  │     MONOLITH DATABASE       │ │     │  │  Restaurant  │        │
│  │  (orders, kitchen_tickets,  │ │     │  │  Database    │        │
│  │   courier, delivery, ...)   │ │     │  │  (H2:mem:    │        │
│  │  NO restaurant/menu_item!   │ │     │  │   restaurant)│        │
│  └─────────────────────────────┘ │     │  └──────────────┘        │
│                                  │     │                          │
│  ┌──────────────────────────┐    │REST │                          │
│  │ RestaurantServiceClient  │────┼────>│  GET /api/restaurants    │
│  │ (HTTP Proxy)             │    │     │  GET /api/restaurants/{id}│
│  └──────────────────────────┘    │     │  GET /api/restaurants/   │
│                                  │     │      menu-items?ids=...  │
└──────────────────────────────────┘     └──────────────────────────┘
```

---

## What Changed in the Monolith?

| File | Change | Why |
|------|--------|-----|
| `RestaurantService.java` | **Deleted** | Replaced by `RestaurantServiceClient` |
| `RestaurantServiceClient.java` | **New** | HTTP proxy that calls restaurant-service:8081 |
| `RestaurantRepository.java` | **Deleted** | No local DB access for restaurants |
| `MenuItemRepository.java` | **Deleted** | No local DB access for menu items |
| `RestaurantController.java` | **Deleted** | REST API moved to restaurant-service |
| `Restaurant.java` | **Modified** | Stripped `@Entity` annotations -- now a plain DTO |
| `MenuItem.java` | **Modified** | Stripped `@Entity` annotations -- now a plain DTO |
| `data.sql` | **Modified** | Removed restaurant/menu_item INSERTs |
| `application.properties` | **Modified** | Added `restaurant-service.url` property |

**What did NOT change:** `ConsumerWebController`, `RestaurantWebController`, `OrderService` -- they still call `getRestaurant()`, `getAllRestaurants()`, `getMenuItemsByIds()`. The proxy is transparent.

---

## How to Run

### Step 1: Start the Restaurant Service (port 8081)

```bash
cd iteration-1-restaurant-service/restaurant-service
mvn spring-boot:run
```

Wait for: `Started RestaurantServiceApplication on port 8081`

### Step 2: Verify the Restaurant Service

```bash
# Get all restaurants
curl http://localhost:8081/api/restaurants

# Get a specific restaurant with menu
curl http://localhost:8081/api/restaurants/1

# Get menu items by IDs (used by OrderService)
curl "http://localhost:8081/api/restaurants/menu-items?ids=1,2,3"

# H2 Console
open http://localhost:8081/h2-console
# JDBC URL: jdbc:h2:mem:restaurant
```

### Step 3: Start the Monolith (port 8080)

```bash
cd iteration-1-restaurant-service/ftgo-monolith
mvn spring-boot:run
```

Wait for: `Started FtgoMonolithApplication on port 8080`

### Step 4: Test the Full Flow

1. Browse restaurants: http://localhost:8080/consumer/restaurants
   - Data comes from restaurant-service via REST!
2. View a menu: Click "View Menu" on any restaurant
3. Place an order: Select items, fill in details, submit
4. Check the order: You'll be redirected to the confirmation page
5. Restaurant dashboard: http://localhost:8080/restaurant
6. Courier dashboard: http://localhost:8080/courier

Everything works end-to-end, just like before -- but restaurant data now lives in a separate service.

---

## The Key Class: RestaurantServiceClient

```java
@Service("restaurantService")
public class RestaurantServiceClient {

    private final RestTemplate restTemplate;
    private final String restaurantServiceUrl;

    public RestaurantServiceClient(@Value("${restaurant-service.url}") String url) {
        this.restTemplate = new RestTemplate();
        this.restaurantServiceUrl = url;
    }

    public Restaurant getRestaurant(Long id) {
        return restTemplate.getForObject(
                restaurantServiceUrl + "/api/restaurants/{id}",
                Restaurant.class, id);
    }

    public List<MenuItem> getMenuItemsByIds(List<Long> ids) { ... }
    public List<Restaurant> getAllRestaurants() { ... }
}
```

This is the **Strangler Fig** in action:
- Same method names as the old `RestaurantService`
- Under the hood: HTTP calls instead of database queries
- Callers (`OrderService`, web controllers) are unaware of the change

---

## Discussion Points

### 1. What happens when restaurant-service goes down?

Try it! Stop the restaurant service (Ctrl+C) while the monolith is running. Then:

```bash
curl http://localhost:8080/consumer/restaurants
```

You'll get a `500 Internal Server Error`. In a monolith, this failure mode doesn't exist -- all code runs in the same process. This is the **new cost of microservices**.

**How do we fix this?** Circuit Breaker pattern (coming in Iteration 3).

### 2. Why doesn't the monolith's @Transactional cover restaurant calls anymore?

In the original monolith, `OrderService.createOrder()` was a single `@Transactional` method. If the restaurant lookup failed, the whole order creation rolled back.

Now, the restaurant lookup is an HTTP call. If the HTTP call fails AFTER the order is saved, we have **partial state**. This is the fundamental challenge of distributed systems.

**How do we fix this?** Saga pattern (coming in Iteration 6).

### 3. What about latency?

In the monolith, `getRestaurant(id)` is a ~1ms database query. Now it's an HTTP call: DNS resolution + TCP connection + serialization + network round trip + deserialization. Could be 5-50ms.

For a training environment on localhost, this is negligible. In production, this is why you need:
- Connection pooling
- Caching (Redis, local)
- Async communication where possible (coming in Iteration 2)

---

## Exercise: Add a New Restaurant

Add a restaurant directly to the restaurant service and see it appear in the monolith UI:

```bash
curl -X POST http://localhost:8081/api/restaurants \
  -H "Content-Type: application/json" \
  -d '{"name": "Bangalore Bites", "address": "100 Brigade Road, Bangalore", "phone": "9876543213"}'
```

Now refresh http://localhost:8080/consumer/restaurants -- the new restaurant appears!

This demonstrates that the restaurant service is the **single source of truth**. The monolith just reads from it.

---

## Patterns Introduced

| Pattern | Where | Description |
|---------|-------|-------------|
| **Strangler Fig** | Overall approach | Monolith delegates to new service; old code is replaced incrementally |
| **Database per Service** | restaurant-service has `jdbc:h2:mem:restaurant` | Each service owns its data exclusively |
| **Service Proxy** | `RestaurantServiceClient` | Same interface, HTTP implementation replaces local DB calls |
| **API Gateway** (informal) | Monolith acts as gateway | Consumers still hit port 8080; monolith routes to 8081 internally |

---

## What's Next: Iteration 2

In Iteration 2, we extract the **Notification Service** and introduce:
- **Apache Kafka** for asynchronous messaging
- **Event-driven architecture** -- fire-and-forget notifications
- The monolith publishes an `OrderCreated` event; notification-service consumes it

This breaks the synchronous chain: the monolith no longer waits for notifications to complete before responding.
