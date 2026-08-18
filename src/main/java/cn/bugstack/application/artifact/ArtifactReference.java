package cn.bugstack.application.artifact;

/** 可安全返回给外部调用方的工件元数据，不包含服务器文件路径。 */
public final class ArtifactReference {

    private final String artifactId;
    private final String fileName;
    private final String mediaType;
    private final long size;

    public ArtifactReference(String artifactId, String fileName, String mediaType, long size) {
        this.artifactId = artifactId;
        this.fileName = fileName;
        this.mediaType = mediaType;
        this.size = size;
    }

    public String getArtifactId() {
        return artifactId;
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
}
