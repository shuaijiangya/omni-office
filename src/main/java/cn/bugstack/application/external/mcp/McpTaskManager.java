package cn.bugstack.application.external.mcp;

import cn.bugstack.application.concurrent.BoundedExecutors;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

/** MCP 2025-11-25 实验性 Tasks 状态机。实例本身即授权上下文边界。 */
final class McpTaskManager implements AutoCloseable {

    private static final long DEFAULT_TTL = 60 * 60 * 1000L;
    private static final long MAX_TTL = 24 * 60 * 60 * 1000L;
    private static final long POLL_INTERVAL = 1_000L;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final ExecutorService executor = BoundedExecutors.fixed(2, 100,
            "omni-mcp-task", new ThreadPoolExecutor.AbortPolicy());
    private final ConcurrentMap<String, TaskRecord> tasks = new ConcurrentHashMap<>();

    McpTaskManager(ObjectMapper mapper) {
        this(mapper, Clock.systemUTC());
    }

    McpTaskManager(ObjectMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    ObjectNode submit(Supplier<ObjectNode> operation, long requestedTtl) {
        cleanup();
        long ttl = requestedTtl <= 0 ? DEFAULT_TTL : Math.min(requestedTtl, MAX_TTL);
        if (ttl < 1_000) throw new IllegalArgumentException("task ttl must be at least 1000 milliseconds");
        Instant now = clock.instant();
        TaskRecord task = new TaskRecord(UUID.randomUUID().toString(), now, ttl);
        tasks.put(task.id, task);
        try {
            task.future = executor.submit(() -> execute(task, operation));
        } catch (RejectedExecutionException capacityExceeded) {
            tasks.remove(task.id, task);
            throw new IllegalStateException("MCP task queue capacity is exhausted", capacityExceeded);
        }
        ObjectNode result = mapper.createObjectNode();
        result.set("task", taskNode(task));
        ObjectNode metadata = result.putObject("_meta");
        metadata.put("io.modelcontextprotocol/model-immediate-response",
                "Document generation is running asynchronously. Poll the returned task ID.");
        metadata.putObject("io.modelcontextprotocol/related-task").put("taskId", task.id);
        return result;
    }

    ObjectNode get(String taskId) {
        return taskNode(require(taskId));
    }

    ObjectNode result(String taskId) {
        TaskRecord task = require(taskId);
        Future<?> future = task.future;
        if (future != null && !terminal(task.status)) {
            try {
                future.get(Math.max(1, task.ttl), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("task result wait interrupted", e);
            } catch (TimeoutException | ExecutionException e) {
                if (!terminal(task.status)) fail(task, "Task result wait failed: " + e.getClass().getSimpleName());
            }
        }
        if (task.result == null) {
            ObjectNode value = mapper.createObjectNode();
            value.put("isError", true);
            value.putArray("content").addObject().put("type", "text")
                    .put("text", task.statusMessage == null ? "Task did not produce a result" : task.statusMessage);
            task.result = value;
        }
        ObjectNode value = task.result.deepCopy();
        value.putObject("_meta").putObject("io.modelcontextprotocol/related-task").put("taskId", task.id);
        return value;
    }

    ObjectNode cancel(String taskId) {
        TaskRecord task = require(taskId);
        synchronized (task) {
            if (terminal(task.status)) throw new IllegalArgumentException(
                    "cannot cancel task already in terminal status: " + task.status);
            task.status = "cancelled";
            task.statusMessage = "The task was cancelled by request.";
            task.updatedAt = clock.instant();
            if (task.future != null) task.future.cancel(true);
        }
        return taskNode(task);
    }

    ObjectNode list(boolean enabled) {
        if (!enabled) throw new UnsupportedOperationException("tasks/list requires an authenticated context");
        cleanup();
        List<TaskRecord> values = new ArrayList<>(tasks.values());
        values.sort(Comparator.comparing(task -> task.createdAt));
        ArrayNode array = mapper.createArrayNode();
        values.stream().limit(100).forEach(task -> array.add(taskNode(task)));
        return mapper.createObjectNode().set("tasks", array);
    }

    private void execute(TaskRecord task, Supplier<ObjectNode> operation) {
        try {
            ObjectNode result = operation.get();
            synchronized (task) {
                if ("cancelled".equals(task.status)) return;
                task.result = result;
                task.status = result.path("isError").asBoolean(false) ? "failed" : "completed";
                task.statusMessage = "completed".equals(task.status)
                        ? "The operation completed successfully." : "Tool execution returned an error.";
                task.updatedAt = clock.instant();
            }
        } catch (RuntimeException e) {
            fail(task, "Tool execution failed: " + safe(e.getMessage()));
        }
    }

    private void fail(TaskRecord task, String message) {
        synchronized (task) {
            if ("cancelled".equals(task.status)) return;
            task.status = "failed";
            task.statusMessage = message;
            task.updatedAt = clock.instant();
            ObjectNode result = mapper.createObjectNode();
            result.put("isError", true);
            result.putArray("content").addObject().put("type", "text").put("text", message);
            task.result = result;
        }
    }

    private TaskRecord require(String taskId) {
        cleanup();
        if (taskId == null || taskId.isBlank()) throw new IllegalArgumentException("taskId is required");
        TaskRecord task = tasks.get(taskId);
        if (task == null) throw new IllegalArgumentException("task not found or expired");
        return task;
    }

    private ObjectNode taskNode(TaskRecord task) {
        ObjectNode value = mapper.createObjectNode();
        value.put("taskId", task.id);
        value.put("status", task.status);
        if (task.statusMessage != null) value.put("statusMessage", task.statusMessage);
        value.put("createdAt", task.createdAt.toString());
        value.put("lastUpdatedAt", task.updatedAt.toString());
        value.put("ttl", task.ttl);
        value.put("pollInterval", POLL_INTERVAL);
        return value;
    }

    private void cleanup() {
        Instant now = clock.instant();
        tasks.forEach((id, task) -> {
            if (!task.createdAt.plusMillis(task.ttl).isAfter(now)) tasks.remove(id, task);
        });
    }

    private boolean terminal(String status) {
        return "completed".equals(status) || "failed".equals(status) || "cancelled".equals(status);
    }

    @Override
    public void close() {
        tasks.values().forEach(task -> {
            if (task.future != null && !terminal(task.status)) task.future.cancel(true);
        });
        executor.shutdownNow();
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) return "unknown error";
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    private static final class TaskRecord {
        private final String id;
        private final Instant createdAt;
        private final long ttl;
        private volatile Instant updatedAt;
        private volatile String status = "working";
        private volatile String statusMessage = "The operation is now in progress.";
        private volatile ObjectNode result;
        private volatile Future<?> future;

        private TaskRecord(String id, Instant createdAt, long ttl) {
            this.id = id;
            this.createdAt = createdAt;
            this.updatedAt = createdAt;
            this.ttl = ttl;
        }
    }
}
