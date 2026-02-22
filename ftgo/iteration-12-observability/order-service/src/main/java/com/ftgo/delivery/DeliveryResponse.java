package com.ftgo.delivery;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Delivery DTO — no longer a JPA entity.
 * Delivery data now lives in the Delivery Service (port 8085).
 *
 * Note: status is a String, not a DeliveryStatus enum.
 * The monolith no longer owns the DeliveryStatus enum — that lives in delivery-service.
 * Using String avoids coupling the monolith to delivery-service's internal types.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @ToString
public class DeliveryResponse {
    private Long id;
    private Long orderId;
    private Long courierId;
    private String pickupAddress;
    private String deliveryAddress;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime deliveredAt;

    // Courier fields — populated when the response represents a courier
    private String name;
    private String phone;
    private boolean available;
}
