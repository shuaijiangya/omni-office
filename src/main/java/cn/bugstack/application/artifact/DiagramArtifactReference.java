package cn.bugstack.application.artifact;

import cn.bugstack.protocol.diagram.DiagramTypeSpec;

/** 一组可编辑 VSDX 与同源 PNG 预览图的外部引用。 */
public final class DiagramArtifactReference {

    private final String diagramArtifactId;
    private final DiagramTypeSpec type;
    private final ArtifactReference vsdx;
    private final ArtifactReference preview;

    public DiagramArtifactReference(String diagramArtifactId, DiagramTypeSpec type,
                                    ArtifactReference vsdx, ArtifactReference preview) {
        this.diagramArtifactId = diagramArtifactId;
        this.type = type;
        this.vsdx = vsdx;
        this.preview = preview;
    }

    public String getDiagramArtifactId() {
        return diagramArtifactId;
    }

    public DiagramTypeSpec getType() {
        return type;
    }

    public ArtifactReference getVsdx() {
        return vsdx;
    }

    public ArtifactReference getPreview() {
        return preview;
    }
}
