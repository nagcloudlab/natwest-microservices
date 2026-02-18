package com.ftgo.delivery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FTGO Delivery Service — Extracted Microservice
 *
 * This service owns ALL delivery and courier data and manages the delivery lifecycle:
 *   PENDING → COURIER_ASSIGNED → PICKED_UP → DELIVERED
 *
 * Communication:
 *   - Monolith → Delivery: REST (POST /api/deliveries to create deliveries)
 *   - Delivery → Monolith: Kafka (publishes DeliveryStatusChanged events to "delivery-events" topic)
 *   - Delivery → Kitchen: REST (GET /api/kitchen/tickets/order/{orderId} to check ticket readiness)
 *
 * This is the first service that talks to ANOTHER extracted service (not just the monolith).
 * The delivery-service calls kitchen-service directly via REST to check if the kitchen ticket
 * is ready for pickup before allowing the courier to pick up the order.
 *
 * Cross-service communication pattern:
 *   Monolith → Delivery Service → Kitchen Service (service-to-service)
 */
@SpringBootApplication
public class DeliveryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeliveryServiceApplication.class, args);
    }
}
