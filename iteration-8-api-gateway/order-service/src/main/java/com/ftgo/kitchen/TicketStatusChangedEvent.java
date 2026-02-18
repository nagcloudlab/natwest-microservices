package com.ftgo.kitchen;

import lombok.*;

/**
 * Domain Event consumed from Kafka topic "kitchen-events".
 *
 * This is the CONTRACT between kitchen-service (producer) and monolith (consumer).
 * Must match the event class in kitchen-service exactly.
 *
 * When kitchen-service changes a ticket's status (e.g., PREPARING, READY_FOR_PICKUP),
 * it publishes this event. The monolith's TicketStatusEventConsumer receives it
 * and updates the corresponding Order status.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @ToString
public class TicketStatusChangedEvent {
    private Long ticketId;
    private Long orderId;
    private String newStatus;
}
