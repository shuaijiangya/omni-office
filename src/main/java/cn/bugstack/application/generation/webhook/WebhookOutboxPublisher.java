package cn.bugstack.application.generation.webhook;

import cn.bugstack.application.generation.GenerationArtifact;
import cn.bugstack.application.generation.GenerationEventPublisher;
import cn.bugstack.application.generation.GenerationJobRecord;
import cn.bugstack.application.generation.GenerationJobStatus;
import cn.bugstack.application.generation.GenerationJobRepository;
import cn.bugstack.application.generation.PostgresGenerationJobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** 将安全的任务终态摘要幂等写入 Outbox，不复制业务请求正文。 */
public final class WebhookOutboxPublisher implements GenerationEventPublisher {

    private final WebhookEndpointRegistry endpoints;
    private final WebhookDeliveryRepository repository;
    private final Clock clock;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 创建任务终态 Outbox 发布器。
     *
     * @param endpoints 预注册 Webhook 目录
     * @param repository Webhook Outbox 仓储
     */
    public WebhookOutboxPublisher(WebhookEndpointRegistry endpoints,
                                  WebhookDeliveryRepository repository) {
        this(endpoints, repository, Clock.systemUTC());
    }

    WebhookOutboxPublisher(WebhookEndpointRegistry endpoints, WebhookDeliveryRepository repository,
                           Clock clock) {
        if (endpoints == null || repository == null || clock == null) {
            throw new IllegalArgumentException("webhook outbox dependencies are required");
        }
        this.endpoints = endpoints;
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public void validateWebhook(String tenantId, String webhookId) {
        if (webhookId != null) endpoints.require(tenantId, webhookId);
    }

    @Override
    public String enqueueTerminal(GenerationJobRecord job) {
        String webhookId = webhookId(job);
        if (webhookId == null) return null;
        if (job.getStatus() == null || !job.getStatus().isTerminal()) {
            throw new IllegalArgumentException("only terminal generation jobs can enqueue webhook events");
        }
        String eventType = eventType(job.getStatus());
        java.util.Optional<WebhookDeliveryRecord> existing = repository.findByEventKey(
                job.getTenantId(), job.getJobId(), eventType);
        if (existing.isPresent()) return existing.get().getEventId();
        WebhookDeliveryRecord event = prepareTerminal(job);
        return repository.enqueue(event).getEventId();
    }

    @Override
    public GenerationJobRecord commitClaimedTerminal(GenerationJobRepository jobs,
                                                      GenerationJobRecord job, String workerId) {
        if (jobs instanceof PostgresGenerationJobRepository
                && repository instanceof PostgresWebhookDeliveryRepository) {
            WebhookDeliveryRecord event = webhookId(job) == null ? null : prepareTerminal(job);
            return ((PostgresGenerationJobRepository) jobs).commitClaimedTerminal(
                    job, workerId, event, (PostgresWebhookDeliveryRepository) repository);
        }
        return GenerationEventPublisher.super.commitClaimedTerminal(jobs, job, workerId);
    }

    @Override
    public GenerationJobRecord commitTerminal(GenerationJobRepository jobs, GenerationJobRecord job) {
        if (jobs instanceof PostgresGenerationJobRepository
                && repository instanceof PostgresWebhookDeliveryRepository) {
            WebhookDeliveryRecord event = webhookId(job) == null ? null : prepareTerminal(job);
            return ((PostgresGenerationJobRepository) jobs).commitTerminal(
                    job, event, (PostgresWebhookDeliveryRepository) repository);
        }
        return GenerationEventPublisher.super.commitTerminal(jobs, job);
    }

    private WebhookDeliveryRecord prepareTerminal(GenerationJobRecord job) {
        String webhookId = webhookId(job);
        if (webhookId == null) throw new IllegalArgumentException("generation job has no webhook");
        if (job.getStatus() == null || !job.getStatus().isTerminal()) {
            throw new IllegalArgumentException("only terminal generation jobs can enqueue webhook events");
        }
        String eventType = eventType(job.getStatus());
        Instant now = clock.instant();
        WebhookDeliveryRecord event = new WebhookDeliveryRecord();
        event.setEventId(UUID.randomUUID().toString());
        event.setEventType(eventType);
        event.setTenantId(job.getTenantId());
        event.setWebhookId(webhookId);
        event.setJobId(job.getJobId());
        event.setPayload(payload(job, event.getEventId(), eventType));
        event.setStatus(WebhookDeliveryStatus.PENDING);
        event.setNextAttemptAt(now);
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        return event;
    }

    private ObjectNode payload(GenerationJobRecord job, String eventId, String eventType) {
        ObjectNode value = mapper.createObjectNode();
        value.put("eventId", eventId);
        value.put("eventType", eventType);
        value.put("occurredAt", clock.instant().toString());
        ObjectNode data = value.putObject("data");
        data.put("jobId", job.getJobId());
        data.put("tenantId", job.getTenantId());
        data.put("correlationId", job.getCorrelationId());
        data.put("mode", job.getMode().name());
        data.put("status", job.getStatus().name());
        data.put("attemptCount", job.getAttemptCount());
        if (job.getErrorCode() != null) data.put("errorCode", job.getErrorCode());
        if (job.getCompletedAt() != null) data.put("completedAt", job.getCompletedAt().toString());
        ArrayNode artifacts = data.putArray("artifacts");
        for (GenerationArtifact artifact : job.getArtifacts()) {
            ObjectNode item = artifacts.addObject();
            item.put("artifactId", artifact.getArtifactId());
            item.put("resourceUri", artifact.getResourceUri());
            item.put("fileName", artifact.getFileName());
            item.put("mediaType", artifact.getMediaType());
            item.put("size", artifact.getSize());
            item.put("sha256", artifact.getSha256());
            if (artifact.getExpiresAt() != null) item.put("expiresAt", artifact.getExpiresAt().toString());
        }
        return value;
    }

    private String webhookId(GenerationJobRecord job) {
        if (job == null || job.getRequest() == null) return null;
        String value = job.getRequest().path("webhookId").asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private String eventType(GenerationJobStatus status) {
        switch (status) {
            case SUCCEEDED: return "generation.succeeded";
            case FAILED: return "generation.failed";
            case CANCELLED: return "generation.cancelled";
            default: throw new IllegalArgumentException("unsupported terminal job status");
        }
    }
}
