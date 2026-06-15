package com.avinash.taskqueue.service;

import java.time.Instant;

final class QueueScore {
    private static final int PRIORITY_LEVELS = 10;

    private QueueScore() {
    }

    static double forTask(Instant availableAt, int priority) {
        return availableAt.toEpochMilli() * PRIORITY_LEVELS + (9 - priority);
    }

    static double availableThrough(Instant now) {
        return now.toEpochMilli() * PRIORITY_LEVELS + 9;
    }
}
