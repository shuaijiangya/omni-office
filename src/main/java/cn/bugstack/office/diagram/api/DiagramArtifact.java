package cn.bugstack.office.diagram.api;

import cn.bugstack.office.diagram.model.DiagramType;

import java.nio.file.Path;

/**
 * 设计图渲染生成的文件产物。
 */
public final class DiagramArtifact {

    /** 图类型。 */
    private final DiagramType type;
    /** SVG 文件路径。 */
    private final Path svgPath;

    /**
     * 创建设计图文件产物。
     *
     * @param type 图类型
     * @param svgPath SVG 文件路径
     */
    public DiagramArtifact(DiagramType type, Path svgPath) {
        if (type == null || svgPath == null) {
            throw new IllegalArgumentException("diagram artifact type and svg path must not be null");
        }
        this.type = type;
        this.svgPath = svgPath;
    }

    /**
     * 获取图类型。
     *
     * @return 图类型
     */
    public DiagramType getType() {
        return type;
    }

    /**
     * 获取生成的 SVG 文件路径。
     *
     * @return SVG 文件路径
     */
    public Path getSvgPath() {
        return svgPath;
    }
}
