package com.demo.time.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Combined time + greeting response")
public record TimeResponse(
        @Schema(description = "Current server time in ISO-8601")
        Instant time,

        @Schema(description = "Greeting from greeting-service")
        String greeting,

        @Schema(description = "Hostname of the responding instance")
        String host
) {}
