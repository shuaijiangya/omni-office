package cn.bugstack.application.diagram;

import cn.bugstack.application.artifact.DiagramArtifactReference;
import cn.bugstack.protocol.diagram.DiagramSpec;

/** 从 DiagramSpec 生成并持久化 VSDX/PNG 工件。 */
public interface DiagramGenerationService {

    DiagramArtifactReference generate(DiagramSpec spec);
}
