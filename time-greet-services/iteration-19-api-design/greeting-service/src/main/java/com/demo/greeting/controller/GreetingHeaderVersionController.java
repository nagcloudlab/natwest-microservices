package com.demo.greeting.controller;

import com.demo.greeting.dto.GreetingResponseV1;
import com.demo.greeting.dto.GreetingResponseV2;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/greeting")
@Tag(name = "Greeting (Header Versioning)",
     description = "Same endpoint, version selected via Accept header: " +
                   "application/vnd.demo.v1+json or application/vnd.demo.v2+json")
public class GreetingHeaderVersionController {

    @GetMapping(produces = "application/vnd.demo.v1+json")
    @Operation(summary = "Greet (header V1)",
               description = "Returns V1 response when Accept: application/vnd.demo.v1+json")
    public GreetingResponseV1 greetV1() throws UnknownHostException {
        return new GreetingResponseV1(
                "Hello, World!",
                InetAddress.getLocalHost().getHostName()
        );
    }

    @GetMapping(produces = "application/vnd.demo.v2+json")
    @Operation(summary = "Greet (header V2)",
               description = "Returns V2 response when Accept: application/vnd.demo.v2+json")
    public GreetingResponseV2 greetV2() throws UnknownHostException {
        return new GreetingResponseV2(
                "Hello, World!",
                InetAddress.getLocalHost().getHostName(),
                "en",
                Instant.now(),
                Map.of("version", "2", "api", "greeting")
        );
    }
}
