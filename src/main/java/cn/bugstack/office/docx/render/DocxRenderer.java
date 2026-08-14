package cn.bugstack.office.docx.render;

import cn.bugstack.office.docx.model.DocumentNode;

import java.nio.file.Path;

/**
 * docx 组件树渲染器接口。
 */
public interface DocxRenderer {

    /**
     * 将内部文档节点树渲染到指定路径。
     *
     * @param document 文档根节点
     * @param outputPath 输出 docx 文件路径
     */
    void render(DocumentNode document, Path outputPath);
}
