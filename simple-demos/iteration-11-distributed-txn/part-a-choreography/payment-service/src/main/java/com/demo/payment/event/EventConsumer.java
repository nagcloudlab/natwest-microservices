package com.demo.payment.event;

import com.demo.payment.Payment;
import com.demo.payment.PaymentService;
import com.demo.payment.PaymentStatus;
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

    private final PaymentService paymentService;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EventConsumer(PaymentService paymentService, EventPublisher eventPublisher) {
        this.paymentService = paymentService;
        this.eventPublisher = eventPublisher;
    }

    @KafkaListener(topics = "inventory-events", groupId = "payment-service")
    public void handleInventoryEvent(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String eventType = event.get("eventType").asText();
            Long orderId = event.get("orderId").asLong();

            log.info("Received {} for order {}", eventType, orderId);

            if ("INVENTORY_RESERVED".equals(eventType)) {
                BigDecimal amount = new BigDecimal(event.get("amount").asText());

                Payment payment = paymentService.processPayment(orderId, amount);

                if (payment.getStatus() == PaymentStatus.PROCESSED) {
                    eventPublisher.publish("payment-events", orderId.toString(),
                            "PAYMENT_PROCESSED", orderId);
                } else {
                    eventPublisher.publish("payment-events", orderId.toString(),
                            "PAYMENT_FAILED", orderId);
                }
            }
        } catch (Exception e) {
            log.error("Error processing inventory event: {}", e.getMessage());
        }
    }
}
