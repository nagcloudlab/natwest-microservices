package com.ftgo.kitchen;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Kitchen Ticket DTO — no longer a JPA entity.
 * Kitchen ticket data now lives in the Kitchen Service (port 8084).
 *
 * Note: status is a String, not a TicketStatus enum.
 * The monolith no longer owns the TicketStatus enum — that lives in kitchen-service.
 * Using String avoids coupling the monolith to kitchen-service's internal types.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @ToString
public class KitchenTicketResponse {
    private Long id;
    private Long orderId;
    private Long restaurantId;
    private String items;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime readyAt;
}
