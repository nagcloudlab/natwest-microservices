# Distributed Transaction Management — Saga Pattern

## The Problem

In a microservices architecture, a single business operation (e.g., "place an order") often spans multiple services. Unlike a monolith where you can wrap everything in a single database transaction, each microservice has its own database. If one step fails mid-way, you're left with **partial state** — a payment authorized but no kitchen ticket, or a ticket created but no delivery scheduled.

**This is the distributed transaction problem.**

## The Solution: Saga Pattern

A **saga** is a sequence of local transactions where each step has a **compensating action** that undoes its effect if a later step fails.

There are two approaches to implementing sagas:

| Aspect | Choreography (Part A) | Orchestration (Part B) |
|--------|----------------------|----------------------|
| Coordination | Decentralized — services react to events | Centralized — one coordinator controls flow |
| Communication | Kafka events | REST calls (synchronous) |
| Coupling | Loose — services don't know about each other | Tighter — orchestrator knows all steps |
| Complexity | Distributed across services | Concentrated in orchestrator |
| Debugging | Harder — follow events across logs | Easier — single place to see flow |
| Best for | Simple flows, high autonomy | Complex flows, need for visibility |

## Saga Flow (Both Parts)

```
Happy Path:
  Place Order → Reserve Inventory → Process Payment → Approve Order

Failure Path (payment fails):
  Place Order → Reserve Inventory → Process Payment (FAILS!)
                                  → Release Inventory (compensate)
                                  → Reject Order
```

**Failure simulation:** Amounts > $100 trigger a payment decline (deterministic for live demo).

---

## Part A — Choreography (Event-Driven)

**Location:** `part-a-choreography/`

No central coordinator. Each service reacts to events and publishes new events.

### Event Flow

```
order-service publishes:    ORDER_CREATED            → [order-events]
inventory-service listens:  ORDER_CREATED            → reserves stock
inventory-service publishes: INVENTORY_RESERVED      → [inventory-events]
payment-service listens:    INVENTORY_RESERVED       → processes payment
payment-service publishes:  PAYMENT_PROCESSED        → [payment-events]
order-service listens:      PAYMENT_PROCESSED        → approves order

If payment fails:
payment-service publishes:  PAYMENT_FAILED           → [payment-events]
inventory-service listens:  PAYMENT_FAILED           → releases stock (compensation)
order-service listens:      PAYMENT_FAILED           → rejects order
```

### Kafka Topics

| Topic | Producer | Consumer |
|-------|----------|----------|
| `order-events` | order-service | inventory-service |
| `inventory-events` | inventory-service | payment-service |
| `payment-events` | payment-service | order-service, inventory-service |

### Key Takeaway

No service knows the full saga flow. Each just reacts to events it cares about. This is great for loose coupling but makes it hard to see the complete flow.

### Run It

```bash
cd part-a-choreography
docker compose up --build
```

**Happy path (amount < $100):**
```bash
curl -X POST localhost:9003/api/orders \
  -H "Content-Type: application/json" \
  -d '{"productId":"PIZZA","quantity":2,"amount":25.00}'
```

Wait a few seconds for events to propagate, then check the order:
```bash
curl localhost:9003/api/orders/1
# → status: APPROVED
```

**Failure path (amount > $100):**
```bash
curl -X POST localhost:9003/api/orders \
  -H "Content-Type: application/json" \
  -d '{"productId":"PIZZA","quantity":2,"amount":150.00}'
```

```bash
curl localhost:9003/api/orders/2
# → status: REJECTED
```

**Watch the event flow in logs:**
```bash
docker compose logs -f
```

You'll see the chain: ORDER_CREATED → INVENTORY_RESERVED → PAYMENT_FAILED → INVENTORY_RELEASED → Order REJECTED.

---

## Part B — Orchestration (Central Coordinator)

**Location:** `part-b-orchestration/`

A central `OrderSagaOrchestrator` in order-service controls the entire flow.

### How It Works

```java
// OrderSagaOrchestrator defines the saga as a list of steps:
Step 1: Reserve Inventory    → compensate: Release Inventory
Step 2: Process Payment      → compensate: Refund Payment

// Execute steps in order. If step N fails:
//   → Compensate steps N-1, N-2, ... 1 (reverse order)
```

### Key Classes

| Class | Purpose |
|-------|---------|
| `SagaStep` | Holds a forward action + compensating action (Runnable lambdas) |
| `SagaState` | Enum: STARTED, COMPLETED, COMPENSATING, FAILED |
| `OrderSagaOrchestrator` | Iterates steps, catches failures, compensates in reverse |

### REST Endpoints Used

| Step | Forward | Compensate |
|------|---------|------------|
| Inventory | `POST /api/inventory/reserve` | `POST /api/inventory/release/{orderId}` |
| Payment | `POST /api/payments/process` | `POST /api/payments/refund/{orderId}` |

### Run It

```bash
cd part-b-orchestration
docker compose up --build
```

**Happy path (amount < $100):**
```bash
curl -X POST localhost:9003/api/orders \
  -H "Content-Type: application/json" \
  -d '{"productId":"PIZZA","quantity":2,"amount":25.00}'
# → status: APPROVED
```

**Failure path (amount > $100):**
```bash
curl -X POST localhost:9003/api/orders \
  -H "Content-Type: application/json" \
  -d '{"productId":"PIZZA","quantity":2,"amount":150.00}'
# → status: REJECTED
```

**Watch the saga execution in logs:**
```bash
docker compose logs order-service
```

You'll see:
```
SAGA [Order 2] Starting saga execution
SAGA [Order 2] Step 1: Reserving inventory for PIZZA x2
SAGA [Order 2] Step 1: Inventory reserved successfully
SAGA [Order 2] Step 2: Processing payment of $150.00
SAGA [Order 2] Step 'Process Payment' FAILED: ...
SAGA [Order 2] COMPENSATING: Releasing inventory
SAGA [Order 2] COMPENSATED: Inventory released
SAGA [Order 2] Saga FAILED — all completed steps compensated
Order 2 REJECTED — saga compensation completed
```

---

## Comparison

| Aspect | Part A (Choreography) | Part B (Orchestration) |
|--------|----------------------|----------------------|
| Who controls the flow? | Nobody — each service reacts | `OrderSagaOrchestrator` |
| How do services communicate? | Kafka events (async) | REST calls (sync) |
| Where is the saga logic? | Spread across all 3 services | In one class |
| Response to caller | Eventual (order stays PENDING) | Immediate (APPROVED/REJECTED) |
| Infrastructure needed | Kafka | Nothing extra |
| Adding a new step | Modify multiple services | Add one line to orchestrator |
| Debugging | Follow events across logs | Read one class |

## When to Use Which?

**Choreography** — when services are truly independent, the flow is simple, and you want maximum decoupling.

**Orchestration** — when the flow is complex, you need immediate feedback, or you want clear visibility into what happened (audit log, saga state).

In practice, **orchestration is more common** for order-creation flows because you need to tell the user immediately whether their order succeeded.
