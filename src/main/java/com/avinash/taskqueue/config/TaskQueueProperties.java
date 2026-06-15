package com.avinash.taskqueue.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "task-queue")
public record TaskQueueProperties(int maxAttempts, Duration retryBaseDelay) {
}
