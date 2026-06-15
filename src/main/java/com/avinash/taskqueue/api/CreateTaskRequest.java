package com.avinash.taskqueue.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record CreateTaskRequest(
        @NotBlank String type,
        @NotNull JsonNode payload,
        @Min(0) @Max(9) int priority,
        Instant availableAt) {
}
