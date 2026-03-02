package com.demo.time.controller;

import com.demo.time.client.GreetingClient;
import com.demo.time.dto.TimeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/time")
@Tag(name = "Time", description = "Time endpoint — calls greeting-service with Redis caching")
public class TimeController {

    private static final Logger log = LoggerFactory.getLogger(TimeController.class);

    private final GreetingClient greetingClient;

    public TimeController(GreetingClient greetingClient) {
        this.greetingClient = greetingClient;
    }

    @GetMapping
    @Operation(summary = "Get current time + greeting",
               description = "Calls greeting-service to fetch a greeting (cached in Redis for 30s). " +
                             "If greeting-service rate-limits the request, a fallback message is returned.")
    @ApiResponse(responseCode = "200", description = "Time and greeting returned")
    public TimeResponse getTime() throws UnknownHostException {
        log.info("Time request received");
        String greeting = greetingClient.fetchGreeting();

        return new TimeResponse(
                Instant.now(),
                greeting,
                InetAddress.getLocalHost().getHostName()
        );
    }

    @DeleteMapping("/cache")
    @Operation(summary = "Evict greeting cache",
               description = "Manually clears the Redis greeting cache, forcing the next request to call greeting-service")
    @ApiResponse(responseCode = "200", description = "Cache evicted")
    public Map<String, String> evictCache() {
        greetingClient.evictCache();
        return Map.of("status", "cache evicted");
    }
}
