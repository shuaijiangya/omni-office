package cn.bugstack.application.external;

import java.nio.file.Path;

/** 仅供服务端读取的工件解析结果，真实路径不会写入外部协议。 */
public final class ResolvedExternalArtifact {

    private final ExternalArtifactReference reference;
    private final Path contentPath;

    public ResolvedExternalArtifact(ExternalArtifactReference reference, Path contentPath) {
        this.reference = reference;
        this.contentPath = contentPath;
    }

    public ExternalArtifactReference getReference() {
        return reference;
    }

    public Path getContentPath() {
        return contentPath;
    }
}
