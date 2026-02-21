package com.ftgo.notification;

import lombok.*;

import java.math.BigDecimal;

/**
 * Event published by the monolith when an order is created.
 * This is the CONTRACT between the monolith (producer) and notification-service (consumer).
 *
 * The monolith publishes this to Kafka topic "order-events".
 * The notification-service consumes it and sends a confirmation SMS/push.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @ToString
public class OrderCreatedEvent {
    private Long orderId;
    private String consumerName;
    private String consumerContact;
    private String restaurantName;
    private BigDecimal totalAmount;
}
