package com.ftgo.external;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MockTwilioNotificationGateway implements NotificationGateway {

    @Override
    public boolean send(String recipient, String message) {
        log.info(">>> Mock Twilio: Sending to {}: {}", recipient, message);
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return true;
    }
}
