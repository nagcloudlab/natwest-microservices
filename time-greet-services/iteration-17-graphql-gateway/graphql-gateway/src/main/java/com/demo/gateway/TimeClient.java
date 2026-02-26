package com.demo.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class TimeClient {

    private final RestTemplate restTemplate;

    @Value("${time.service.url}")
    private String timeServiceUrl;

    public TimeClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getTime() {
        long start = System.nanoTime();
        Map<String, Object> result = restTemplate.getForObject(
                timeServiceUrl + "/time", Map.class);
        long latencyMs = (System.nanoTime() - start) / 1_000_000;

        return Map.of(
                "data", result,
                "latencyMs", latencyMs
        );
    }
}
