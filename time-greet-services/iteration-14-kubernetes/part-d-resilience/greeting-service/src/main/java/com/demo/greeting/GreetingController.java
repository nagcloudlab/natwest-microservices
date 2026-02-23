package com.demo.greeting;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
public class GreetingController {

    private final AtomicBoolean healthy = new AtomicBoolean(true);

    @GetMapping("/greeting")
    public Map<String, String> greeting() throws UnknownHostException {
        return Map.of(
                "message", "Hello from Greeting Service!",
                "host", InetAddress.getLocalHost().getHostName()
        );
    }

    @PostMapping("/greeting/fail")
    public Map<String, String> fail() throws UnknownHostException {
        healthy.set(false);
        return Map.of(
                "status", "Service marked as unhealthy — liveness probe will restart this pod",
                "host", InetAddress.getLocalHost().getHostName()
        );
    }

    @PostMapping("/greeting/recover")
    public Map<String, String> recover() throws UnknownHostException {
        healthy.set(true);
        return Map.of(
                "status", "Service marked as healthy again",
                "host", InetAddress.getLocalHost().getHostName()
        );
    }

    public boolean isHealthy() {
        return healthy.get();
    }
}
