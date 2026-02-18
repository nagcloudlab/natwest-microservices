package com.ftgo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FTGO — Food To Go (Iteration 3: Restaurant + Notification + Accounting Extracted)
 *
 * Three modules have been extracted:
 *   - Restaurant → Restaurant Service (port 8081, sync REST)
 *   - Notification → Notification Service (port 8082, async Kafka)
 *   - Accounting → Accounting Service (port 8083, sync REST + Circuit Breaker)
 *
 * This monolith still contains:
 *   - Order Module        (com.ftgo.order)
 *   - Kitchen Module      (com.ftgo.kitchen)
 *   - Delivery Module     (com.ftgo.delivery)
 *
 * New in this iteration: payment authorization calls the Accounting Service
 * via REST with a Resilience4j Circuit Breaker. If the service is down,
 * the circuit breaker trips and orders are rejected gracefully.
 */
@SpringBootApplication
public class FtgoMonolithApplication {

    public static void main(String[] args) {
        SpringApplication.run(FtgoMonolithApplication.class, args);
    }
}
