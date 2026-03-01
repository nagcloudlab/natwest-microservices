package com.demo.greeting.controller;

import com.demo.greeting.dto.GreetingResponseV2;
import com.demo.greeting.exception.GreetingNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/greeting")
@Tag(name = "Greeting V2", description = "Version 2 – extended greeting with language, timestamp, metadata (URI versioning)")
public class GreetingControllerV2 {

    @GetMapping
    @Operation(summary = "Greet the world (V2)",
               description = "Returns an extended greeting with language, timestamp, and metadata")
    @ApiResponse(responseCode = "200", description = "Greeting returned successfully")
    public GreetingResponseV2 greet() throws UnknownHostException {
        return buildResponse("Hello, World!");
    }

    @GetMapping("/{name}")
    @Operation(summary = "Greet by name (V2)",
               description = "Returns a personalised extended greeting. Names containing digits are rejected.")
    @ApiResponse(responseCode = "200", description = "Greeting returned successfully")
    @ApiResponse(responseCode = "404", description = "Name is invalid (contains digits)")
    public GreetingResponseV2 greetByName(
            @Parameter(description = "Name to greet", example = "Alice")
            @PathVariable String name) throws UnknownHostException {

        if (name.chars().anyMatch(Character::isDigit)) {
            throw new GreetingNotFoundException(name);
        }
        return buildResponse("Hello, " + name + "!");
    }

    private GreetingResponseV2 buildResponse(String message) throws UnknownHostException {
        return new GreetingResponseV2(
                message,
                InetAddress.getLocalHost().getHostName(),
                "en",
                Instant.now(),
                Map.of("version", "2", "api", "greeting")
        );
    }
}
