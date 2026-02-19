package com.ftgo.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
    Long id,
    String consumerName,
    Long restaurantId,
    String restaurantName,
    String deliveryAddress,
    String status,
    BigDecimal totalAmount,
    List<OrderItem> items,
    LocalDateTime createdAt
) {}
