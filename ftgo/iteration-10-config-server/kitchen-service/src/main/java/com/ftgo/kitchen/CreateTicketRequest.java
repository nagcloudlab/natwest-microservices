package com.ftgo.kitchen;

import lombok.*;

/**
 * Request DTO for creating a kitchen ticket.
 * Sent by the monolith's KitchenServiceClient via REST.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class CreateTicketRequest {
    private Long orderId;
    private Long restaurantId;
    private String items;
}
