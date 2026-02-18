package com.ftgo.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Kafka consumer — listens for OrderCreated events and sends notifications.
 *
 * This is FIRE-AND-FORGET from the monolith's perspective:
 * - The monolith publishes the event and immediately returns to the user
 * - This consumer processes the event asynchronously
 * - If this service is down, messages queue up in Kafka and get processed when it restarts
 *
 * Compare this to iteration 0 (monolith), where the notification was SYNCHRONOUS:
 * the user had to wait for the SMS to "send" before getting their order confirmation.
 */
@Slf4j
@Component
public class OrderEventConsumer {

    @Autowired
    private NotificationRepository notificationRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = "order-events", groupId = "notification-service")
    public void handleOrderCreated(String message) {
        try {
            OrderCreatedEvent event = objectMapper.readValue(message, OrderCreatedEvent.class);
            log.info(">>> Received OrderCreated event: {}", event);

            // Simulate sending SMS (like the old MockTwilioNotificationGateway)
            String smsMessage = "Hi " + event.getConsumerName()
                    + ", your order #" + event.getOrderId()
                    + " from " + event.getRestaurantName()
                    + " (total: Rs." + event.getTotalAmount() + ") has been confirmed!";

            log.info(">>> Mock Twilio: Sending to {}: {}", event.getConsumerContact(), smsMessage);
            Thread.sleep(300); // Simulate external API latency

            // Save notification record
            Notification notification = new Notification();
            notification.setOrderId(event.getOrderId());
            notification.setType(NotificationType.SMS);
            notification.setRecipient(event.getConsumerContact());
            notification.setMessage(smsMessage);
            notification.setSentAt(LocalDateTime.now());
            notificationRepository.save(notification);

            log.info(">>> Notification saved for order #{}", event.getOrderId());
        } catch (Exception e) {
            log.error("Failed to process OrderCreated event: {}", e.getMessage(), e);
        }
    }
}
