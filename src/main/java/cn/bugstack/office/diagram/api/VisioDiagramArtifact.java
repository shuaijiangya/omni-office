package cn.bugstack.office.diagram.api;

import cn.bugstack.office.diagram.model.DiagramType;

import java.nio.file.Path;
import java.util.Objects;

/**
 * 可编辑 Visio 图的输出产物。
 *
 * <p>一个产物包含可编辑的 {@code .vsdx} 文件和用于 Word OLE 展示的 PNG 预览图。
 * 二者必须由同一份 {@code DiagramDefinition} 生成，以保证预览与编辑内容一致。</p>
 *
 * @author luojiang
 */
public final class VisioDiagramArtifact {

    /** 图类型。 */
    private final DiagramType type;
    /** 可编辑 VSDX 文件路径。 */
    private final Path vsdxPath;
    /** Word OLE 对象使用的 PNG 预览图路径。 */
    private final Path previewPngPath;

    /**
     * 创建可编辑 Visio 图产物。
     *
     * @param type 图类型
     * @param vsdxPath 可编辑 VSDX 文件路径
     * @param previewPngPath Word OLE 预览 PNG 文件路径
     */
    public VisioDiagramArtifact(DiagramType type, Path vsdxPath, Path previewPngPath) {
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.vsdxPath = Objects.requireNonNull(vsdxPath, "vsdxPath must not be null");
        this.previewPngPath = Objects.requireNonNull(previewPngPath, "previewPngPath must not be null");
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
     * 获取可编辑 VSDX 文件路径。
     *
     * @return VSDX 文件路径
     */
    public Path getVsdxPath() {
        return vsdxPath;
    }

    /**
     * 获取用于 Word OLE 展示的 PNG 预览图路径。
     *
     * @return PNG 预览图路径
     */
    public Path getPreviewPngPath() {
        return previewPngPath;
    }
}
