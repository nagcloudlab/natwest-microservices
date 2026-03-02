package com.demo.greeting.controller;

import com.demo.greeting.dto.GreetingResponse;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.net.InetAddress;
import java.net.UnknownHostException;

@RestController
@RequestMapping("/api/greeting")
@Tag(name = "Greeting", description = "Greeting endpoints protected by Resilience4j RateLimiter")
public class GreetingController {

    private static final Logger log = LoggerFactory.getLogger(GreetingController.class);

    @GetMapping
    @RateLimiter(name = "greeting")
    @Operation(summary = "Greet the world",
               description = "Returns a default greeting. Rate-limited to 5 requests per 10 seconds.")
    @ApiResponse(responseCode = "200", description = "Greeting returned successfully")
    @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    public GreetingResponse greet() throws UnknownHostException {
        log.info("Greeting request received");
        return new GreetingResponse(
                "Hello, World!",
                InetAddress.getLocalHost().getHostName()
        );
    }

    @GetMapping("/{name}")
    @RateLimiter(name = "greeting")
    @Operation(summary = "Greet by name",
               description = "Returns a personalised greeting. Rate-limited to 5 requests per 10 seconds.")
    @ApiResponse(responseCode = "200", description = "Greeting returned successfully")
    @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    public GreetingResponse greetByName(
            @Parameter(description = "Name to greet", example = "Alice")
            @PathVariable String name) throws UnknownHostException {
        log.info("Greeting request received for name={}", name);
        return new GreetingResponse(
                "Hello, " + name + "!",
                InetAddress.getLocalHost().getHostName()
        );
    }
}
