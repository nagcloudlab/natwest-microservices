package com.demo.order.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(String topic, String key, String eventType,
                        Long orderId, String productId, int quantity, BigDecimal amount) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "eventType", eventType,
                    "orderId", orderId,
                    "productId", productId,
                    "quantity", quantity,
                    "amount", amount
            ));
            kafkaTemplate.send(topic, key, payload);
            log.info("Published {} to {} [key={}]", eventType, topic, key);
        } catch (Exception e) {
            log.error("Failed to publish event: {}", e.getMessage());
        }
    }
}
