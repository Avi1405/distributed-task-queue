package com.avinash.taskqueue.api;

import jakarta.validation.constraints.NotBlank;

public record FailTaskRequest(@NotBlank String reason) {
}
