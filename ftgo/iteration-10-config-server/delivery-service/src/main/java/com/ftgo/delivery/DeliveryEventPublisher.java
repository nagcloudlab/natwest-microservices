package com.ftgo.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka producer — publishes delivery status change events to "delivery-events" topic.
 *
 * This follows the same pattern as kitchen-service's TicketEventPublisher (iteration 4).
 *
 * The monolith's DeliveryStatusEventConsumer listens on "delivery-events" and updates
 * Order status when deliveries are picked up or delivered.
 *
 * Events published:
 *   - PICKED_UP  → monolith updates Order → PICKED_UP
 *   - DELIVERED  → monolith updates Order → DELIVERED
 */
@Slf4j
@Component
public class DeliveryEventPublisher {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void publishStatusChanged(DeliveryStatusChangedEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("delivery-events", String.valueOf(event.getOrderId()), json);
            log.info(">>> Published DeliveryStatusChanged event to Kafka: {}", event);
        } catch (Exception e) {
            log.error("Failed to publish DeliveryStatusChanged event: {}", e.getMessage(), e);
        }
    }
}
