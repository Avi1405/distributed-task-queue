package com.avinash.taskqueue.service;

public class TaskNotFoundException extends RuntimeException {
    public TaskNotFoundException(String id) {
        super("Task not found: " + id);
    }
}
