package com.demo.time.controller;

import com.demo.time.dto.TimeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/time")
@Tag(name = "Time", description = "Time endpoint — calls greeting-service (may hit its rate limit)")
public class TimeController {

    private static final Logger log = LoggerFactory.getLogger(TimeController.class);

    private final RestClient restClient;

    public TimeController(RestClient greetingRestClient) {
        this.restClient = greetingRestClient;
    }

    @GetMapping
    @Operation(summary = "Get current time + greeting",
               description = "Calls greeting-service to fetch a greeting. " +
                             "If greeting-service rate-limits the request, a fallback message is returned.")
    @ApiResponse(responseCode = "200", description = "Time and greeting returned")
    public TimeResponse getTime() throws UnknownHostException {
        log.info("Time request received — calling greeting-service");
        String greeting;
        try {
            var body = restClient.get()
                    .uri("/api/greeting")
                    .retrieve()
                    .body(Map.class);
            greeting = body != null ? (String) body.get("message") : "Hello, World!";
            log.info("Received greeting from greeting-service: {}", greeting);
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("Greeting-service rate limit hit — using fallback");
            greeting = "Hello, World! (greeting-service rate limit exceeded)";
        } catch (Exception e) {
            log.error("Failed to reach greeting-service: {}", e.getMessage());
            greeting = "Hello, World! (greeting-service unavailable)";
        }

        return new TimeResponse(
                Instant.now(),
                greeting,
                InetAddress.getLocalHost().getHostName()
        );
    }
}
