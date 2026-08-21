package cn.bugstack.application.generation;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 生成任务的可序列化持久化快照。
 *
 * <p>请求 JSON 和工件列表的访问器执行防御性复制；{@code version}、{@code leaseOwner} 和
 * {@code leaseUntil} 由仓储及 Worker 协调逻辑维护，普通调用方不应自行修改。</p>
 */
public final class GenerationJobRecord {

    private String jobId;
    private String tenantId;
    private String principalId;
    private String correlationId;
    private String idempotencyKey;
    private String requestSha256;
    private GenerationMode mode;
    private JsonNode request;
    private GenerationJobStatus status;
    private int attemptCount;
    private int maxAttempts = 2;
    private String errorCode;
    private String errorMessage;
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant updatedAt;
    private long version;
    private String leaseOwner;
    private Instant leaseUntil;
    private String terminalEventId;
    private Instant terminalEventQueuedAt;
    private List<GenerationArtifact> artifacts = new ArrayList<>();

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getPrincipalId() { return principalId; }
    public void setPrincipalId(String principalId) { this.principalId = principalId; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getRequestSha256() { return requestSha256; }
    public void setRequestSha256(String requestSha256) { this.requestSha256 = requestSha256; }
    public GenerationMode getMode() { return mode; }
    public void setMode(GenerationMode mode) { this.mode = mode; }
    public JsonNode getRequest() { return request == null ? null : request.deepCopy(); }
    public void setRequest(JsonNode request) { this.request = request == null ? null : request.deepCopy(); }
    public GenerationJobStatus getStatus() { return status; }
    public void setStatus(GenerationJobStatus status) { this.status = status; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public String getLeaseOwner() { return leaseOwner; }
    public void setLeaseOwner(String leaseOwner) { this.leaseOwner = leaseOwner; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(Instant leaseUntil) { this.leaseUntil = leaseUntil; }
    public String getTerminalEventId() { return terminalEventId; }
    public void setTerminalEventId(String terminalEventId) { this.terminalEventId = terminalEventId; }
    public Instant getTerminalEventQueuedAt() { return terminalEventQueuedAt; }
    public void setTerminalEventQueuedAt(Instant terminalEventQueuedAt) {
        this.terminalEventQueuedAt = terminalEventQueuedAt;
    }
    public List<GenerationArtifact> getArtifacts() { return new ArrayList<>(artifacts); }
    public void setArtifacts(List<GenerationArtifact> artifacts) {
        this.artifacts = artifacts == null ? new ArrayList<>() : new ArrayList<>(artifacts);
    }
}
