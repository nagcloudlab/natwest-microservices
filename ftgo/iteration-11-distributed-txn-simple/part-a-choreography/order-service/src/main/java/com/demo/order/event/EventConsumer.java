package com.demo.order.event;

import com.demo.order.OrderService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class EventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventConsumer.class);

    private final OrderService orderService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EventConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    @KafkaListener(topics = "payment-events", groupId = "order-service")
    public void handlePaymentEvent(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String eventType = event.get("eventType").asText();
            Long orderId = event.get("orderId").asLong();

            log.info("Received {} for order {}", eventType, orderId);

            switch (eventType) {
                case Events.PAYMENT_PROCESSED -> {
                    log.info("Payment successful for order {} — approving order", orderId);
                    orderService.approveOrder(orderId);
                }
                case Events.PAYMENT_FAILED -> {
                    log.info("Payment failed for order {} — rejecting order", orderId);
                    orderService.rejectOrder(orderId);
                }
                default -> log.warn("Unknown payment event type: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Error processing payment event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "inventory-events", groupId = "order-service")
    public void handleInventoryEvent(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String eventType = event.get("eventType").asText();
            Long orderId = event.get("orderId").asLong();

            log.info("Received {} for order {}", eventType, orderId);

            if (Events.INVENTORY_RELEASED.equals(eventType)) {
                log.info("Inventory released for order {} — compensation complete", orderId);
            }
        } catch (Exception e) {
            log.error("Error processing inventory event: {}", e.getMessage());
        }
    }
}
