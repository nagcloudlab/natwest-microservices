package com.ftgo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FTGO — Food To Go (Iteration 4: Restaurant + Notification + Accounting + Kitchen Extracted)
 *
 * Four modules have been extracted:
 *   - Restaurant → Restaurant Service (port 8081, sync REST)
 *   - Notification → Notification Service (port 8082, async Kafka)
 *   - Accounting → Accounting Service (port 8083, sync REST + Circuit Breaker)
 *   - Kitchen → Kitchen Service (port 8084, REST + Kafka bidirectional)
 *
 * This monolith still contains:
 *   - Order Module        (com.ftgo.order)
 *   - Delivery Module     (com.ftgo.delivery)
 *
 * New in this iteration: Kitchen extraction with BIDIRECTIONAL communication.
 *   - Monolith → Kitchen: REST (create/manage tickets via KitchenServiceClient)
 *   - Kitchen → Monolith: Kafka (status change events via TicketStatusEventConsumer)
 *   - The monolith is now both a Kafka PRODUCER (order-events) and CONSUMER (kitchen-events)
 */
@SpringBootApplication
public class FtgoMonolithApplication {

    public static void main(String[] args) {
        SpringApplication.run(FtgoMonolithApplication.class, args);
    }
}
