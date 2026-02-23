package com.demo.greeting;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class CustomHealthIndicator implements HealthIndicator {

    private final GreetingController greetingController;

    public CustomHealthIndicator(GreetingController greetingController) {
        this.greetingController = greetingController;
    }

    @Override
    public Health health() {
        if (greetingController.isHealthy()) {
            return Health.up().withDetail("greeting", "operational").build();
        }
        return Health.down().withDetail("greeting", "simulated failure").build();
    }
}
