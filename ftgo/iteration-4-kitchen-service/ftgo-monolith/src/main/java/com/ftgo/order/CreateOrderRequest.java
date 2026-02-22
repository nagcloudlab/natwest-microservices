package com.ftgo.order;

import java.util.List;

public record CreateOrderRequest(
    Long consumerId,
    String consumerName,
    String consumerContact,
    Long restaurantId,
    String deliveryAddress,
    String paymentMethod,
    List<OrderItemRequest> items
) {}
