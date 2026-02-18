package com.ftgo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FTGO — Food To Go (Iteration 1: Restaurant Extracted)
 *
 * The Restaurant module has been extracted into its own microservice (port 8081).
 * This monolith still contains:
 *   - Order Module        (com.ftgo.order)
 *   - Kitchen Module      (com.ftgo.kitchen)
 *   - Delivery Module     (com.ftgo.delivery)
 *   - Accounting Module   (com.ftgo.accounting)
 *   - Notification Module (com.ftgo.notification)
 *
 * Restaurant data is accessed via RestaurantServiceClient (HTTP → localhost:8081).
 * This is the Strangler Fig pattern — the monolith shrinks as services are extracted.
 */
@SpringBootApplication
public class FtgoMonolithApplication {

    public static void main(String[] args) {
        SpringApplication.run(FtgoMonolithApplication.class, args);
    }
}
