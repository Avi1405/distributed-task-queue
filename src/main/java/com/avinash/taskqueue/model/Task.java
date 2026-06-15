package com.avinash.taskqueue.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record Task(
        String id,
        String type,
        JsonNode payload,
        int priority,
        TaskStatus status,
        int attempts,
        Instant availableAt,
        Instant createdAt,
        Instant updatedAt,
        String workerId,
        String lastError) {
}
