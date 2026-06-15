package com.avinash.taskqueue.api;

import com.avinash.taskqueue.model.Task;
import com.avinash.taskqueue.service.TaskQueueService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class TaskQueueController {
    private final TaskQueueService service;

    public TaskQueueController(TaskQueueService service) {
        this.service = service;
    }

    @PostMapping("/tasks")
    public ResponseEntity<Task> enqueue(@Valid @RequestBody CreateTaskRequest request) {
        Task task = service.enqueue(request);
        return ResponseEntity.created(URI.create("/tasks/" + task.id())).body(task);
    }

    @PostMapping("/tasks/claim")
    public ResponseEntity<Task> claim(@Valid @RequestBody ClaimTaskRequest request) {
        return service.claim(request.workerId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/tasks/{id}")
    public Task find(@PathVariable String id) {
        return service.find(id).orElseThrow(() -> new com.avinash.taskqueue.service.TaskNotFoundException(id));
    }

    @PostMapping("/tasks/{id}/complete")
    public Task complete(@PathVariable String id) {
        return service.complete(id);
    }

    @PostMapping("/tasks/{id}/fail")
    public Task fail(@PathVariable String id, @Valid @RequestBody FailTaskRequest request) {
        return service.fail(id, request.reason());
    }

    @GetMapping("/queue/stats")
    public Map<String, Long> stats() {
        return service.stats();
    }
}
