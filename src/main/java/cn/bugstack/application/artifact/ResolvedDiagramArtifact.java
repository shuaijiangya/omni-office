package cn.bugstack.application.artifact;

import java.nio.file.Path;

/** 应用内部解析后的图工件；路径不会出现在外部协议中。 */
public final class ResolvedDiagramArtifact {

    private final DiagramArtifactReference reference;
    private final Path vsdxPath;
    private final Path previewPath;

    public ResolvedDiagramArtifact(DiagramArtifactReference reference, Path vsdxPath, Path previewPath) {
        this.reference = reference;
        this.vsdxPath = vsdxPath;
        this.previewPath = previewPath;
    }

    public DiagramArtifactReference getReference() {
        return reference;
    }

    public Path getVsdxPath() {
        return vsdxPath;
    }

    public Path getPreviewPath() {
        return previewPath;
    }
}
