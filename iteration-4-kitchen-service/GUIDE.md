# Iteration 4: Extract Kitchen Service — Domain Events + Bidirectional Kafka

## What Changed

| Component | Before (Iteration 3) | After (Iteration 4) |
|-----------|----------------------|----------------------|
| Kitchen logic | Local `KitchenService` in monolith | Standalone `kitchen-service` on port 8084 |
| Kitchen data | `kitchen_ticket` table in monolith's H2 | Separate H2 database in kitchen-service |
| Command flow | Direct method call | REST via `KitchenServiceClient` |
| Status sync | `KitchenService` directly updates `OrderRepository` | Kafka events (`kitchen-events` topic) |
| Monolith's Kafka role | Producer only (order-events) | **Both producer AND consumer** |

## Architecture

```
+--------------------+           +---------------------+
|   FTGO Monolith    |   REST    |   Kitchen Service   |
|   (port 8080)      |---------->|   (port 8084, NEW)  |
|                    |           |   H2: kitchen DB    |
|   Modules remaining:|           +----------+----------+
|   - Order          |                      |
|   - Delivery       |    Kafka:            |
|                    |    kitchen-events     |
|   H2: ftgo DB      |<--------------------+
|   (order, delivery,|
|    courier)        |
+--------+-----------+
         |
         |  REST          +---------------------+
         +--------------->|  Restaurant Service  |
         |                |     (port 8081)      |
         |                +---------------------+
         |
         |  Kafka:        +---------------------+
         |  order-events  | Notification Service |
         +--------------->|     (port 8082)      |
         |                +---------------------+
         |
         |  REST +        +---------------------+
         |  Circuit       |  Accounting Service  |
         +--Breaker------>|     (port 8083)      |
                          +---------------------+
```

## Why Kitchen Extraction Is Harder

Previous extractions used **one-way** communication:
- Restaurant: Monolith → REST → Restaurant Service (one direction)
- Notification: Monolith → Kafka → Notification Service (one direction)
- Accounting: Monolith → REST → Accounting Service (one direction)

Kitchen has **bidirectional coupling**:
1. **Monolith → Kitchen (commands via REST):** Create ticket, accept, prepare, mark ready
2. **Kitchen → Monolith (events via Kafka):** Status changes update Order status

This means the monolith becomes both a Kafka **producer** and **consumer** — the first time this happens in our extraction journey.

## Key Concept: Domain Events

### Before (Monolith — Tight Coupling)
```java
// KitchenService.java (OLD — in monolith)
@Autowired
private OrderRepository orderRepository;  // Direct DB dependency!

public KitchenTicket startPreparation(Long ticketId) {
    ticket.setStatus(TicketStatus.PREPARING);

    // Kitchen directly modifies Order — TIGHT COUPLING
    Order order = orderRepository.findById(ticket.getOrderId()).orElse(null);
    order.setStatus(OrderStatus.PREPARING);
    orderRepository.save(order);  // Same transaction, same database

    return kitchenTicketRepository.save(ticket);
}
```

**Problems:**
- Kitchen module imports Order module classes
- Kitchen directly writes to Order's database table
- Can't deploy or scale them independently
- Single database = single point of failure

### After (Microservice — Domain Events)
```java
// KitchenService.java (NEW — in kitchen-service)
@Autowired
private TicketEventPublisher ticketEventPublisher;  // Kafka publisher!
// NO OrderRepository — Kitchen doesn't know about Orders

public KitchenTicket startPreparation(Long ticketId) {
    ticket.setStatus(TicketStatus.PREPARING);
    kitchenTicketRepository.save(ticket);

    // Publish event — let interested parties react
    ticketEventPublisher.publishStatusChanged(
        new TicketStatusChangedEvent(ticket.getId(), ticket.getOrderId(), "PREPARING"));

    return ticket;
}
```

```java
// TicketStatusEventConsumer.java (in monolith — consumes the event)
@KafkaListener(topics = "kitchen-events", groupId = "ftgo-monolith")
public void handleTicketStatusChanged(String message) {
    TicketStatusChangedEvent event = objectMapper.readValue(message, ...);
    Order order = orderRepository.findById(event.getOrderId()).orElse(null);
    order.setStatus(OrderStatus.PREPARING);  // React to event
    orderRepository.save(order);
}
```

**Benefits:**
- Kitchen-service has zero knowledge of Orders
- Communication is via a well-defined event contract
- Services can be deployed, scaled, and updated independently
- If the monolith is temporarily down, events queue in Kafka

## Eventual Consistency

With domain events, there's a small window where data is inconsistent:

```
Timeline:
  T0: Kitchen staff clicks "Start Preparing"
  T1: kitchen-service updates ticket to PREPARING
  T2: kitchen-service publishes TicketStatusChanged event to Kafka
  T3: Kafka delivers event to monolith (typically <100ms)
  T4: monolith updates Order to PREPARING

  Between T1 and T4: ticket is PREPARING, but order still shows APPROVED
  This is "eventual consistency" — the data converges, but not instantly
```

### Why This Is Acceptable
- The inconsistency window is typically < 100ms
- The UI auto-refreshes every 15 seconds
- No business decision depends on instant consistency here
- This is how all large-scale distributed systems work (Amazon, Netflix, etc.)

### When Eventual Consistency Is NOT Acceptable
- Payment authorization — we CANNOT approve an order without confirmed payment
- That's why Accounting Service uses synchronous REST, not async Kafka
- The choice of sync vs async depends on the business requirement

## Bidirectional Kafka

### Before (Iteration 2 — Monolith is Producer Only)
```
Monolith ──publishes──> order-events ──> Notification Service
```

### After (Iteration 4 — Monolith is Both)
```
Monolith ──publishes──> order-events   ──> Notification Service
Monolith <──consumes── kitchen-events <── Kitchen Service
```

### Configuration in monolith's `application.properties`
```properties
# PRODUCER config (from iteration 2)
spring.kafka.producer.key-serializer=...StringSerializer
spring.kafka.producer.value-serializer=...StringSerializer

# CONSUMER config (new in iteration 4)
spring.kafka.consumer.group-id=ftgo-monolith
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.key-deserializer=...StringDeserializer
spring.kafka.consumer.value-deserializer=...StringDeserializer
```

Spring Kafka supports both producer and consumer in the same application out of the box. The `group-id` ensures that if you run multiple monolith instances, each event is processed by only one instance.

## Files Changed

### Kitchen Service (NEW — port 8084)
| File | Purpose |
|------|---------|
| `KitchenServiceApplication.java` | Spring Boot entry point |
| `KitchenTicket.java` | JPA entity (copied from monolith) |
| `TicketStatus.java` | Enum (copied from monolith) |
| `KitchenTicketRepository.java` | JPA repository (copied from monolith) |
| `KitchenService.java` | Business logic — **publishes Kafka events instead of updating OrderRepository** |
| `KitchenController.java` | REST API with POST (create), GET (query), PUT (accept/prepare/ready) |
| `CreateTicketRequest.java` | Request DTO for ticket creation |
| `TicketStatusChangedEvent.java` | Kafka event DTO — the contract between services |
| `TicketEventPublisher.java` | Kafka producer for `kitchen-events` topic |

### Monolith Changes
| Action | File | What Changed |
|--------|------|-------------|
| DELETE | `kitchen/KitchenService.java` | Moved to kitchen-service |
| DELETE | `kitchen/KitchenTicket.java` | Moved to kitchen-service |
| DELETE | `kitchen/TicketStatus.java` | Moved to kitchen-service |
| DELETE | `kitchen/KitchenTicketRepository.java` | Moved to kitchen-service |
| DELETE | `kitchen/KitchenController.java` | Moved to kitchen-service |
| CREATE | `kitchen/KitchenServiceClient.java` | REST proxy with @CircuitBreaker + timeout |
| CREATE | `kitchen/KitchenTicketResponse.java` | DTO (status is String, not enum) |
| CREATE | `kitchen/TicketStatusEventConsumer.java` | @KafkaListener on `kitchen-events` topic |
| CREATE | `kitchen/TicketStatusChangedEvent.java` | Event DTO matching kitchen-service's version |
| MODIFY | `order/OrderService.java` | `KitchenService` → `KitchenServiceClient` |
| MODIFY | `web/RestaurantWebController.java` | `KitchenService` → `KitchenServiceClient` |
| MODIFY | `delivery/DeliveryService.java` | `KitchenTicketRepository` → `KitchenServiceClient` (REST) |
| MODIFY | `application.properties` | Kitchen service URL, Kafka consumer config, Resilience4j |
| MODIFY | `templates/restaurant/tickets.html` | `t.status.name()` → `t.status` (String not enum) |

## Running Everything

### 1. Start Infrastructure
```bash
# Start Kafka cluster (from project root)
cd kafka && docker-compose up -d
```

### 2. Start All Services
```bash
# Terminal 1: Restaurant Service (port 8081)
cd iteration-4-kitchen-service/restaurant-service && mvn spring-boot:run

# Terminal 2: Notification Service (port 8082)
cd iteration-4-kitchen-service/notification-service && mvn spring-boot:run

# Terminal 3: Accounting Service (port 8083)
cd iteration-4-kitchen-service/accounting-service && mvn spring-boot:run

# Terminal 4: Kitchen Service (port 8084) — NEW
cd iteration-4-kitchen-service/kitchen-service && mvn spring-boot:run

# Terminal 5: Monolith (port 8080)
cd iteration-4-kitchen-service/ftgo-monolith && mvn spring-boot:run
```

### 3. Test the Full Flow

**Place an order (monolith → kitchen-service via REST):**
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "consumerId": 1,
    "consumerName": "Rahul",
    "consumerContact": "9876543210",
    "restaurantId": 1,
    "deliveryAddress": "123 Main St, Mumbai",
    "paymentMethod": "UPI",
    "items": [{"menuItemId": 1, "quantity": 2}]
  }'
```

**Verify ticket was created in kitchen-service:**
```bash
curl http://localhost:8084/api/kitchen/tickets
```

**Accept the ticket (kitchen-service):**
```bash
curl -X PUT http://localhost:8084/api/kitchen/tickets/1/accept
```

**Start preparation (triggers Kafka event → monolith updates order):**
```bash
curl -X PUT http://localhost:8084/api/kitchen/tickets/1/preparing
```

**Verify order status updated in monolith:**
```bash
curl http://localhost:8080/api/orders/1
# Should show status: "PREPARING"
```

**Mark ready (triggers Kafka event → monolith updates order):**
```bash
curl -X PUT http://localhost:8084/api/kitchen/tickets/1/ready
```

**Verify final order status:**
```bash
curl http://localhost:8080/api/orders/1
# Should show status: "READY_FOR_PICKUP"
```

### 4. Circuit Breaker Test
```bash
# Kill kitchen-service, then try to place an order
# The circuit breaker should trip and return a clear error
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{...}'
# Expected: "Kitchen service is currently unavailable..."
```

## Exercises

### Exercise 1: Add Event Timestamps
Add a `timestamp` field to `TicketStatusChangedEvent`. In the monolith's consumer, log the delay between event creation and processing to measure eventual consistency latency.

### Exercise 2: Dead Letter Queue
What happens if the monolith's Kafka consumer fails to process an event? Implement a dead letter topic (`kitchen-events-dlq`) for failed events.

### Exercise 3: Idempotent Consumer
If the same event is delivered twice (Kafka "at least once" delivery), the order status gets set twice. Implement idempotency by tracking processed event IDs.

### Exercise 4: Event Sourcing Preview
Currently, we only publish events for PREPARING and READY_FOR_PICKUP. Extend the system to publish events for ALL status changes (CREATED, ACCEPTED, PREPARING, READY_FOR_PICKUP). Then build a simple event log endpoint that shows the full history of a ticket.

### Exercise 5: Saga Preview
Currently, if kitchen-service is down when placing an order, the order is rejected. Design (don't implement) a Saga that would:
1. Save the order as PENDING
2. Try to create the kitchen ticket
3. If kitchen-service is down, keep the order as PENDING and retry later
4. When the ticket is eventually created, move the order to APPROVED

## What's Left in the Monolith?

After 4 iterations of extraction:

| Module | Location | Communication |
|--------|----------|---------------|
| Restaurant | restaurant-service (8081) | REST |
| Notification | notification-service (8082) | Kafka (async) |
| Accounting | accounting-service (8083) | REST + Circuit Breaker |
| **Kitchen** | **kitchen-service (8084)** | **REST + Kafka (bidirectional)** |
| Order | **monolith** | — |
| Delivery | **monolith** | — |

Next iteration: Extract Delivery Service — the last module before the monolith becomes just the Order service.
