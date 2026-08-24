package cn.bugstack.application.external;

import java.time.Instant;

/** 可安全返回给外部 Function Calling 或 MCP 客户端的生成工件元数据。 */
public final class ExternalArtifactReference {

    private final String artifactId;
    private final String resourceUri;
    private final String fileName;
    private final String mediaType;
    private final long size;
    private final String sha256;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final String ownerPrincipalId;

    public ExternalArtifactReference(String artifactId, String resourceUri, String fileName,
                                     String mediaType, long size, String sha256) {
        this(artifactId, resourceUri, fileName, mediaType, size, sha256, null, null);
    }

    public ExternalArtifactReference(String artifactId, String resourceUri, String fileName,
                                     String mediaType, long size, String sha256,
                                     Instant createdAt, Instant expiresAt) {
        this.artifactId = artifactId;
        this.resourceUri = resourceUri;
        this.fileName = fileName;
        this.mediaType = mediaType;
        this.size = size;
        this.sha256 = sha256;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.ownerPrincipalId = null;
    }

    public ExternalArtifactReference(String artifactId, String resourceUri, String fileName,
                                     String mediaType, long size, String sha256,
                                     Instant createdAt, Instant expiresAt, String ownerPrincipalId) {
        this.artifactId = artifactId;
        this.resourceUri = resourceUri;
        this.fileName = fileName;
        this.mediaType = mediaType;
        this.size = size;
        this.sha256 = sha256;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.ownerPrincipalId = ownerPrincipalId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getResourceUri() {
        return resourceUri;
    }

    public String getFileName() {
        return fileName;
    }

    public String getMediaType() {
        return mediaType;
    }

    public long getSize() {
        return size;
    }

    public String getSha256() {
        return sha256;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * 返回工件所属主体。旧版工件没有该元数据时返回 {@code null}，只能由拥有跨主体权限的调用方读取。
     *
     * @return 所属主体 ID，或 {@code null}
     */
    public String getOwnerPrincipalId() {
        return ownerPrincipalId;
    }
}
