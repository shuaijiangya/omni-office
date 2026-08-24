package cn.bugstack.application.artifact;

import cn.bugstack.protocol.diagram.DiagramTypeSpec;

import java.nio.file.Path;
import java.time.Instant;

/** 图工件存储与解析 SPI。 */
public interface DiagramArtifactStore {

    DiagramArtifactReference store(DiagramTypeSpec type, Path vsdxSource, Path previewSource);

    ResolvedDiagramArtifact resolve(String diagramArtifactId);

    /** 删除指定图工件；存储实现不支持删除时返回 {@code false}。 */
    default boolean delete(String diagramArtifactId) {
        return false;
    }

    /** 清理指定时刻已经过期的图工件。 */
    default int purgeExpired(Instant now) {
        return 0;
    }
}
