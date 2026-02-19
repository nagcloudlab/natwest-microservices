package com.ftgo.notification;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long orderId;

    @Enumerated(EnumType.STRING)
    private NotificationType type;

    private String recipient;
    private String message;
    private LocalDateTime sentAt;
}
