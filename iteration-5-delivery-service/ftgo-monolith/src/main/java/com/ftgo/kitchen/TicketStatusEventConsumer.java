package com.ftgo.kitchen;

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
 * Kafka consumer — listens for TicketStatusChanged events from kitchen-service.
 *
 * This is the REVERSE of iteration 2's Kafka flow:
 *   Iteration 2: Monolith → Kafka (order-events) → Notification Service
 *   Iteration 4: Kitchen Service → Kafka (kitchen-events) → Monolith
 *
 * The monolith is now BOTH a Kafka producer AND consumer:
 *   - PRODUCES: order-events (consumed by notification-service)
 *   - CONSUMES: kitchen-events (produced by kitchen-service)
 *
 * This replaces the old tight coupling where KitchenService directly updated Order status
 * via OrderRepository. Now the update happens asynchronously via domain events.
 *
 * EVENTUAL CONSISTENCY:
 *   There's a small delay (typically < 100ms) between kitchen-service updating the ticket
 *   and the monolith updating the order. During that window, the ticket might be PREPARING
 *   but the order still shows APPROVED. This is acceptable for our use case — the order
 *   status will converge quickly, and the UI auto-refreshes every 15 seconds.
 */
@Slf4j
@Component
public class TicketStatusEventConsumer {

    @Autowired
    private OrderRepository orderRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = "kitchen-events", groupId = "ftgo-monolith")
    @Transactional
    public void handleTicketStatusChanged(String message) {
        try {
            TicketStatusChangedEvent event = objectMapper.readValue(message, TicketStatusChangedEvent.class);
            log.info(">>> Received TicketStatusChanged event: {}", event);

            Order order = orderRepository.findById(event.getOrderId()).orElse(null);
            if (order == null) {
                log.warn(">>> Order #{} not found for ticket status update", event.getOrderId());
                return;
            }

            // Map kitchen ticket status → order status
            switch (event.getNewStatus()) {
                case "PREPARING" -> {
                    order.setStatus(OrderStatus.PREPARING);
                    log.info(">>> Updated order #{} status to PREPARING", order.getId());
                }
                case "READY_FOR_PICKUP" -> {
                    order.setStatus(OrderStatus.READY_FOR_PICKUP);
                    log.info(">>> Updated order #{} status to READY_FOR_PICKUP", order.getId());
                }
                case "ACCEPTED" -> {
                    // No order status change needed for ACCEPTED
                    log.info(">>> Ticket accepted for order #{} — no order status change", order.getId());
                }
                default -> log.warn(">>> Unknown ticket status: {}", event.getNewStatus());
            }

            orderRepository.save(order);

        } catch (Exception e) {
            log.error("Failed to process TicketStatusChanged event: {}", e.getMessage(), e);
        }
    }
}
