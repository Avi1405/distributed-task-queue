package com.avinash.taskqueue.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class QueueScoreTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void higherPrioritySortsFirstAtTheSameAvailabilityTime() {
        assertThat(QueueScore.forTask(NOW, 9)).isLessThan(QueueScore.forTask(NOW, 1));
    }

    @Test
    void futureTasksSortAfterCurrentlyAvailableTasks() {
        assertThat(QueueScore.forTask(NOW.plusMillis(1), 9))
                .isGreaterThan(QueueScore.availableThrough(NOW));
    }
}
