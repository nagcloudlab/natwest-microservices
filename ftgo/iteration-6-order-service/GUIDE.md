# Iteration 6: Promote to Order Service + React/Next.js SPA Frontend

## What Changed

| Component | Before (Iteration 5) | After (Iteration 6) |
|-----------|----------------------|----------------------|
| Monolith | `ftgo-monolith` with Thymeleaf UI + REST API | **Gone** — renamed to `order-service` (pure REST API) |
| Order logic | Embedded in monolith | Standalone `order-service` on port 8080 |
| Web UI | Server-rendered Thymeleaf templates (10 files) | React/Next.js SPA (`ftgo-web`, port 3000) |
| API access | Browser → monolith (server-side rendering) | Browser → Next.js API routes (BFF) → microservices |
| Web controllers | 4 Thymeleaf controllers | **Removed** — no server-side rendering |
| Thymeleaf dependency | In pom.xml | **Removed** |

**The monolith is gone. All 6 modules are now standalone microservices.**

## Architecture

```
                    +---------------------+
                    |    ftgo-web (SPA)    |
                    |    Next.js :3000     |
                    |                     |
                    |  Pages:             |
                    |  - Consumer          |
                    |  - Restaurant        |
                    |  - Courier           |
                    +----+----+----+------+
                         |    |    |
              API Routes (BFF proxy)
                    |    |    |    |
         +----------+   |    |    +----------+
         |              |    |               |
         v              v    v               v
+----------------+ +----------------+ +----------------+
| Order Service  | | Restaurant Svc | | Kitchen Service|
|   :8080        | |   :8081        | |   :8084        |
+----------------+ +----------------+ +----------------+
         |                                    ^
         |              +----------------+    |
         +--REST------->| Delivery Svc   |----+
         |              |   :8085        | (cross-service)
         |              +----------------+
         |
         |  Kafka       +----------------+   +----------------+
         +--order------>| Notification   |   | Accounting Svc |
            events      |   :8082        |   |   :8083        |
                        +----------------+   +----------------+
```

## Key Teaching Concept: Backend For Frontend (BFF) Pattern

The Next.js API routes act as a **BFF** — a server-side layer between the browser and the microservices:

```
Browser ──fetch──> Next.js API Route ──HTTP──> Microservice
                   (server-side)               (backend)
```

**Why BFF?**
- **No CORS issues** — the browser only talks to `localhost:3000`
- **Service aggregation** — one API route can call multiple services
- **Security** — backend service URLs are never exposed to the browser
- **Rate limiting** — can add throttling at the BFF layer
- **Data transformation** — can reshape responses for the frontend's needs

**BFF Proxy Routing:**

| Next.js API Route | Proxies To | Service |
|-------------------|-----------|---------|
| `/api/orders` | `:8080/api/orders` | Order Service |
| `/api/orders/[id]` | `:8080/api/orders/{id}` | Order Service |
| `/api/orders/[id]/cancel` | `:8080/api/orders/{id}/cancel` | Order Service |
| `/api/restaurants` | `:8081/api/restaurants` | Restaurant Service |
| `/api/restaurants/[id]` | `:8081/api/restaurants/{id}` | Restaurant Service |
| `/api/restaurants/[id]/menu` | `:8081/api/restaurants/{id}/menu` | Restaurant Service |
| `/api/kitchen/tickets` | `:8084/api/kitchen/tickets` | Kitchen Service |
| `/api/kitchen/tickets/[id]/accept` | `:8084/api/kitchen/tickets/{id}/accept` | Kitchen Service |
| `/api/kitchen/tickets/[id]/preparing` | `:8084/api/kitchen/tickets/{id}/preparing` | Kitchen Service |
| `/api/kitchen/tickets/[id]/ready` | `:8084/api/kitchen/tickets/{id}/ready` | Kitchen Service |
| `/api/deliveries` | `:8085/api/deliveries` | Delivery Service |
| `/api/deliveries/[id]/assign/[courierId]` | `:8085/api/deliveries/{id}/assign/{courierId}` | Delivery Service |
| `/api/deliveries/[id]/pickup` | `:8085/api/deliveries/{id}/pickup` | Delivery Service |
| `/api/deliveries/[id]/deliver` | `:8085/api/deliveries/{id}/deliver` | Delivery Service |
| `/api/deliveries/courier/[id]` | `:8085/api/deliveries/courier/{id}` | Delivery Service |
| `/api/couriers` | `:8085/api/deliveries/couriers` | Delivery Service |
| `/api/couriers/[id]` | `:8085/api/deliveries/couriers/{id}` | Delivery Service |
| `/api/payments/order/[id]` | `:8083/api/payments/order/{id}` | Accounting Service |
| `/api/notifications/order/[id]` | `:8082/api/notifications/order/{id}` | Notification Service |

## Files Changed

### Order Service (renamed from `ftgo-monolith/`)

| Action | File | Purpose |
|--------|------|---------|
| **Renamed** | `FtgoMonolithApplication.java` → `OrderServiceApplication.java` | New entry point |
| **Removed** | `web/HomeController.java` | Thymeleaf controller |
| **Removed** | `web/ConsumerWebController.java` | Thymeleaf controller |
| **Removed** | `web/RestaurantWebController.java` | Thymeleaf controller |
| **Removed** | `web/CourierWebController.java` | Thymeleaf controller |
| **Removed** | `templates/` (10 files) | All Thymeleaf templates |
| **Modified** | `pom.xml` | Removed `spring-boot-starter-thymeleaf`, renamed artifact |
| **Modified** | `application.properties` | Updated app name, consumer group |
| **Modified** | `OrderController.java` | Added `@CrossOrigin` for direct API testing |

**Files kept (22 Java files):**
- `order/` — Order, OrderService, OrderController, DTOs (9 files)
- `restaurant/` — RestaurantServiceClient, Restaurant, MenuItem (3 files)
- `accounting/` — AccountingServiceClient, PaymentResponse (2 files)
- `kitchen/` — KitchenServiceClient, KitchenTicketResponse, TicketStatusEventConsumer, TicketStatusChangedEvent (4 files)
- `delivery/` — DeliveryServiceClient, DeliveryResponse, DeliveryStatusEventConsumer, DeliveryStatusChangedEvent (4 files)
- `event/` — OrderEventPublisher, OrderCreatedEvent (2 files)
- `config/` — AsyncConfig (1 file)

### Next.js SPA (`ftgo-web/`)

| Category | Files | Purpose |
|----------|-------|---------|
| Config | `package.json`, `next.config.js`, `tsconfig.json`, `tailwind.config.ts`, `postcss.config.js`, `.env.local` | Project setup |
| Layout | `app/layout.tsx`, `app/globals.css` | Root layout + Tailwind CSS |
| Home | `app/page.tsx` | Role selection (Consumer/Restaurant/Courier) |
| Consumer | `app/consumer/restaurants/page.tsx` | Browse restaurants |
| Consumer | `app/consumer/restaurants/[id]/menu/page.tsx` | View menu + place order |
| Consumer | `app/consumer/orders/page.tsx` | My orders list |
| Consumer | `app/consumer/orders/[id]/page.tsx` | Order detail + tracking |
| Restaurant | `app/restaurant/page.tsx` | Restaurant dashboard |
| Restaurant | `app/restaurant/[id]/tickets/page.tsx` | Kitchen ticket management |
| Courier | `app/courier/page.tsx` | Courier dashboard |
| Courier | `app/courier/[id]/deliveries/page.tsx` | Delivery management |
| Components | `Navbar.tsx`, `OrderStatusBadge.tsx`, `WorkflowStepper.tsx`, `RestaurantCard.tsx`, `AutoRefresh.tsx` | Reusable UI components |
| Lib | `lib/api.ts` | API client (types + fetch helpers) |
| Lib | `lib/proxy.ts` | BFF proxy utilities |
| API Routes | 18 route files under `app/api/` | BFF proxy to all 6 services |

## Running the Application

### Prerequisites
- Java 17+, Maven
- Node.js 18+, npm
- Kafka cluster (3 brokers)

### 1. Start Kafka
```bash
cd kafka && bash start-cluster.sh
```

### 2. Start All 6 Backend Services

Open 6 separate terminals:

```bash
# Terminal 1 — Order Service (port 8080)
cd iteration-6-order-service/order-service
mvn spring-boot:run

# Terminal 2 — Restaurant Service (port 8081)
cd iteration-6-order-service/restaurant-service
mvn spring-boot:run

# Terminal 3 — Notification Service (port 8082)
cd iteration-6-order-service/notification-service
mvn spring-boot:run

# Terminal 4 — Accounting Service (port 8083)
cd iteration-6-order-service/accounting-service
mvn spring-boot:run

# Terminal 5 — Kitchen Service (port 8084)
cd iteration-6-order-service/kitchen-service
mvn spring-boot:run

# Terminal 6 — Delivery Service (port 8085)
cd iteration-6-order-service/delivery-service
mvn spring-boot:run
```

### 3. Start the Frontend

```bash
# Terminal 7 — Next.js SPA (port 3000)
cd iteration-6-order-service/ftgo-web
npm run dev
```

### 4. Open in Browser

Navigate to **http://localhost:3000**

## End-to-End Test Flow

### Consumer Flow
1. Open http://localhost:3000 → Click **Consumer**
2. Browse restaurants → Click **View Menu** on a restaurant
3. Select menu items (use +/- buttons), fill in customer details
4. Click **Place Order** → Redirected to order detail page
5. See order status: **PENDING** → Watch it progress automatically

### Restaurant Flow
6. Click **Restaurant** in the navbar → Select a restaurant
7. See kitchen tickets with status **CREATED**
8. Click **Accept** → Status becomes **ACCEPTED**
9. Click **Start Preparing** → Status becomes **PREPARING** (Order updates via Kafka)
10. Click **Mark Ready** → Status becomes **READY_FOR_PICKUP** (Order updates via Kafka)

### Courier Flow
11. Click **Courier** in the navbar → Select a courier
12. In "Pending Deliveries", click **Assign to Me** → Delivery moves to "My Deliveries"
13. Click **Pick Up** → Verifies kitchen ticket is READY_FOR_PICKUP (cross-service check)
14. Click **Deliver** → Delivery status becomes **DELIVERED** (Order updates via Kafka)

### Verify Final State
15. Go back to **Consumer** → **My Orders** → Order status should be **DELIVERED**
16. Order detail page shows payment info, notifications, and completed workflow stepper

**Order status progression:** PENDING → APPROVED → PREPARING → READY_FOR_PICKUP → PICKED_UP → DELIVERED

## Microservice Decomposition Summary

| Iteration | What Was Extracted | Communication Pattern |
|-----------|-------------------|----------------------|
| 1 | Restaurant Service (8081) | Synchronous REST + Timeout + Retry + Bulkhead |
| 2 | Notification Service (8082) | Asynchronous Kafka (fire-and-forget) |
| 3 | Accounting Service (8083) | Synchronous REST + Circuit Breaker |
| 4 | Kitchen Service (8084) | Bidirectional: REST + Kafka events |
| 5 | Delivery Service (8085) | Bidirectional + Cross-service (delivery → kitchen) |
| 6 | Order Service (8080) + SPA | Monolith eliminated, React/Next.js BFF frontend |

**Final result: 6 microservices + 1 SPA frontend. Zero monolith.**

## Technology Stack

| Layer | Technology |
|-------|-----------|
| Backend Services | Spring Boot 3.2, Java 17, H2, Spring Kafka, Resilience4j |
| Frontend | Next.js 14, React 18, TypeScript, Tailwind CSS |
| Messaging | Apache Kafka (3-broker cluster) |
| API Pattern | BFF (Backend For Frontend) via Next.js API routes |
| Fault Tolerance | Circuit Breaker, Timeout, Retry, Bulkhead, Fallback |
