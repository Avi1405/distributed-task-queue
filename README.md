# Distributed Task Queue

A compact Redis-backed task queue reference implementation built with Java 17 and Spring Boot.

The project demonstrates the core coordination mechanics behind asynchronous work distribution: durable task metadata, priority-aware scheduling, atomic claims, retry backoff, dead-letter handling, and operational metrics.

## What it does

- Enqueues JSON tasks with priorities from 0 to 9
- Schedules immediate or delayed execution in a Redis sorted set
- Atomically claims one available task with a Lua script
- Tracks `QUEUED`, `PROCESSING`, `COMPLETED`, and `DEAD_LETTER` states
- Retries failed tasks with exponential backoff
- Exposes queue depth and task lookup APIs
- Publishes Spring Boot health, metrics, and Prometheus endpoints
- Runs locally with Docker Compose

## Architecture

```text
Producer -> REST API -> Redis sorted set
                         |
Worker  <- atomic claim -+
   |
   +-> complete
   +-> fail -> delayed retry -> dead letter
```

Task metadata is stored in a Redis hash. Queue membership is stored in a sorted set where the score combines availability time and priority. A Lua script selects and removes an available task in one Redis operation, preventing two workers from claiming the same item. Stale queue entries with missing metadata are skipped during a claim.

## Run it

```bash
docker compose up --build
```

The API is available at `http://localhost:8080`.

## API examples

Create a task:

```bash
curl -X POST http://localhost:8080/tasks \
  -H 'Content-Type: application/json' \
  -d '{"type":"email.digest","payload":{"accountId":"123"},"priority":8}'
```

Claim work:

```bash
curl -X POST http://localhost:8080/tasks/claim \
  -H 'Content-Type: application/json' \
  -d '{"workerId":"worker-1"}'
```

Complete or fail:

```bash
curl -X POST http://localhost:8080/tasks/{taskId}/complete

curl -X POST http://localhost:8080/tasks/{taskId}/fail \
  -H 'Content-Type: application/json' \
  -d '{"reason":"upstream timeout"}'
```

Inspect:

```bash
curl http://localhost:8080/tasks/{taskId}
curl http://localhost:8080/queue/stats
curl http://localhost:8080/actuator/prometheus
```

## Design choices

- **Sorted set scheduling:** supports delayed tasks and ordering without polling multiple lists.
- **Lua-based claim:** makes selection and removal atomic.
- **At-least-once delivery:** workers must make handlers idempotent.
- **Exponential retry:** transient failures are delayed; exhausted tasks enter a dead-letter set.
- **JSON task envelope:** keeps the queue generic while allowing typed consumers.

## Intentional limitations

This is a reference implementation, not a hosted production service. A worker crash after claiming a task can currently leave that task in `PROCESSING`; a production version should add processing leases and visibility timeouts. It should also add worker heartbeats, task cancellation, authentication, rate limits, a Redis Cluster key strategy, tracing, and integration tests against a real Redis container.

## Test

```bash
mvn test
```
