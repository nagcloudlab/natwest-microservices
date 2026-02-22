package com.ftgo.notification;

import com.ftgo.external.NotificationGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationGateway notificationGateway;

    public void sendOrderConfirmation(Long orderId, String consumerName, String consumerContact) {
        String message = "Hi " + consumerName + ", your order #" + orderId + " has been confirmed!";
        notificationGateway.send(consumerContact, message);

        Notification notification = new Notification();
        notification.setOrderId(orderId);
        notification.setType(NotificationType.SMS);
        notification.setRecipient(consumerContact);
        notification.setMessage(message);
        notification.setSentAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }

    public void sendDeliveryUpdate(Long orderId, String consumerName, String consumerContact, String message) {
        notificationGateway.send(consumerContact, message);

        Notification notification = new Notification();
        notification.setOrderId(orderId);
        notification.setType(NotificationType.PUSH);
        notification.setRecipient(consumerContact);
        notification.setMessage(message);
        notification.setSentAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }

    public List<Notification> getNotificationsByOrderId(Long orderId) {
        return notificationRepository.findByOrderId(orderId);
    }
}
