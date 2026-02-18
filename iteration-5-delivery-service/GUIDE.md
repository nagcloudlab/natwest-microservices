# Iteration 5: Extract Delivery Service — Cross-Service Communication

## What Changed

| Component | Before (Iteration 4) | After (Iteration 5) |
|-----------|----------------------|----------------------|
| Delivery logic | Local `DeliveryService` in monolith | Standalone `delivery-service` on port 8085 |
| Delivery data | `delivery`, `courier` tables in monolith's H2 | Separate H2 database in delivery-service |
| Create delivery | Direct method call from `OrderService` | REST via `DeliveryServiceClient` |
| Status sync | `DeliveryService` directly updates `OrderRepository` | Kafka events (`delivery-events` topic) |
| Kitchen check | Via monolith's `KitchenServiceClient` | Delivery-service calls kitchen-service directly (REST) |
| Monolith's remaining modules | Order + Delivery | **Order only!** |

## Architecture

```
+--------------------+           +---------------------+
|   FTGO Monolith    |   REST    |   Delivery Service  |
|   (port 8080)      |---------->|   (port 8085, NEW)  |
|                    |           |   H2: delivery DB   |
|   Modules remaining:|           +----+----------+-----+
|   - Order (ONLY!)  |     Kafka:     |          |
|                    |  delivery-     | REST     |
|   H2: ftgo DB      |  events       |          |
|   (orders only)   |<--------+     |          |
+--------+-----------+               |          |
         |                            v          |
         |  REST          +---------------------+|
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
         |                +---------------------+
         |
         |  REST +        +---------------------+
         |  Kafka         |   Kitchen Service    |
         +--Bidirectional>|     (port 8084)      |
                          +----------^-----------+
                                     |
                          Delivery Service calls
                          Kitchen Service (REST)
                          to check ticket readiness
```

## Why Delivery Extraction Is the Most Complex

Previous extractions had **one-way** or **bidirectional** communication with the monolith only:
- Restaurant: Monolith → REST → Restaurant Service
- Notification: Monolith → Kafka → Notification Service
- Accounting: Monolith → REST → Accounting Service
- Kitchen: Monolith ↔ Kitchen Service (REST + Kafka)

Delivery has **three communication paths**:
1. **Monolith → Delivery (commands via REST):** Create delivery, assign courier, etc.
2. **Delivery → Monolith (events via Kafka):** Status changes update Order status
3. **Delivery → Kitchen (queries via REST):** Check if ticket is READY_FOR_PICKUP before allowing pickup

This is the first time an extracted service talks to **another extracted service** — not just to the monolith. This is called **cross-service communication** and is a hallmark of real microservice architectures.

## Key Concept: Cross-Service Communication

### Before (Monolith — Everything Local)
```java
// DeliveryService.java (OLD — in monolith)
@Autowired
private KitchenServiceClient kitchenService;  // Calls kitchen-service via monolith's client

@Autowired
private OrderRepository orderRepository;  // Direct DB dependency!

public Delivery markPickedUp(Long deliveryId) {
    // Check kitchen via monolith's client
    KitchenTicketResponse ticket = kitchenService.getTicketByOrderId(delivery.getOrderId());
    if (!"READY_FOR_PICKUP".equals(ticket.getStatus())) {
        throw new RuntimeException("Kitchen not ready!");
    }

    delivery.setStatus(DeliveryStatus.PICKED_UP);

    // Directly update Order — TIGHT COUPLING
    Order order = orderRepository.findById(delivery.getOrderId()).orElse(null);
    order.setStatus(OrderStatus.PICKED_UP);
    orderRepository.save(order);

    return deliveryRepository.save(delivery);
}
```

### After (Microservice — Cross-Service + Domain Events)
```java
// DeliveryService.java (NEW — in delivery-service)
@Autowired
private KitchenServiceClient kitchenServiceClient;  // Calls kitchen-service DIRECTLY (service-to-service)

@Autowired
private DeliveryEventPublisher eventPublisher;  // Kafka publisher
// NO OrderRepository — delivery-service doesn't know about Orders!

public Delivery markPickedUp(Long deliveryId) {
    // Cross-service call: delivery-service → kitchen-service (direct REST)
    if (!kitchenServiceClient.isReadyForPickup(delivery.getOrderId())) {
        throw new RuntimeException("Kitchen not ready!");
    }

    delivery.setStatus(DeliveryStatus.PICKED_UP);
    deliveryRepository.save(delivery);

    // Publish event — the monolith will update Order status
    eventPublisher.publishStatusChanged(
        new DeliveryStatusChangedEvent(delivery.getId(), delivery.getOrderId(), "PICKED_UP"));

    return delivery;
}
```

```java
// DeliveryStatusEventConsumer.java (in monolith — consumes the event)
@KafkaListener(topics = "delivery-events", groupId = "ftgo-monolith")
public void handleDeliveryStatusChanged(String message) {
    DeliveryStatusChangedEvent event = objectMapper.readValue(message, ...);
    Order order = orderRepository.findById(event.getOrderId()).orElse(null);
    order.setStatus(OrderStatus.PICKED_UP);  // React to event
    orderRepository.save(order);
}
```

## Multi-Topic Kafka Consumption

The monolith now consumes from **two** Kafka topics:

### Before (Iteration 4 — One Inbound Topic)
```
Monolith ──publishes──> order-events    ──> Notification Service
Monolith <──consumes── kitchen-events  <── Kitchen Service
```

### After (Iteration 5 — Two Inbound Topics)
```
Monolith ──publishes──> order-events    ──> Notification Service
Monolith <──consumes── kitchen-events  <── Kitchen Service
Monolith <──consumes── delivery-events <── Delivery Service
```

Each topic has its own `@KafkaListener` consumer class:
- `TicketStatusEventConsumer` → listens on `kitchen-events`
- `DeliveryStatusEventConsumer` → listens on `delivery-events`

Both use the same consumer group (`ftgo-monolith`) ensuring each event is processed exactly once per monolith instance.

## Kafka Events: delivery-events

| Event | Produced By | Consumed By | Effect |
|-------|------------|-------------|--------|
| `DeliveryStatusChangedEvent(PICKED_UP)` | delivery-service | monolith | Order → PICKED_UP |
| `DeliveryStatusChangedEvent(DELIVERED)` | delivery-service | monolith | Order → DELIVERED |

## Files Changed

### Delivery Service (NEW — port 8085)
| File | Purpose |
|------|---------|
| `DeliveryServiceApplication.java` | Spring Boot entry point |
| `Delivery.java` | JPA entity (copied from monolith) |
| `DeliveryStatus.java` | Enum (copied from monolith) |
| `DeliveryRepository.java` | JPA repository (copied from monolith, added `findByOrderId`) |
| `Courier.java` | JPA entity (copied from monolith) |
| `CourierRepository.java` | JPA repository (copied from monolith) |
| `DeliveryService.java` | Business logic — **publishes Kafka events instead of updating OrderRepository** |
| `DeliveryController.java` | REST API with POST (create), GET (query), PUT (assign/pickup/deliver) |
| `CreateDeliveryRequest.java` | Request DTO for delivery creation |
| `DeliveryStatusChangedEvent.java` | Kafka event DTO — the contract between services |
| `DeliveryEventPublisher.java` | Kafka producer for `delivery-events` topic |
| `KitchenServiceClient.java` | REST client for cross-service calls to kitchen-service |
| `application.properties` | Port 8085, H2 delivery DB, Kafka producer, kitchen-service URL |
| `data.sql` | Courier seed data (moved from monolith) |

### Monolith Changes
| Action | File | What Changed |
|--------|------|-------------|
| DELETE | `delivery/Delivery.java` | Moved to delivery-service |
| DELETE | `delivery/DeliveryStatus.java` | Moved to delivery-service |
| DELETE | `delivery/DeliveryRepository.java` | Moved to delivery-service |
| DELETE | `delivery/Courier.java` | Moved to delivery-service |
| DELETE | `delivery/CourierRepository.java` | Moved to delivery-service |
| DELETE | `delivery/DeliveryService.java` | Moved to delivery-service |
| DELETE | `delivery/DeliveryController.java` | Moved to delivery-service |
| CREATE | `delivery/DeliveryServiceClient.java` | REST proxy with @CircuitBreaker + timeout |
| CREATE | `delivery/DeliveryResponse.java` | DTO (status as String, not enum) |
| CREATE | `delivery/DeliveryStatusEventConsumer.java` | @KafkaListener on `delivery-events` topic |
| CREATE | `delivery/DeliveryStatusChangedEvent.java` | Event DTO matching delivery-service's version |
| MODIFY | `order/OrderService.java` | `DeliveryService` → `DeliveryServiceClient` |
| MODIFY | `web/CourierWebController.java` | `DeliveryService` → `DeliveryServiceClient`, use String status |
| MODIFY | `application.properties` | Delivery service URL, Resilience4j config, Kafka consumer |
| MODIFY | `data.sql` | Removed courier seed data (moved to delivery-service) |
| MODIFY | `templates/courier/deliveries.html` | `d.status.name()` → `d.status` (String not enum) |

## Running Everything

### 1. Start Infrastructure
```bash
# Start Kafka cluster (KRaft mode — no ZooKeeper, no Docker)
cd kafka && bash start-cluster.sh
# Brokers: localhost:9092, localhost:9093, localhost:9094

# Stop later with:
cd kafka && bash stop-cluster.sh
```

### 2. Start All 6 Services
```bash
# Terminal 1: Restaurant Service (port 8081)
cd iteration-5-delivery-service/restaurant-service && mvn spring-boot:run

# Terminal 2: Notification Service (port 8082)
cd iteration-5-delivery-service/notification-service && mvn spring-boot:run

# Terminal 3: Accounting Service (port 8083)
cd iteration-5-delivery-service/accounting-service && mvn spring-boot:run

# Terminal 4: Kitchen Service (port 8084)
cd iteration-5-delivery-service/kitchen-service && mvn spring-boot:run

# Terminal 5: Delivery Service (port 8085) — NEW
cd iteration-5-delivery-service/delivery-service && mvn spring-boot:run

# Terminal 6: Monolith (port 8080)
cd iteration-5-delivery-service/ftgo-monolith && mvn spring-boot:run
```

### 3. Test the Full Flow

**Place an order:**
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

**Verify delivery was created in delivery-service:**
```bash
curl http://localhost:8085/api/deliveries
# Should show a delivery with status PENDING for the order
```

**Kitchen workflow (kitchen-service):**
```bash
# Accept the ticket
curl -X PUT http://localhost:8084/api/kitchen/tickets/1/accept

# Start preparation (triggers Kafka event → monolith updates order to PREPARING)
curl -X PUT http://localhost:8084/api/kitchen/tickets/1/preparing

# Mark ready (triggers Kafka event → monolith updates order to READY_FOR_PICKUP)
curl -X PUT http://localhost:8084/api/kitchen/tickets/1/ready
```

**Delivery workflow (delivery-service):**
```bash
# Assign a courier
curl -X PUT http://localhost:8085/api/deliveries/1/assign

# Pick up (delivery-service checks kitchen-service ticket status via REST)
curl -X PUT http://localhost:8085/api/deliveries/1/pickup
# This triggers Kafka event → monolith updates order to PICKED_UP

# Deliver
curl -X PUT http://localhost:8085/api/deliveries/1/deliver
# This triggers Kafka event → monolith updates order to DELIVERED
```

**Verify final order status:**
```bash
curl http://localhost:8080/api/orders/1
# Should show status: "DELIVERED"
```

**Verify all supporting services:**
```bash
# Payment authorized (accounting-service)
curl http://localhost:8083/api/payments/order/1

# SMS sent (notification-service)
curl http://localhost:8082/api/notifications/order/1

# Kitchen ticket completed (kitchen-service)
curl http://localhost:8084/api/kitchen/tickets/order/1
```

### 4. End-to-End Flow Summary

```
Step  Service                      Action                          Order Status
----  ---------------------------  ------------------------------  ----------------
1     Monolith → Restaurant        Validate restaurant & menu      PENDING
2     Monolith → Accounting        Authorize payment (REST)        PENDING
3     Monolith → Kitchen           Create ticket (REST)            PENDING
4     Monolith → Delivery          Create delivery (REST)          PENDING
5     Monolith → Notification      Publish order event (Kafka)     APPROVED
6     Kitchen Service              Accept ticket                   APPROVED
7     Kitchen → Monolith           Start preparing (Kafka event)   PREPARING
8     Kitchen → Monolith           Mark ready (Kafka event)        READY_FOR_PICKUP
9     Delivery Service             Assign courier                  READY_FOR_PICKUP
10    Delivery → Kitchen           Check ticket ready (REST)       READY_FOR_PICKUP
11    Delivery → Monolith          Courier picks up (Kafka event)  PICKED_UP
12    Delivery → Monolith          Courier delivers (Kafka event)  DELIVERED
```

### 5. Circuit Breaker Test
```bash
# Kill delivery-service, then try to place an order
# The circuit breaker should trip and return a clear error
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "consumerId": 1,
    "consumerName": "Test",
    "consumerContact": "1234567890",
    "restaurantId": 1,
    "deliveryAddress": "Test Address",
    "paymentMethod": "UPI",
    "items": [{"menuItemId": 1, "quantity": 1}]
  }'
# Expected: "Delivery service is currently unavailable..."
```

## What's Left in the Monolith?

After 5 iterations of extraction, the monolith contains **only the Order module**:

| Module | Location | Communication |
|--------|----------|---------------|
| Restaurant | restaurant-service (8081) | REST |
| Notification | notification-service (8082) | Kafka (async) |
| Accounting | accounting-service (8083) | REST + Circuit Breaker |
| Kitchen | kitchen-service (8084) | REST + Kafka (bidirectional) |
| **Delivery** | **delivery-service (8085)** | **REST + Kafka (bidirectional) + cross-service** |
| Order | **monolith (8080)** | — |

The monolith is now effectively the **Order Service** — it only owns Order data and orchestrates the other services. The next step would be to promote the monolith to `order-service`, completing the microservice decomposition.

## Communication Patterns Summary

```
                     REST (sync)                    Kafka (async)
                     ──────────                    ─────────────
Restaurant Service   Monolith → Restaurant         —
Notification Service —                             Monolith → Notification
Accounting Service   Monolith → Accounting         —
Kitchen Service      Monolith → Kitchen            Kitchen → Monolith
Delivery Service     Monolith → Delivery           Delivery → Monolith
                     Delivery → Kitchen (cross-service!)
```

This table shows the evolution from simple one-way REST calls to complex bidirectional patterns with cross-service communication — a natural progression in microservice architectures.
