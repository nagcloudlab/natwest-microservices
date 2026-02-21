package com.ftgo.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class SagaEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SagaEventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Async("eventPublisherExecutor")
    public void publishSagaCompleted(Long orderId, String finalState) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "eventType", "SAGA_" + finalState,
                    "orderId", orderId,
                    "sagaState", finalState
            ));
            kafkaTemplate.send("saga-events", String.valueOf(orderId), payload);
            log.info("Published SAGA_{} to saga-events for order #{}", finalState, orderId);
        } catch (Exception e) {
            log.error("Failed to publish saga event for order #{}: {}", orderId, e.getMessage());
        }
    }
}
