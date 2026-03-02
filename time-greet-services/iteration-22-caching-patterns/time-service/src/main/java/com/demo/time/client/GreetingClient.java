package com.demo.time.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class GreetingClient {

    private static final Logger log = LoggerFactory.getLogger(GreetingClient.class);

    private final RestClient restClient;

    public GreetingClient(RestClient greetingRestClient) {
        this.restClient = greetingRestClient;
    }

    @Cacheable("greetings")
    public String fetchGreeting() {
        log.info("Cache miss — calling greeting-service");
        try {
            var body = restClient.get()
                    .uri("/api/greeting")
                    .retrieve()
                    .body(Map.class);
            String greeting = body != null ? (String) body.get("message") : "Hello, World!";
            log.info("Received greeting from greeting-service: {}", greeting);
            return greeting;
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("Greeting-service rate limit hit — using fallback");
            return "Hello, World! (greeting-service rate limit exceeded)";
        } catch (Exception e) {
            log.error("Failed to reach greeting-service: {}", e.getMessage());
            return "Hello, World! (greeting-service unavailable)";
        }
    }

    @CacheEvict(value = "greetings", allEntries = true)
    public void evictCache() {
        log.info("Greeting cache evicted");
    }
}
