package cn.bugstack.office.diagram.render;

import cn.bugstack.office.diagram.api.DiagramArtifact;
import cn.bugstack.office.diagram.api.DiagramRenderer;
import cn.bugstack.office.diagram.model.DiagramDefinition;
import cn.bugstack.office.diagram.model.DiagramType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/**
 * 基于内置布局策略生成标准 SVG 文件的渲染器。
 */
public final class SvgDiagramRenderer implements DiagramRenderer {

    /** 按图类型索引的 SVG 布局策略。 */
    private final Map<DiagramType, SvgDiagramLayout> layouts = new EnumMap<>(DiagramType.class);

    /**
     * 创建包含用例图、流程图、数据库 ER 图和系统 E-R 图布局策略的渲染器。
     */
    public SvgDiagramRenderer() {
        register(new UseCaseSvgLayout());
        register(new FlowSvgLayout());
        register(new ErSvgLayout());
        register(new SystemErSvgLayout());
    }

    /**
     * 注册或替换指定图类型的 SVG 布局策略。
     *
     * @param layout SVG 布局策略
     * @return 当前渲染器
     */
    public SvgDiagramRenderer register(SvgDiagramLayout layout) {
        if (layout == null || layout.supportedType() == null) {
            throw new IllegalArgumentException("svg diagram layout and supported type must not be null");
        }
        layouts.put(layout.supportedType(), layout);
        return this;
    }

    /**
     * 将图定义渲染为 SVG 文本。
     *
     * @param definition 图语义定义
     * @return 完整 SVG 文本
     */
    @Override
    public String renderToSvg(DiagramDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("diagram definition must not be null");
        }
        SvgDiagramLayout layout = layouts.get(definition.getType());
        if (layout == null) {
            throw new IllegalStateException("no svg layout registered for diagram type: " + definition.getType());
        }
        return layout.render(definition);
    }

    /**
     * 将图定义渲染并写入 UTF-8 SVG 文件。
     *
     * @param definition 图语义定义
     * @param outputPath SVG 输出路径
     * @return SVG 文件产物
     * @throws IOException 创建目录或写入文件失败时抛出
     */
    @Override
    public DiagramArtifact render(DiagramDefinition definition, Path outputPath) throws IOException {
        if (outputPath == null) {
            throw new IllegalArgumentException("svg diagram output path must not be null");
        }
        Path parent = outputPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(outputPath, renderToSvg(definition), StandardCharsets.UTF_8);
        return new DiagramArtifact(definition.getType(), outputPath);
    }
}
