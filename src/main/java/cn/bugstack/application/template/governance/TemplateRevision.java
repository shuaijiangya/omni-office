package cn.bugstack.application.template.governance;

import cn.bugstack.protocol.template.DocumentTemplateSpec;

import java.time.Instant;

/** 模板定义及其审核元数据快照。 */
public final class TemplateRevision {

    private DocumentTemplateSpec template;
    private TemplateLifecycleStatus status;
    private String createdBy;
    private String reviewedBy;
    private String reviewComment;
    private Instant createdAt;
    private Instant updatedAt;
    private String sampleDataSha256;
    private String renderedSha256;
    private long renderedSize;
    private Instant publicationValidatedAt;

    public DocumentTemplateSpec getTemplate() { return template; }
    public void setTemplate(DocumentTemplateSpec template) { this.template = template; }
    public TemplateLifecycleStatus getStatus() { return status; }
    public void setStatus(TemplateLifecycleStatus status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public String getSampleDataSha256() { return sampleDataSha256; }
    public void setSampleDataSha256(String sampleDataSha256) { this.sampleDataSha256 = sampleDataSha256; }
    public String getRenderedSha256() { return renderedSha256; }
    public void setRenderedSha256(String renderedSha256) { this.renderedSha256 = renderedSha256; }
    public long getRenderedSize() { return renderedSize; }
    public void setRenderedSize(long renderedSize) { this.renderedSize = renderedSize; }
    public Instant getPublicationValidatedAt() { return publicationValidatedAt; }
    public void setPublicationValidatedAt(Instant publicationValidatedAt) { this.publicationValidatedAt = publicationValidatedAt; }
}
