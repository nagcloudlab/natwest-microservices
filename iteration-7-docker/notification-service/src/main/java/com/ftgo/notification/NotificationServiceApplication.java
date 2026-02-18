package com.ftgo.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * FTGO Notification Service — Extracted Microservice
 *
 * This service listens for events on Kafka and sends notifications.
 * It is completely ASYNCHRONOUS — the monolith publishes an event and moves on.
 * This service processes the event whenever it's ready (fire-and-forget).
 *
 * Kafka topic consumed: "order-events"
 * Events handled: OrderCreated
 */
@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
