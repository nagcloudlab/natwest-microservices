package com.demo.inventory.event;

import com.demo.inventory.InventoryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class EventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventConsumer.class);

    private final InventoryService inventoryService;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EventConsumer(InventoryService inventoryService, EventPublisher eventPublisher) {
        this.inventoryService = inventoryService;
        this.eventPublisher = eventPublisher;
    }

    @KafkaListener(topics = "order-events", groupId = "inventory-service")
    public void handleOrderEvent(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String eventType = event.get("eventType").asText();
            Long orderId = event.get("orderId").asLong();

            log.info("Received {} for order {}", eventType, orderId);

            if ("ORDER_CREATED".equals(eventType)) {
                String productId = event.get("productId").asText();
                int quantity = event.get("quantity").asInt();
                BigDecimal amount = new BigDecimal(event.get("amount").asText());

                inventoryService.reserve(orderId, productId, quantity);

                // Forward the amount to the next step (payment-service listens on inventory-events)
                eventPublisher.publish("inventory-events", orderId.toString(),
                        "INVENTORY_RESERVED", orderId, amount);
            }
        } catch (Exception e) {
            log.error("Error processing order event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "payment-events", groupId = "inventory-service")
    public void handlePaymentEvent(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String eventType = event.get("eventType").asText();
            Long orderId = event.get("orderId").asLong();

            log.info("Received {} for order {}", eventType, orderId);

            if ("PAYMENT_FAILED".equals(eventType)) {
                log.info("Payment failed for order {} — releasing inventory (compensation)", orderId);
                inventoryService.release(orderId);
                eventPublisher.publish("inventory-events", orderId.toString(),
                        "INVENTORY_RELEASED", orderId);
            }
        } catch (Exception e) {
            log.error("Error processing payment event: {}", e.getMessage());
        }
    }
}
