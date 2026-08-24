package cn.bugstack.application.document;

import cn.bugstack.application.artifact.DiagramArtifactReference;
import cn.bugstack.application.artifact.DiagramArtifactStore;
import cn.bugstack.application.artifact.ResolvedDiagramArtifact;
import cn.bugstack.application.diagram.DiagramGenerationService;
import cn.bugstack.export.document.CaptionTargetType;
import cn.bugstack.export.document.CaptionPosition;
import cn.bugstack.export.document.ReportCaption;
import cn.bugstack.export.document.ReportDiagram;
import cn.bugstack.export.document.ReportDiagramEmbedMode;
import cn.bugstack.protocol.document.block.DiagramBlockSpec;

/** 支持内联 DiagramSpec 与已有工件引用的默认图块解析器。 */
public final class DefaultDiagramBlockResolver implements DiagramBlockResolver {

    private final DiagramGenerationService generationService;
    private final DiagramArtifactStore artifactStore;

    public DefaultDiagramBlockResolver(DiagramGenerationService generationService,
                                       DiagramArtifactStore artifactStore) {
        if (generationService == null || artifactStore == null) {
            throw new IllegalArgumentException("diagram block resolver dependencies must not be null");
        }
        this.generationService = generationService;
        this.artifactStore = artifactStore;
    }

    @Override
    public ReportDiagram resolve(DiagramBlockSpec block) {
        String artifactId = block.getDiagramArtifactId();
        if (block.getDefinition() != null) {
            DiagramArtifactReference generated = generationService.generate(block.getDefinition());
            artifactId = generated.getDiagramArtifactId();
        }
        ResolvedDiagramArtifact artifact = artifactStore.resolve(artifactId);
        ReportDiagram diagram = new ReportDiagram();
        diagram.setVsdxSource(artifact.getVsdxPath().toString());
        diagram.setPreviewSource(artifact.getPreviewPath().toString());
        diagram.setEmbedMode(ReportDiagramEmbedMode.valueOf(block.getEmbedMode()));
        if (block.getMaxWidthPoints() != null) {
            diagram.setMaxWidthPoints(block.getMaxWidthPoints());
            diagram.setMaxHeightPoints(block.getMaxHeightPoints());
        }
        if (hasText(block.getCaption())) {
            ReportCaption caption = new ReportCaption(CaptionTargetType.IMAGE, block.getCaption().trim());
            caption.setAutoNumbered(true);
            caption.setPosition(CaptionPosition.valueOf(block.getCaptionPosition()));
            diagram.setCaption(caption);
        }
        return diagram;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
