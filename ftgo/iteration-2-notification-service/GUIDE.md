# Iteration 2: Extract Notification Service (Async Kafka)

## What You'll Learn

1. **Event-Driven Architecture** -- Services communicate via events, not direct calls
2. **Apache Kafka** -- Distributed message broker for reliable async messaging
3. **Fire-and-Forget Pattern** -- The producer publishes and moves on; the consumer processes when ready
4. **Loose Coupling** -- The monolith doesn't know (or care) who consumes the events

---

## Architecture: Before & After

### Before (Iteration 1)

```
┌──────────────────────────────────┐     ┌──────────────────────────┐
│          MONOLITH :8080          │     │  RESTAURANT SERVICE :8081│
│                                  │REST │                          │
│  OrderService                    │────>│  /api/restaurants        │
│    │                             │     └──────────────────────────┘
│    ├── accountingService.pay()   │
│    ├── kitchenService.create()   │
│    ├── deliveryService.create()  │
│    └── notificationService ──────┼── SYNCHRONOUS (user waits!)
│         .sendOrderConfirmation() │
│                                  │
└──────────────────────────────────┘
```

The user waits for ALL steps to complete -- including the 300ms notification call.

### After (Iteration 2)

```
┌──────────────────────────────────┐     ┌──────────────────────────┐
│          MONOLITH :8080          │     │  RESTAURANT SERVICE :8081│
│                                  │REST │                          │
│  OrderService                    │────>│  /api/restaurants        │
│    │                             │     └──────────────────────────┘
│    ├── accountingService.pay()   │
│    ├── kitchenService.create()   │
│    ├── deliveryService.create()  │     ┌─────────────────┐
│    └── orderEventPublisher ──────┼────>│  KAFKA :9092     │
│         .publishOrderCreated()   │     │  topic:          │
│                                  │     │  "order-events"  │
│  User gets response IMMEDIATELY  │     └────────┬────────┘
└──────────────────────────────────┘              │
                                                  │ async consume
                                     ┌────────────┴─────────────┐
                                     │  NOTIFICATION SVC :8082  │
                                     │                          │
                                     │  OrderEventConsumer      │
                                     │  → Save notification     │
                                     │  → "Send" SMS (mock)     │
                                     │                          │
                                     │  Own DB: H2:mem:         │
                                     │          notification    │
                                     └──────────────────────────┘
```

The user gets their order confirmation INSTANTLY. Notification happens in the background.

---

## What Changed in the Monolith?

| File | Change | Why |
|------|--------|-----|
| `NotificationService.java` | **Deleted** | Replaced by Kafka event publishing |
| `Notification.java` | **Deleted** | Entity moved to notification-service |
| `NotificationRepository.java` | **Deleted** | Repository moved to notification-service |
| `NotificationController.java` | **Deleted** | API moved to notification-service |
| `NotificationType.java` | **Deleted** | Enum moved to notification-service |
| `MockTwilioNotificationGateway.java` | **Deleted** | Mock gateway moved to notification-service |
| `NotificationGateway.java` | **Deleted** | Interface moved to notification-service |
| `OrderCreatedEvent.java` | **New** | Kafka event DTO (the contract) |
| `OrderEventPublisher.java` | **New** | Publishes events to Kafka |
| `OrderService.java` | **Modified** | `notificationService.send()` → `orderEventPublisher.publish()` |
| `pom.xml` | **Modified** | Added `spring-kafka` dependency |
| `application.properties` | **Modified** | Added Kafka producer config |

---

## New Service: notification-service

| File | Purpose |
|------|---------|
| `NotificationServiceApplication.java` | Spring Boot app on port 8082 |
| `OrderEventConsumer.java` | `@KafkaListener` — consumes OrderCreated events |
| `OrderCreatedEvent.java` | Event DTO (same as monolith's — the contract) |
| `Notification.java` | JPA entity — owns notification data |
| `NotificationRepository.java` | JPA repository |
| `NotificationController.java` | REST API: `GET /api/notifications/order/{orderId}` |
| `NotificationType.java` | Enum: EMAIL, SMS, PUSH |

---

## How to Run

### Step 0: Start Kafka

Kafka must be running on `localhost:9092`. The simplest way:

**Option A: Docker (recommended)**
```bash
docker run -d --name kafka \
  -p 9092:9092 \
  -e KAFKA_CFG_NODE_ID=0 \
  -e KAFKA_CFG_PROCESS_ROLES=controller,broker \
  -e KAFKA_CFG_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093 \
  -e KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  -e KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT \
  -e KAFKA_CFG_CONTROLLER_QUORUM_VOTERS=0@localhost:9093 \
  -e KAFKA_CFG_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  bitnami/kafka:latest
```

**Option B: Local Kafka installation**
```bash
# Start Kafka with KRaft (no ZooKeeper needed in Kafka 3.3+)
kafka-server-start.sh config/kraft/server.properties
```

### Step 1: Start Restaurant Service (port 8081)

```bash
cd iteration-2-notification-service/restaurant-service
mvn spring-boot:run
```

### Step 2: Start Notification Service (port 8082)

```bash
cd iteration-2-notification-service/notification-service
mvn spring-boot:run
```

### Step 3: Start the Monolith (port 8080)

```bash
cd iteration-2-notification-service/ftgo-monolith
mvn spring-boot:run
```

### Step 4: Place an Order and Watch

**Terminal 1:** Watch notification-service logs for incoming events:
```bash
# Look for "Received OrderCreated event" in the notification-service console
```

**Terminal 2:** Place an order:
```bash
curl -X POST http://localhost:8080/consumer/orders \
  -d "restaurantId=1&consumerName=Alice&consumerContact=9999888877&deliveryAddress=42 MG Road&paymentMethod=CREDIT_CARD&menuItemIds=1&menuItemIds=2&quantities=1&quantities=2"
```

**What you'll see:**
1. Monolith returns the order confirmation **immediately**
2. A moment later, notification-service logs: `>>> Received OrderCreated event`
3. Then: `>>> Mock Twilio: Sending to 9999888877: Hi Alice, your order #1...`

### Step 5: Query Notifications

```bash
# All notifications (from notification-service)
curl http://localhost:8082/api/notifications

# Notifications for a specific order
curl http://localhost:8082/api/notifications/order/1
```

---

## Sync vs Async: The Key Difference

### Iteration 1 (Synchronous Notification)
```
User clicks "Place Order"
  → OrderService.createOrder()
    → restaurantService.getRestaurant()     ~5ms  (REST)
    → accountingService.authorizePayment()  ~200ms (Mock Stripe)
    → kitchenService.createTicket()         ~1ms
    → deliveryService.createDelivery()      ~1ms
    → notificationService.send()            ~300ms (Mock Twilio) ← USER WAITS!
  ← Response to user                        ~510ms total
```

### Iteration 2 (Asynchronous Notification)
```
User clicks "Place Order"
  → OrderService.createOrder()
    → restaurantService.getRestaurant()     ~5ms  (REST)
    → accountingService.authorizePayment()  ~200ms (Mock Stripe)
    → kitchenService.createTicket()         ~1ms
    → deliveryService.createDelivery()      ~1ms
    → orderEventPublisher.publish()         ~2ms  (Kafka) ← FIRE AND FORGET!
  ← Response to user                        ~210ms total (300ms faster!)

  ... meanwhile, in notification-service ...
    ← Kafka delivers OrderCreated event
    → Mock Twilio sends SMS                 ~300ms (but user doesn't wait)
```

---

## Discussion Points

### 1. What happens if notification-service is down?

Unlike iteration 1 (where the monolith would fail), here:
- The monolith **still works perfectly** -- the order is created
- The Kafka message sits in the `order-events` topic
- When notification-service comes back up, it processes ALL queued messages
- No notifications are lost!

**Try it:** Stop notification-service, place 3 orders, then restart it. Watch it process all 3.

### 2. What about message ordering?

Kafka guarantees order **within a partition**. Since we use `orderId` as the key, all events for the same order go to the same partition and are processed in order.

### 3. What if the notification fails to send?

Right now, we log the error and move on. In production, you'd use:
- **Dead Letter Queue (DLQ)** -- failed messages go to a separate topic for investigation
- **Retry with backoff** -- Spring Kafka supports automatic retry
- **Idempotency** -- ensure re-processing the same event doesn't send duplicate notifications

### 4. Could other services consume the same event?

Yes! That's the beauty of event-driven architecture. You could add:
- **Analytics Service** -- counts orders per restaurant per hour
- **Audit Service** -- records every order for compliance
- **Loyalty Service** -- awards points for each order

None of these require changes to the monolith. Just add more consumers to the topic.

---

## Patterns Introduced

| Pattern | Where | Description |
|---------|-------|-------------|
| **Event-Driven Architecture** | Kafka `order-events` topic | Services communicate via events, not direct calls |
| **Fire-and-Forget** | `OrderEventPublisher` | Producer publishes and immediately returns |
| **Async Messaging** | Spring Kafka | Decouples producer timing from consumer processing |
| **Event Contract** | `OrderCreatedEvent` | Shared schema between producer and consumer |

---

## Running Services at This Stage

```
Monolith          :8080   (Order, Kitchen, Delivery, Accounting)
Restaurant Service :8081  (Restaurant + Menu data)
Notification Service :8082 (Kafka consumer → SMS/Push)
Kafka             :9092   (Message broker)
```

4 processes total (vs 1 in the original monolith).

---

## What's Next: Iteration 3

In Iteration 3, we extract the **Accounting Service** and introduce:
- **Anti-Corruption Layer** -- translate between monolith and service models
- **Circuit Breaker (Resilience4j)** -- handle failures gracefully when the accounting service is down
- Unlike notification (fire-and-forget), payment is **critical** -- we can't just fire and forget!
