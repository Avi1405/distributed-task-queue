package com.avinash.taskqueue.service;

import com.avinash.taskqueue.api.CreateTaskRequest;
import com.avinash.taskqueue.config.TaskQueueProperties;
import com.avinash.taskqueue.model.Task;
import com.avinash.taskqueue.model.TaskStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class TaskQueueService {
    private static final String QUEUE_KEY = "task-queue:ready";
    private static final String TASKS_KEY = "task-queue:tasks";
    private static final String DEAD_LETTER_KEY = "task-queue:dead-letter";
    private static final DefaultRedisScript<String> CLAIM_SCRIPT = new DefaultRedisScript<>(
            """
            local items = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, 1)
            if #items == 0 then return nil end
            redis.call('ZREM', KEYS[1], items[1])
            return items[1]
            """,
            String.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final TaskQueueProperties properties;
    private final Clock clock;
    private final Counter enqueued;
    private final Counter completed;
    private final Counter failed;

    public TaskQueueService(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            TaskQueueProperties properties,
            MeterRegistry meterRegistry) {
        this(redis, objectMapper, properties, meterRegistry, Clock.systemUTC());
    }

    TaskQueueService(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            TaskQueueProperties properties,
            MeterRegistry meterRegistry,
            Clock clock) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
        this.enqueued = meterRegistry.counter("task.queue.enqueued");
        this.completed = meterRegistry.counter("task.queue.completed");
        this.failed = meterRegistry.counter("task.queue.failed");
        meterRegistry.gauge("task.queue.depth", redis, template -> queueDepth().doubleValue());
    }

    public Task enqueue(CreateTaskRequest request) {
        Instant now = clock.instant();
        Instant availableAt = request.availableAt() == null ? now : request.availableAt();
        Task task = new Task(
                UUID.randomUUID().toString(),
                request.type(),
                request.payload(),
                request.priority(),
                TaskStatus.QUEUED,
                0,
                availableAt,
                now,
                now,
                null,
                null);
        save(task);
        redis.opsForZSet().add(QUEUE_KEY, task.id(), QueueScore.forTask(availableAt, task.priority()));
        enqueued.increment();
        return task;
    }

    public Optional<Task> claim(String workerId) {
        while (true) {
            String id = redis.execute(
                    CLAIM_SCRIPT,
                    List.of(QUEUE_KEY),
                    Double.toString(QueueScore.availableThrough(clock.instant())));
            if (id == null) {
                return Optional.empty();
            }

            Optional<Task> current = find(id);
            if (current.isEmpty()) {
                continue;
            }

            Instant now = clock.instant();
            Task task = current.get();
            Task claimed = new Task(
                    task.id(),
                    task.type(),
                    task.payload(),
                    task.priority(),
                    TaskStatus.PROCESSING,
                    task.attempts() + 1,
                    task.availableAt(),
                    task.createdAt(),
                    now,
                    workerId,
                    task.lastError());
            save(claimed);
            return Optional.of(claimed);
        }
    }

    public Task complete(String id) {
        Task current = getRequired(id);
        requireStatus(current, TaskStatus.PROCESSING);
        Task result = withState(current, TaskStatus.COMPLETED, null, null, clock.instant());
        save(result);
        completed.increment();
        return result;
    }

    public Task fail(String id, String reason) {
        Task current = getRequired(id);
        requireStatus(current, TaskStatus.PROCESSING);
        failed.increment();

        if (current.attempts() >= properties.maxAttempts()) {
            Task dead = withState(current, TaskStatus.DEAD_LETTER, null, reason, clock.instant());
            save(dead);
            redis.opsForSet().add(DEAD_LETTER_KEY, dead.id());
            return dead;
        }

        Duration delay = properties.retryBaseDelay().multipliedBy(1L << (current.attempts() - 1));
        Instant retryAt = clock.instant().plus(delay);
        Task retry = new Task(
                current.id(),
                current.type(),
                current.payload(),
                current.priority(),
                TaskStatus.QUEUED,
                current.attempts(),
                retryAt,
                current.createdAt(),
                clock.instant(),
                null,
                reason);
        save(retry);
        redis.opsForZSet().add(QUEUE_KEY, retry.id(), QueueScore.forTask(retryAt, retry.priority()));
        return retry;
    }

    public Optional<Task> find(String id) {
        Object value = redis.opsForHash().get(TASKS_KEY, id);
        return value == null ? Optional.empty() : Optional.of(read(value.toString()));
    }

    public Map<String, Long> stats() {
        return Map.of(
                "queued", queueDepth(),
                "deadLetter", Optional.ofNullable(redis.opsForSet().size(DEAD_LETTER_KEY)).orElse(0L));
    }

    private Long queueDepth() {
        return Optional.ofNullable(redis.opsForZSet().size(QUEUE_KEY)).orElse(0L);
    }

    private Task getRequired(String id) {
        return find(id).orElseThrow(() -> new TaskNotFoundException(id));
    }

    private void requireStatus(Task task, TaskStatus expected) {
        if (task.status() != expected) {
            throw new IllegalStateException(
                    "Task %s is %s, expected %s".formatted(task.id(), task.status(), expected));
        }
    }

    private Task withState(
            Task task,
            TaskStatus status,
            String workerId,
            String lastError,
            Instant updatedAt) {
        return new Task(
                task.id(),
                task.type(),
                task.payload(),
                task.priority(),
                status,
                task.attempts(),
                task.availableAt(),
                task.createdAt(),
                updatedAt,
                workerId,
                lastError);
    }

    private void save(Task task) {
        try {
            redis.opsForHash().put(TASKS_KEY, task.id(), objectMapper.writeValueAsString(task));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize task", exception);
        }
    }

    private Task read(String value) {
        try {
            return objectMapper.readValue(value, Task.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to deserialize task", exception);
        }
    }
}
