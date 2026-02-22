package com.ftgo.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes order events to Kafka.
 *
 * This replaces the synchronous NotificationService call.
 *
 * Before (monolith, synchronous):
 *   orderService.createOrder() → notificationService.sendOrderConfirmation()
 *   User waits for SMS to "send" before seeing confirmation.
 *
 * After (event-driven, asynchronous):
 *   orderService.createOrder() → orderEventPublisher.publishOrderCreated()
 *   User sees confirmation IMMEDIATELY. Notification happens in background.
 *
 * Benefits:
 * 1. Faster response — user doesn't wait for notification
 * 2. Loose coupling — monolith doesn't know/care who consumes the event
 * 3. Resilience — if notification-service is down, messages queue in Kafka
 * 4. Extensibility — add more consumers (analytics, audit) without changing monolith
 */
@Slf4j
@Component
public class OrderEventPublisher {

    private static final String TOPIC = "order-events";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void publishOrderCreated(OrderCreatedEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, String.valueOf(event.getOrderId()), json);
            log.info(">>> Published OrderCreated event to Kafka: {}", event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize OrderCreated event: {}", e.getMessage(), e);
        }
    }
}
