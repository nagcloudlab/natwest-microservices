package com.ftgo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Order Service — Standalone Microservice (Iteration 6)
 *
 * The monolith is GONE. This is a pure REST API for order management.
 * The UI is now a React/Next.js SPA (ftgo-web on port 3000).
 *
 * All 6 microservices:
 *   - Order Service (this)       → port 8080 — Order entity, orchestrates order lifecycle
 *   - Restaurant Service         → port 8081 — Restaurant + menu data
 *   - Notification Service       → port 8082 — Consumes order-events via Kafka
 *   - Accounting Service         → port 8083 — Payment authorization
 *   - Kitchen Service            → port 8084 — Kitchen ticket management
 *   - Delivery Service           → port 8085 — Delivery + courier management
 *
 * Communication patterns:
 *   - Order → Restaurant/Accounting/Kitchen/Delivery: REST (outbound)
 *   - Order → Notification: Kafka producer (order-events)
 *   - Kitchen/Delivery → Order: Kafka consumer (kitchen-events, delivery-events)
 */
@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
