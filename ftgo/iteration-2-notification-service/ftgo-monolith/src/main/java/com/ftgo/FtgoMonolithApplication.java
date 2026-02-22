package com.ftgo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FTGO — Food To Go (Iteration 2: Restaurant + Notification Extracted)
 *
 * Two modules have been extracted:
 *   - Restaurant → Restaurant Service (port 8081, sync REST)
 *   - Notification → Notification Service (port 8082, async Kafka)
 *
 * This monolith still contains:
 *   - Order Module        (com.ftgo.order)
 *   - Kitchen Module      (com.ftgo.kitchen)
 *   - Delivery Module     (com.ftgo.delivery)
 *   - Accounting Module   (com.ftgo.accounting)
 *
 * New in this iteration: the monolith publishes OrderCreated events to Kafka.
 * notification-service consumes them asynchronously (fire-and-forget).
 */
@SpringBootApplication
public class FtgoMonolithApplication {

    public static void main(String[] args) {
        SpringApplication.run(FtgoMonolithApplication.class, args);
    }
}
