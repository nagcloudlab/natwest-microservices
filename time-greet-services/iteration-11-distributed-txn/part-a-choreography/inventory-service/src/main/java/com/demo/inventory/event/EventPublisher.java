package com.demo.inventory.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(String topic, String key, String eventType, Long orderId, BigDecimal amount) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("eventType", eventType);
            payload.put("orderId", orderId);
            if (amount != null) {
                payload.put("amount", amount);
            }
            kafkaTemplate.send(topic, key, objectMapper.writeValueAsString(payload));
            log.info("Published {} to {} [key={}]", eventType, topic, key);
        } catch (Exception e) {
            log.error("Failed to publish event: {}", e.getMessage());
        }
    }

    public void publish(String topic, String key, String eventType, Long orderId) {
        publish(topic, key, eventType, orderId, null);
    }
}
