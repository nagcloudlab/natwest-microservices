# FTGO Monolith — Participant Guide

## What is this?

FTGO (Food To Go) is a food-delivery monolith: one Spring Boot app, one H2 database, one deploy. It lets consumers order food, restaurants manage kitchen tickets, and couriers deliver orders. This is our starting point before we decompose into microservices.

## How to run

```bash
cd ftgo-monolith
mvn spring-boot:run
```

Open [http://localhost:8080](http://localhost:8080) in your browser.
The H2 database console is at [http://localhost:8080/h2-console](http://localhost:8080/h2-console) (JDBC URL: `jdbc:h2:mem:ftgo`).

## Architecture

```
┌───────────────────────────────────────────────────────────────────┐
│                         Spring Boot JVM                           │
│                                                                   │
│  ┌─────────┐ ┌────────────┐ ┌─────────┐ ┌──────────┐              │
│  │  Order   │ │ Restaurant │ │ Kitchen │ │ Delivery │             │
│  └────┬─────┘ └────────────┘ └─────────┘ └─────┬────┘             │
│       │   ┌────────────┐ ┌──────────────┐       │                 │
│       │   │ Accounting │ │ Notification │       │                 │
│       │   └────────────┘ └──────────────┘       │                 │
│       │                                         │                 │
│       │    cross-module DB query ────────────────┘                │
│       │    (DeliveryService reads KitchenTicketRepository)        │
├───────┴───────────────────────────────────────────────────────────┤
│                     H2 Database (9 tables)                        │
│  restaurant · menu_item · orders · order_items · kitchen_ticket   │
│  delivery · courier · payment · notification                      │
└───────────────────────────────────────────────────────────────────┘
```

**Key traits:** 6 modules in 1 JVM, 9 tables in 1 DB, 1 `@Transactional` for order creation.

## Order lifecycle

### Creation (single `@Transactional` in `OrderService.createOrder()`)

| Step | Action | Module |
|------|--------|--------|
| 1 | Validate restaurant exists and is open | Restaurant |
| 2 | Validate menu items and get prices | Restaurant |
| 3 | Build order items and calculate total | Order |
| 4 | Save order (status → PENDING) | Order |
| 5 | Authorize payment (Mock Stripe) | Accounting |
| 6 | Create kitchen ticket (status → CREATED) | Kitchen |
| 7 | Create delivery (status → PENDING) | Delivery |
| 8 | Send SMS notification (Mock Twilio) | Notification |
| 9 | Approve order (status → APPROVED) | Order |

If **any** step fails, the entire transaction rolls back — this is the ACID guarantee you lose with microservices.

### Post-order flow

**Restaurant kitchen:**
```
CREATED → ACCEPTED → PREPARING → READY_FOR_PICKUP
```

**Courier delivery:**
```
PENDING → COURIER_ASSIGNED → PICKED_UP → DELIVERED
```

> **Important:** Courier pickup is blocked until the kitchen marks the ticket as `READY_FOR_PICKUP`. This is enforced via a cross-module DB query — `DeliveryService` reads `KitchenTicketRepository` directly.

## Web UI walkthrough

### 1. Consumer flow
1. Go to **Consumer → Browse Restaurants**
2. Pick an open restaurant (Mumbai Masala or Delhi Darbar)
3. Select menu items and quantities, click **Place Order**
4. See the order confirmation page
5. View all orders at **My Orders**

### 2. Restaurant flow
1. Go to **Restaurant → Kitchen Dashboard**
2. Click a restaurant to see its kitchen tickets
3. Walk each ticket through: **Accept → Preparing → Ready for Pickup**

### 3. Courier flow
1. Go to **Courier → Courier Dashboard**
2. Click a courier to see pending and assigned deliveries
3. **Assign** a pending delivery to yourself
4. Wait until the kitchen marks the ticket as ready, then **Pick Up**
5. Finally, **Deliver** — the courier becomes available again

## REST API reference

### Order (`/api/orders`)
| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/orders` | Create order (JSON body) |
| `GET` | `/api/orders` | List all orders |
| `GET` | `/api/orders/{id}` | Get order by ID |
| `PUT` | `/api/orders/{id}/cancel` | Cancel order |

### Restaurant (`/api/restaurants`)
| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/restaurants` | List restaurants |
| `GET` | `/api/restaurants/{id}` | Get restaurant |
| `GET` | `/api/restaurants/{id}/menu` | Get menu items |

### Kitchen (`/api/kitchen/tickets`)
| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/kitchen/tickets` | List tickets (optional `?status=`) |
| `PUT` | `/api/kitchen/tickets/{id}/accept` | Accept ticket |
| `PUT` | `/api/kitchen/tickets/{id}/preparing` | Start preparing |
| `PUT` | `/api/kitchen/tickets/{id}/ready` | Mark ready for pickup |

### Delivery (`/api/deliveries`)
| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/deliveries` | List deliveries |
| `PUT` | `/api/deliveries/{id}/assign` | Auto-assign available courier |
| `PUT` | `/api/deliveries/{id}/pickup` | Mark picked up |
| `PUT` | `/api/deliveries/{id}/deliver` | Mark delivered |

### Accounting (`/api/payments`)
| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/payments/order/{orderId}` | Get payment for order |

### Notification (`/api/notifications`)
| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/notifications/order/{orderId}` | Get notifications for order |

### Example: Create an order via curl

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "consumerId": 1,
    "restaurantId": 1,
    "deliveryAddress": "42 Worli Sea Face, Mumbai",
    "items": [
      {"menuItemId": 1, "quantity": 2},
      {"menuItemId": 4, "quantity": 3}
    ]
  }'
```

## Seed data

Loaded from `src/main/resources/data.sql` on every startup.

### Restaurants
| ID | Name | Address | Open? |
|----|------|---------|-------|
| 1 | Mumbai Masala | 123 MG Road, Mumbai | Yes |
| 2 | Delhi Darbar | 456 Connaught Place, Delhi | Yes |
| 3 | Chennai Spice | 789 Anna Salai, Chennai | No |

### Menu items
| ID | Restaurant | Item | Price |
|----|------------|------|-------|
| 1 | Mumbai Masala | Butter Chicken | ₹350 |
| 2 | Mumbai Masala | Paneer Tikka | ₹280 |
| 3 | Mumbai Masala | Chicken Biryani | ₹320 |
| 4 | Mumbai Masala | Garlic Naan | ₹60 |
| 5 | Delhi Darbar | Chole Bhature | ₹180 |
| 6 | Delhi Darbar | Dal Makhani | ₹220 |
| 7 | Delhi Darbar | Tandoori Chicken | ₹400 |
| 8 | Chennai Spice | Masala Dosa | ₹150 |
| 9 | Chennai Spice | Idli Sambar | ₹120 |

### Couriers
| ID | Name | Phone | Available? |
|----|------|-------|------------|
| 1 | Ravi Kumar | 9998887771 | Yes |
| 2 | Priya Sharma | 9998887772 | Yes |
| 3 | Amit Patel | 9998887773 | No |

## Key monolith traits (discussion points)

| Trait | What it means | Microservice impact |
|-------|---------------|---------------------|
| **Shared DB** | All 9 tables in one H2 instance | Each service will own its DB (Database per Service pattern) |
| **ACID transaction** | `createOrder()` wraps 5 modules in one `@Transactional` | Must use Saga pattern for distributed transactions |
| **Cross-module coupling** | `DeliveryService` queries `KitchenTicketRepository` directly | Must use API calls or events between services |
| **Simple deploy** | One JAR, `java -jar app.jar` | Need container orchestration (Docker, Kubernetes) |
| **Single failure domain** | One bug can bring down the whole app | Independent failure isolation per service |
| **Easy debugging** | One log file, one stack trace | Distributed tracing (Zipkin/Jaeger) needed |
