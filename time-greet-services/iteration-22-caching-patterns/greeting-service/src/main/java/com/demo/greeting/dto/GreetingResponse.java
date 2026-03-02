package com.demo.greeting.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Greeting response")
public record GreetingResponse(
        @Schema(description = "The greeting message", example = "Hello, World!")
        String message,

        @Schema(description = "Hostname of the responding instance", example = "greeting-abc123")
        String host
) {}
