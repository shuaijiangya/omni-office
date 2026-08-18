package cn.bugstack.application.artifact;

import cn.bugstack.protocol.diagram.DiagramTypeSpec;

import java.nio.file.Path;

/** 图工件存储与解析 SPI。 */
public interface DiagramArtifactStore {

    DiagramArtifactReference store(DiagramTypeSpec type, Path vsdxSource, Path previewSource);

    ResolvedDiagramArtifact resolve(String diagramArtifactId);
}
