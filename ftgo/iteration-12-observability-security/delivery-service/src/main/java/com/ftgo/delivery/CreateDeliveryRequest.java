package com.ftgo.delivery;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @ToString
public class CreateDeliveryRequest {
    private Long orderId;
    private String pickupAddress;
    private String deliveryAddress;
}
