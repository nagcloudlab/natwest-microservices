package com.ftgo.notification;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @GetMapping("/order/{orderId}")
    public List<Notification> getNotificationsByOrderId(@PathVariable Long orderId) {
        return notificationService.getNotificationsByOrderId(orderId);
    }
}
