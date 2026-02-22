package com.ftgo.delivery;

import lombok.*;

/**
 * Domain Event consumed from Kafka topic "delivery-events".
 *
 * This is the CONTRACT between delivery-service (producer) and monolith (consumer).
 * Must match the event class in delivery-service exactly.
 *
 * When delivery-service changes a delivery's status (e.g., PICKED_UP, DELIVERED),
 * it publishes this event. The monolith's DeliveryStatusEventConsumer receives it
 * and updates the corresponding Order status.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @ToString
public class DeliveryStatusChangedEvent {
    private Long deliveryId;
    private Long orderId;
    private String newStatus;
}
