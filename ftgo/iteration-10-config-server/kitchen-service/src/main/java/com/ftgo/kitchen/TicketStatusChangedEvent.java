package com.ftgo.kitchen;

import lombok.*;

/**
 * Domain Event published to Kafka when a kitchen ticket's status changes.
 *
 * This is the CONTRACT between kitchen-service (producer) and monolith (consumer).
 * The monolith listens on the "kitchen-events" topic and updates Order status accordingly.
 *
 * Event flow:
 *   Kitchen staff clicks "Start Preparing" →
 *   KitchenService.startPreparation() →
 *   TicketEventPublisher publishes {orderId, ticketId, newStatus: "PREPARING"} →
 *   Monolith's TicketStatusEventConsumer receives event →
 *   Updates Order status to PREPARING
 *
 * This replaces the old tight coupling where KitchenService directly called OrderRepository.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @ToString
public class TicketStatusChangedEvent {
    private Long ticketId;
    private Long orderId;
    private String newStatus;
}
