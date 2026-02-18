package com.ftgo.event;

import lombok.*;

import java.math.BigDecimal;

/**
 * Event published to Kafka when an order is created.
 *
 * This replaces the direct call to NotificationService.sendOrderConfirmation().
 * Instead of synchronously sending a notification, we publish this event
 * and let interested services (notification-service) consume it asynchronously.
 *
 * This is the CONTRACT between producer (monolith) and consumers (notification-service).
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @ToString
public class OrderCreatedEvent {
    private Long orderId;
    private String consumerName;
    private String consumerContact;
    private String restaurantName;
    private BigDecimal totalAmount;
}
