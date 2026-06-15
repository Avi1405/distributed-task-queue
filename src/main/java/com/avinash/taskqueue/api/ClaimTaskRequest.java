package com.avinash.taskqueue.api;

import jakarta.validation.constraints.NotBlank;

public record ClaimTaskRequest(@NotBlank String workerId) {
}
