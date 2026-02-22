package com.ftgo.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftgo.order.Order;
import com.ftgo.order.OrderRepository;
import com.ftgo.order.OrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka consumer — listens for DeliveryStatusChanged events from delivery-service.
 *
 * This follows the same pattern as TicketStatusEventConsumer (iteration 4):
 *   Kitchen Service → Kafka (kitchen-events) → Monolith
 *   Delivery Service → Kafka (delivery-events) → Monolith
 *
 * The monolith now consumes from TWO Kafka topics:
 *   - kitchen-events  (from iteration 4)
 *   - delivery-events (new in iteration 5)
 *
 * This replaces the old tight coupling where DeliveryService directly updated Order status
 * via OrderRepository. Now the update happens asynchronously via domain events.
 */
@Slf4j
@Component
public class DeliveryStatusEventConsumer {

    @Autowired
    private OrderRepository orderRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = "delivery-events", groupId = "ftgo-monolith")
    @Transactional
    public void handleDeliveryStatusChanged(String message) {
        try {
            DeliveryStatusChangedEvent event = objectMapper.readValue(message, DeliveryStatusChangedEvent.class);
            log.info(">>> Received DeliveryStatusChanged event: {}", event);

            Order order = orderRepository.findById(event.getOrderId()).orElse(null);
            if (order == null) {
                log.warn(">>> Order #{} not found for delivery status update", event.getOrderId());
                return;
            }

            switch (event.getNewStatus()) {
                case "PICKED_UP" -> {
                    order.setStatus(OrderStatus.PICKED_UP);
                    log.info(">>> Updated order #{} status to PICKED_UP", order.getId());
                }
                case "DELIVERED" -> {
                    order.setStatus(OrderStatus.DELIVERED);
                    log.info(">>> Updated order #{} status to DELIVERED", order.getId());
                }
                default -> log.warn(">>> Unknown delivery status: {}", event.getNewStatus());
            }

            orderRepository.save(order);

        } catch (Exception e) {
            log.error("Failed to process DeliveryStatusChanged event: {}", e.getMessage(), e);
        }
    }
}
