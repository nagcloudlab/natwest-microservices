package com.ftgo.notification;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationRepository notificationRepository;

    @GetMapping
    public List<Notification> getAllNotifications() {
        return notificationRepository.findAll();
    }

    @GetMapping("/order/{orderId}")
    public List<Notification> getNotificationsByOrderId(@PathVariable Long orderId) {
        return notificationRepository.findByOrderId(orderId);
    }
}
