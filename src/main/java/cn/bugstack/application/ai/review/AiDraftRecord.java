package cn.bugstack.application.ai.review;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/** 可审计的 AI 文档草稿快照。 */
public final class AiDraftRecord {

    private String draftId;
    private String mode;
    private String templateId;
    private String templateVersion;
    private int attempts;
    private JsonNode documentSpec;
    private AiDraftStatus status;
    private String requestedBy;
    private String reviewedBy;
    private String reviewComment;
    private Instant createdAt;
    private Instant reviewedAt;

    public String getDraftId() { return draftId; }
    public void setDraftId(String draftId) { this.draftId = draftId; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }
    public String getTemplateVersion() { return templateVersion; }
    public void setTemplateVersion(String templateVersion) { this.templateVersion = templateVersion; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public JsonNode getDocumentSpec() { return documentSpec == null ? null : documentSpec.deepCopy(); }
    public void setDocumentSpec(JsonNode documentSpec) { this.documentSpec = documentSpec == null ? null : documentSpec.deepCopy(); }
    public AiDraftStatus getStatus() { return status; }
    public void setStatus(AiDraftStatus status) { this.status = status; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
}
