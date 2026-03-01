package com.demo.greeting.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

@Schema(description = "V2 greeting response – superset of V1 with extra fields for schema evolution demo")
public record GreetingResponseV2(
        @Schema(description = "The greeting message", example = "Hello, World!")
        String message,

        @Schema(description = "Hostname of the responding instance", example = "greeting-service-abc123")
        String host,

        @Schema(description = "Language of the greeting", example = "en")
        String language,

        @Schema(description = "Server timestamp when the greeting was generated")
        Instant timestamp,

        @Schema(description = "Extensible metadata bag")
        Map<String, String> metadata
) {}
