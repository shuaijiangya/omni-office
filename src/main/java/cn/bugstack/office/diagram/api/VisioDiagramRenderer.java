package cn.bugstack.office.diagram.api;

import cn.bugstack.office.diagram.model.DiagramDefinition;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 将图语义定义渲染为可编辑 VSDX 的渲染器。
 *
 * @author luojiang
 */
public interface VisioDiagramRenderer {

    /**
     * 生成 VSDX 文件及其默认 PNG 预览图。
     *
     * @param definition 图语义定义
     * @param vsdxPath VSDX 输出路径
     * @return 可编辑 Visio 图产物
     * @throws IOException 当输出路径无法创建或 Visio 文件无法写入时抛出
     */
    VisioDiagramArtifact render(DiagramDefinition definition, Path vsdxPath) throws IOException;

    /**
     * 生成 VSDX 文件及指定位置的 PNG 预览图。
     *
     * @param definition 图语义定义
     * @param vsdxPath VSDX 输出路径
     * @param previewPngPath PNG 预览图输出路径
     * @return 可编辑 Visio 图产物
     * @throws IOException 当输出路径无法创建或 Visio 文件无法写入时抛出
     */
    VisioDiagramArtifact render(DiagramDefinition definition, Path vsdxPath, Path previewPngPath)
            throws IOException;
}
