package com.ftgo.kitchen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FTGO Kitchen Service — Extracted Microservice
 *
 * This service owns ALL kitchen ticket data and manages the ticket lifecycle:
 *   CREATED → ACCEPTED → PREPARING → READY_FOR_PICKUP
 *
 * Communication:
 *   - Monolith → Kitchen: REST (POST /api/kitchen/tickets to create tickets)
 *   - Kitchen → Monolith: Kafka (publishes TicketStatusChanged events to "kitchen-events" topic)
 *
 * This is the first BIDIRECTIONAL extraction:
 *   - Previous services used ONE-WAY communication (REST or Kafka, not both)
 *   - Kitchen-service uses REST for incoming commands + Kafka for outgoing events
 *   - The monolith is now both a Kafka PRODUCER (order-events) and CONSUMER (kitchen-events)
 */
@SpringBootApplication
public class KitchenServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(KitchenServiceApplication.class, args);
    }
}
