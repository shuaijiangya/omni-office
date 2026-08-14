package cn.bugstack.office.diagram.api;

import cn.bugstack.office.diagram.model.DiagramDefinition;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 设计图渲染策略。
 */
public interface DiagramRenderer {

    /**
     * 将图语义定义渲染为 SVG 文本。
     *
     * @param definition 图语义定义
     * @return 完整 SVG 文本
     */
    String renderToSvg(DiagramDefinition definition);

    /**
     * 将图语义定义渲染并写入 SVG 文件。
     *
     * @param definition 图语义定义
     * @param outputPath SVG 输出路径
     * @return SVG 文件产物
     * @throws IOException 创建目录或写入文件失败时抛出
     */
    DiagramArtifact render(DiagramDefinition definition, Path outputPath) throws IOException;
}
