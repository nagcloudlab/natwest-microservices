package com.ftgo.external;

public interface NotificationGateway {
    boolean send(String recipient, String message);
}
