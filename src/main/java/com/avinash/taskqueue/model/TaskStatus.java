package com.avinash.taskqueue.model;

public enum TaskStatus {
    QUEUED,
    PROCESSING,
    COMPLETED,
    DEAD_LETTER
}
