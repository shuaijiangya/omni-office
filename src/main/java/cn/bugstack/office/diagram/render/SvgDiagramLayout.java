package cn.bugstack.office.diagram.render;

import cn.bugstack.office.diagram.model.DiagramDefinition;
import cn.bugstack.office.diagram.model.DiagramType;

/**
 * 指定图类型的 SVG 布局策略。
 */
public interface SvgDiagramLayout {

    /**
     * 获取当前布局支持的图类型。
     *
     * @return 图类型
     */
    DiagramType supportedType();

    /**
     * 根据图语义定义生成 SVG 文本。
     *
     * @param definition 图语义定义
     * @return 完整 SVG 文本
     */
    String render(DiagramDefinition definition);
}
