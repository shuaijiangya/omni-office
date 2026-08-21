package cn.bugstack.application.generation.webhook;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * 可序列化的持久化 Outbox 记录；请求正文不包含原始业务输入。
 *
 * <p>事件载荷访问器执行防御性复制；版本号和租约字段由投递仓储维护。</p>
 */
public final class WebhookDeliveryRecord {

    private String eventId;
    private String eventType;
    private String tenantId;
    private String webhookId;
    private String jobId;
    private JsonNode payload;
    private WebhookDeliveryStatus status;
    private int attemptCount;
    private int maxAttempts = 8;
    private Integer responseStatus;
    private String lastError;
    private Instant nextAttemptAt;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deliveredAt;
    private long version;
    private String leaseOwner;
    private Instant leaseUntil;

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getWebhookId() { return webhookId; }
    public void setWebhookId(String webhookId) { this.webhookId = webhookId; }
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public JsonNode getPayload() { return payload == null ? null : payload.deepCopy(); }
    public void setPayload(JsonNode payload) { this.payload = payload == null ? null : payload.deepCopy(); }
    public WebhookDeliveryStatus getStatus() { return status; }
    public void setStatus(WebhookDeliveryStatus status) { this.status = status; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public Integer getResponseStatus() { return responseStatus; }
    public void setResponseStatus(Integer responseStatus) { this.responseStatus = responseStatus; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Instant deliveredAt) { this.deliveredAt = deliveredAt; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public String getLeaseOwner() { return leaseOwner; }
    public void setLeaseOwner(String leaseOwner) { this.leaseOwner = leaseOwner; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(Instant leaseUntil) { this.leaseUntil = leaseUntil; }
}
