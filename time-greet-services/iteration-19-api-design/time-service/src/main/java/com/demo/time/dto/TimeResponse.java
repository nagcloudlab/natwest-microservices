package com.demo.time.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Simple time response")
public record TimeResponse(
        @Schema(description = "Current server time in ISO-8601")
        Instant time,

        @Schema(description = "Hostname of the responding instance")
        String host
) {}
