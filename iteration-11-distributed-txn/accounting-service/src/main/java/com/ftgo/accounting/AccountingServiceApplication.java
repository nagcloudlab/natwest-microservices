package com.ftgo.accounting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FTGO Accounting Service — Extracted Microservice
 *
 * This service owns ALL payment data and integrates with the payment gateway (Mock Stripe).
 * It exposes a REST API for authorizing payments and querying payment status.
 *
 * The monolith calls this service via REST, protected by a Circuit Breaker (Resilience4j).
 */
@SpringBootApplication
public class AccountingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountingServiceApplication.class, args);
    }
}
