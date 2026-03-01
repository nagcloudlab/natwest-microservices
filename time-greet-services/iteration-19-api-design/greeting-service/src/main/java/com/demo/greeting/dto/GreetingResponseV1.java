package com.demo.greeting.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "V1 greeting response – minimal fields")
public record GreetingResponseV1(
        @Schema(description = "The greeting message", example = "Hello, World!")
        String message,

        @Schema(description = "Hostname of the responding instance", example = "greeting-service-abc123")
        String host
) {}
