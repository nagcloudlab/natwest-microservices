package com.demo.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class GreetingClient {

    private final RestTemplate restTemplate;

    @Value("${greeting.service.url}")
    private String greetingServiceUrl;

    public GreetingClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getGreeting() {
        long start = System.nanoTime();
        Map<String, Object> result = restTemplate.getForObject(
                greetingServiceUrl + "/greeting", Map.class);
        long latencyMs = (System.nanoTime() - start) / 1_000_000;

        return Map.of(
                "data", result,
                "latencyMs", latencyMs
        );
    }
}
