package cn.bugstack.protocol.document.block;

import cn.bugstack.protocol.diagram.DiagramSpec;

/**
 * 图形制品引用块。
 *
 * <p>{@code diagramArtifactId} 与 {@code definition} 二选一：前者复用已生成工件，
 * 后者在导出时按 DiagramSpec 即时生成。</p>
 */
public final class DiagramBlockSpec extends BlockSpec {

    private String diagramArtifactId;
    private DiagramSpec definition;
    private String embedMode = "EDITABLE_VISIO";
    private String caption;
    private Double maxWidthPoints;
    private Double maxHeightPoints;

    public String getDiagramArtifactId() {
        return diagramArtifactId;
    }

    public void setDiagramArtifactId(String diagramArtifactId) {
        this.diagramArtifactId = diagramArtifactId;
    }

    public DiagramSpec getDefinition() {
        return definition;
    }

    public void setDefinition(DiagramSpec definition) {
        this.definition = definition;
    }

    public String getEmbedMode() {
        return embedMode;
    }

    public void setEmbedMode(String embedMode) {
        this.embedMode = embedMode;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public Double getMaxWidthPoints() {
        return maxWidthPoints;
    }

    public void setMaxWidthPoints(Double maxWidthPoints) {
        this.maxWidthPoints = maxWidthPoints;
    }

    public Double getMaxHeightPoints() {
        return maxHeightPoints;
    }

    public void setMaxHeightPoints(Double maxHeightPoints) {
        this.maxHeightPoints = maxHeightPoints;
    }
}
