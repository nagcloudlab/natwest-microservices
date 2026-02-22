# Iteration 13: Distributed Transaction Management (Saga Pattern)

## What Problem Does This Solve?

After iteration 12, we have a fully observable microservices system with tracing and centralized logging. But there's a critical correctness problem lurking in `OrderService.createOrder()`:

```
Current flow (iterations 5-12):

  Step 5: Authorize Payment ✓   (money charged)
  Step 6: Create Kitchen Ticket ✓  (ticket created)
  Step 7: Create Delivery ✗    (delivery-service DOWN!)

Result:
  - Customer's money is charged ← NOT refunded!
  - Kitchen ticket exists ← NOT cancelled!
  - Delivery doesn't exist
  - Order is stuck in PENDING state

This is DATA INCONSISTENCY across services.
```

In a monolith, wrapping these steps in a single `@Transactional` would roll everything back. But in microservices, each service has its own database — there is no single transaction that spans all of them.

**Problem:** Sequential REST calls with no compensation logic. If any downstream service fails mid-flow, earlier services retain their state (payment charged, ticket created) with no rollback.

**Solution:** Implement the **Saga pattern** — an orchestration-based saga where each step has a compensating action. If step N fails, steps N-1, N-2, ... 1 are compensated in reverse order (refund payment, cancel ticket, cancel delivery).

## What Changed

| Component | Before (Iteration 12) | After (Iteration 13) |
|-----------|----------------------|----------------------|
| Order creation | Sequential REST, no compensation | Orchestration saga with compensation |
| accounting-service | Only `POST /authorize` | Adds `POST /refund/{orderId}` |
| kitchen-service | No cancel endpoint, no CANCELLED status | Adds `PUT /tickets/order/{orderId}/cancel`, CANCELLED status |
| delivery-service | No cancel endpoint, no CANCELLED status | Adds `PUT /deliveries/order/{orderId}/cancel`, CANCELLED status |
| order-service | `OrderService.createOrder()` with sequential REST | New `saga/` package with `OrderSagaOrchestrator` |
| Observability | No saga visibility | `saga_log` table + `GET /orders/{id}/saga-log` endpoint |
| Kafka topics | 3 topics | Adds `saga-events` topic |

## Architecture

```
Before (Iteration 12):                          After (Iteration 13):

OrderService.createOrder():                      OrderService.createOrder():

  Step 5: authorizePayment()                       Step 5: sagaOrchestrator.execute()
  Step 6: createTicket()                                    │
  Step 7: createDelivery()                                  ├── Step 1: authorizePayment()
  Step 8: publishEvent()                                    │     compensate: refundPayment()
  Step 9: approve()                                         ├── Step 2: createTicket()
                                                            │     compensate: cancelTicket()
  If step 7 fails:                                          ├── Step 3: createDelivery()
    Payment charged ← LEAKED!                               │     compensate: cancelDelivery()
    Ticket exists   ← LEAKED!                               │
    No delivery                                             └── If any step fails:
    Order stuck                                                   → Compensate in REVERSE order
                                                                  → Log every step to saga_log
                                                                  → Publish to saga-events topic
                                                                  → Return FAILED → order REJECTED
```

## Key Files

### New Files (order-service)

| File | Purpose |
|------|---------|
| `saga/OrderSagaOrchestrator.java` | Central coordinator — defines steps + compensation |
| `saga/SagaStep.java` | Step definition (name + forward + compensate) |
| `saga/SagaState.java` | Enum: STARTED, COMPLETED, COMPENSATING, FAILED |
| `saga/SagaLog.java` | JPA entity for saga execution log |
| `saga/SagaLogRepository.java` | JPA repository |
| `saga/SagaEventPublisher.java` | Publishes to saga-events Kafka topic |

### Modified Files

| File | Change |
|------|--------|
| `accounting-service/AccountingController.java` | Added `POST /api/payments/refund/{orderId}` |
| `accounting-service/AccountingService.java` | Added `refundPayment()` method |
| `accounting-service/PaymentGateway.java` | Added `refund()` method to interface |
| `accounting-service/MockStripePaymentGateway.java` | Implemented `refund()` |
| `kitchen-service/TicketStatus.java` | Added `CANCELLED` |
| `kitchen-service/KitchenController.java` | Added `PUT /api/kitchen/tickets/order/{orderId}/cancel` |
| `kitchen-service/KitchenService.java` | Added `cancelTicketByOrderId()` |
| `delivery-service/DeliveryStatus.java` | Added `CANCELLED` |
| `delivery-service/DeliveryController.java` | Added `PUT /api/deliveries/order/{orderId}/cancel` |
| `delivery-service/DeliveryService.java` | Added `cancelDeliveryByOrderId()` |
| `order-service/AccountingServiceClient.java` | Added `refundPayment()` |
| `order-service/KitchenServiceClient.java` | Added `cancelTicket()` |
| `order-service/DeliveryServiceClient.java` | Added `cancelDelivery()` |
| `order-service/OrderService.java` | Replaced sequential REST with `sagaOrchestrator.execute()` |
| `order-service/OrderController.java` | Added `GET /{id}/saga-log` endpoint |

## How It Works

### The Saga Orchestrator Pattern

The orchestrator defines the saga as a list of `SagaStep` objects, each with:
- **forwardAction**: the business operation (e.g., authorize payment)
- **compensatingAction**: the undo operation (e.g., refund payment)

```java
// Simplified view of OrderSagaOrchestrator.execute()
List<SagaStep> steps = List.of(
    new SagaStep("Authorize Payment",
        () -> accountingService.authorizePayment(...),      // forward
        () -> accountingService.refundPayment(orderId)),    // compensate

    new SagaStep("Create Kitchen Ticket",
        () -> kitchenService.createTicket(...),
        () -> kitchenService.cancelTicket(orderId)),

    new SagaStep("Create Delivery",
        () -> deliveryService.createDelivery(...),
        () -> deliveryService.cancelDelivery(orderId))
);

// Execute forward. On failure, compensate in reverse.
```

### Compensation Endpoints

Each downstream service now has an "undo" endpoint:

| Service | Forward Endpoint | Compensation Endpoint |
|---------|-----------------|----------------------|
| accounting-service | `POST /api/payments/authorize` | `POST /api/payments/refund/{orderId}` |
| kitchen-service | `POST /api/kitchen/tickets` | `PUT /api/kitchen/tickets/order/{orderId}/cancel` |
| delivery-service | `POST /api/deliveries` | `PUT /api/deliveries/order/{orderId}/cancel` |

All compensation endpoints are **idempotent** — calling them multiple times is safe (e.g., refunding an already-refunded payment returns the existing refund).

### Saga Log

Every saga step is logged to the `saga_log` table:

```
| id | orderId | stepName          | action     | status  | details                        | timestamp           |
|----|---------|-------------------|------------|---------|--------------------------------|---------------------|
| 1  | 42      | SAGA              | START      | SUCCESS | Starting order creation saga    | 2024-01-15 10:30:00 |
| 2  | 42      | Authorize Payment | FORWARD    | SUCCESS | Step 1 completed successfully   | 2024-01-15 10:30:01 |
| 3  | 42      | Create Kitchen Ticket | FORWARD | SUCCESS | Step 2 completed successfully | 2024-01-15 10:30:01 |
| 4  | 42      | Create Delivery   | FORWARD    | FAILED  | Step 3 failed: service down    | 2024-01-15 10:30:02 |
| 5  | 42      | SAGA              | COMPENSATING | IN_PROGRESS | Compensating 2 step(s)    | 2024-01-15 10:30:02 |
| 6  | 42      | Create Kitchen Ticket | COMPENSATE | SUCCESS | Compensation completed     | 2024-01-15 10:30:02 |
| 7  | 42      | Authorize Payment | COMPENSATE | SUCCESS | Compensation completed         | 2024-01-15 10:30:03 |
| 8  | 42      | SAGA              | END        | FAILED  | Saga failed — all compensated  | 2024-01-15 10:30:03 |
```

## Running the System

### Start Everything

```bash
cd iteration-13-distributed-txn
docker compose up --build
```

### Happy Path — All Services Up

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "consumerId": 1,
    "consumerName": "Alice",
    "consumerContact": "9876543210",
    "restaurantId": 1,
    "deliveryAddress": "123 Main St",
    "paymentMethod": "VISA",
    "items": [{"menuItemId": 1, "quantity": 2}]
  }'
```

Expected response: `"status": "APPROVED"`

### View the Saga Log

```bash
curl http://localhost:8080/api/orders/1/saga-log | jq
```

You'll see all saga steps logged with timestamps:
```
SAGA → START → SUCCESS
Authorize Payment → FORWARD → SUCCESS
Create Kitchen Ticket → FORWARD → SUCCESS
Create Delivery → FORWARD → SUCCESS
SAGA → END → COMPLETED
```

### Failure Path — Simulate Service Failure

Stop the delivery service while the system is running:

```bash
docker compose stop delivery-service
```

Place another order:

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "consumerId": 1,
    "consumerName": "Bob",
    "consumerContact": "9876543210",
    "restaurantId": 1,
    "deliveryAddress": "456 Oak Ave",
    "paymentMethod": "VISA",
    "items": [{"menuItemId": 1, "quantity": 1}]
  }'
```

Expected response: `"status": "REJECTED"`

View the saga log to see compensation:

```bash
curl http://localhost:8080/api/orders/2/saga-log | jq
```

```
SAGA → START → SUCCESS
Authorize Payment → FORWARD → SUCCESS
Create Kitchen Ticket → FORWARD → SUCCESS
Create Delivery → FORWARD → FAILED (delivery service unavailable)
SAGA → COMPENSATING → IN_PROGRESS
Create Kitchen Ticket → COMPENSATE → SUCCESS (ticket cancelled)
Authorize Payment → COMPENSATE → SUCCESS (payment refunded)
SAGA → END → FAILED
```

Verify the payment was refunded:

```bash
curl http://localhost:8083/api/payments/order/2 | jq '.status'
# → "REFUNDED"
```

Verify the ticket was cancelled:

```bash
curl http://localhost:8084/api/kitchen/tickets/order/2 | jq '.status'
# → "CANCELLED"
```

Restart delivery service:

```bash
docker compose start delivery-service
```

### Stop Everything

```bash
docker compose down
```

## Teaching Concepts

### Why Not Just Use 2PC (Two-Phase Commit)?

Two-phase commit (2PC) is the traditional distributed transaction protocol. It doesn't work well in microservices because:
- Requires all participants to be available simultaneously
- Holds locks across services — terrible for performance
- Any participant failure blocks the entire transaction
- Not supported across heterogeneous databases/services

Sagas are **eventually consistent** — they accept that partial states exist briefly during execution, but guarantee that compensating actions will clean up on failure.

### Choreography vs Orchestration

| Aspect | Choreography | Orchestration |
|--------|-------------|---------------|
| Coordinator | None — services react to events | Central orchestrator |
| Communication | Async events (Kafka) | Sync REST calls |
| Where is saga logic? | Distributed across all services | One class |
| Response to caller | Eventual (order stays PENDING) | Immediate (APPROVED/REJECTED) |
| Debugging | Follow events across logs | Read orchestrator logs |
| Adding new step | Modify multiple services | Add one line |

We chose **orchestration** for FTGO because:
- Order creation needs immediate feedback (APPROVED/REJECTED)
- The saga_log provides full visibility into what happened
- Complex flows (3+ steps with compensation) are easier to reason about centrally

### Idempotent Compensation

Compensation endpoints must be **idempotent** — safe to call multiple times. Why?
- Network failures may cause the orchestrator to retry compensations
- A service might have processed the compensation but the response was lost
- All our compensation endpoints check the current state before acting

### Observability: Saga Log + Kafka Events

Two observability mechanisms:
1. **saga_log table** — fine-grained step-by-step log, queryable via REST
2. **saga-events Kafka topic** — high-level saga completion/failure events for monitoring
