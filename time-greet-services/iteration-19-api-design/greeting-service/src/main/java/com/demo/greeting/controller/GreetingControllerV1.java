package com.demo.greeting.controller;

import com.demo.greeting.dto.GreetingResponseV1;
import com.demo.greeting.exception.GreetingNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.net.InetAddress;
import java.net.UnknownHostException;

@RestController
@RequestMapping("/api/v1/greeting")
@Tag(name = "Greeting V1", description = "Version 1 – minimal greeting (URI versioning)")
public class GreetingControllerV1 {

    @GetMapping
    @Operation(summary = "Greet the world",
               description = "Returns a default greeting with hostname")
    @ApiResponse(responseCode = "200", description = "Greeting returned successfully")
    public GreetingResponseV1 greet() throws UnknownHostException {
        return new GreetingResponseV1(
                "Hello, World!",
                InetAddress.getLocalHost().getHostName()
        );
    }

    @GetMapping("/{name}")
    @Operation(summary = "Greet by name",
               description = "Returns a personalised greeting. Names containing digits are rejected.")
    @ApiResponse(responseCode = "200", description = "Greeting returned successfully")
    @ApiResponse(responseCode = "404", description = "Name is invalid (contains digits)")
    public GreetingResponseV1 greetByName(
            @Parameter(description = "Name to greet", example = "Alice")
            @PathVariable String name) throws UnknownHostException {

        if (name.chars().anyMatch(Character::isDigit)) {
            throw new GreetingNotFoundException(name);
        }
        return new GreetingResponseV1(
                "Hello, " + name + "!",
                InetAddress.getLocalHost().getHostName()
        );
    }
}
