package cn.bugstack.application.generation;

import cn.bugstack.application.external.ExternalArtifactReference;

import java.time.Instant;

/** 生成任务保存的安全工件快照，不包含服务端文件路径。 */
public final class GenerationArtifact {

    private String artifactId;
    private String resourceUri;
    private String fileName;
    private String mediaType;
    private long size;
    private String sha256;
    private Instant createdAt;
    private Instant expiresAt;

    /** 创建供 JSON 反序列化使用的空工件快照。 */
    public GenerationArtifact() {
    }

    /**
     * 从外部工件引用创建安全快照。
     *
     * @param source 外部工件引用
     * @return 不包含服务端路径的工件快照
     */
    public static GenerationArtifact from(ExternalArtifactReference source) {
        GenerationArtifact value = new GenerationArtifact();
        value.artifactId = source.getArtifactId();
        value.resourceUri = source.getResourceUri();
        value.fileName = source.getFileName();
        value.mediaType = source.getMediaType();
        value.size = source.getSize();
        value.sha256 = source.getSha256();
        value.createdAt = source.getCreatedAt();
        value.expiresAt = source.getExpiresAt();
        return value;
    }

    public String getArtifactId() { return artifactId; }
    public void setArtifactId(String artifactId) { this.artifactId = artifactId; }
    public String getResourceUri() { return resourceUri; }
    public void setResourceUri(String resourceUri) { this.resourceUri = resourceUri; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
