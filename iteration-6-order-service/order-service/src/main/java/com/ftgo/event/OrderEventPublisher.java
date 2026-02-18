package com.ftgo.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Publishes order events to Kafka.
 *
 * Pattern 6: @Asynchronous (MicroProfile) → @Async (Spring)
 *
 * Before @Async:
 *   orderService.createOrder() → orderEventPublisher.publishOrderCreated()
 *   The HTTP thread blocks until Kafka.send() completes (~50-200ms).
 *   The user waits for Kafka to acknowledge before seeing the order confirmation.
 *
 * After @Async:
 *   orderService.createOrder() → orderEventPublisher.publishOrderCreated() [returns immediately]
 *   The Kafka publish runs on a background thread from "eventPublisherExecutor".
 *   The user sees the order confirmation instantly.
 *
 * Why @Async fits here:
 * - Kafka publishing is FIRE-AND-FORGET — the order is already persisted in the DB
 * - If Kafka publish fails, the order still exists (eventual consistency via retry/replay)
 * - The notification is non-critical — the order response shouldn't wait for it
 *
 * Why NOT @Async on authorizePayment()?
 * - Payment is SYNCHRONOUS — we need the result before approving the order
 * - Making it async would mean approving orders without confirmed payment!
 *
 * Check the thread name in logs to verify:
 *   Before: "http-nio-8080-exec-1" (servlet thread)
 *   After:  "event-publisher-1" (dedicated async thread)
 */
@Slf4j
@Component
public class OrderEventPublisher {

    private static final String TOPIC = "order-events";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Async("eventPublisherExecutor")
    public void publishOrderCreated(OrderCreatedEvent event) {
        log.info(">>> Publishing OrderCreated event on thread [{}]: {}",
                Thread.currentThread().getName(), event);
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, String.valueOf(event.getOrderId()), json);
            log.info(">>> Published OrderCreated event to Kafka successfully on thread [{}]",
                    Thread.currentThread().getName());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize OrderCreated event: {}", e.getMessage(), e);
        }
    }
}
