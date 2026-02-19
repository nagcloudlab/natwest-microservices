package com.ftgo.kitchen;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes kitchen ticket status change events to Kafka.
 *
 * This is the REVERSE direction from iteration 2:
 *   Iteration 2: Monolith (producer) → Kafka → Notification Service (consumer)
 *   Iteration 4: Kitchen Service (producer) → Kafka → Monolith (consumer)
 *
 * The monolith is now BOTH a producer (order-events) AND consumer (kitchen-events).
 * This is bidirectional event-driven communication.
 *
 * Why Kafka instead of REST callback?
 * 1. Loose coupling — kitchen-service doesn't know/care who consumes the event
 * 2. Resilience — if monolith is temporarily down, events queue in Kafka
 * 3. Audit trail — Kafka retains events for replay/debugging
 * 4. Multiple consumers — other services can also react to kitchen events
 */
@Slf4j
@Component
public class TicketEventPublisher {

    private static final String TOPIC = "kitchen-events";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void publishStatusChanged(TicketStatusChangedEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, String.valueOf(event.getOrderId()), json);
            log.info(">>> Published TicketStatusChanged event to Kafka: {}", event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize TicketStatusChanged event: {}", e.getMessage(), e);
        }
    }
}
