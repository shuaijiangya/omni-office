package cn.bugstack.office.diagram.render;

import cn.bugstack.office.diagram.model.DiagramDefinition;
import cn.bugstack.office.diagram.model.DiagramEdge;
import cn.bugstack.office.diagram.model.DiagramNode;
import cn.bugstack.office.diagram.model.DiagramType;
import cn.bugstack.office.diagram.model.SvgDiagramOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * 数据库 ER 图的网格 SVG 布局策略。
 */
public final class ErSvgLayout extends AbstractSvgDiagramLayout {

    /** 单个实体卡片宽度。 */
    private static final int ENTITY_WIDTH = 250;
    /** 实体标题区域高度。 */
    private static final int ENTITY_HEADER_HEIGHT = 34;
    /** 每个字段占用的高度。 */
    private static final int FIELD_HEIGHT = 22;

    /**
     * 获取该布局支持的图类型。
     *
     * @return ER 图类型
     */
    @Override
    public DiagramType supportedType() {
        return DiagramType.ER;
    }

    /**
     * 以网格方式生成实体关系图。
     *
     * @param definition ER 图定义
     * @return SVG 文本
     */
    @Override
    public String render(DiagramDefinition definition) {
        SvgDiagramOptions options = definition.getOptions();
        int columns = Math.max(1, Math.min(3, (options.getWidth() - PADDING * 2) / (ENTITY_WIDTH + 48)));
        int rows = (definition.getNodes().size() + columns - 1) / columns;
        int height = Math.max(options.getHeight(), 100 + rows * 220);
        Map<String, Point> positions = new HashMap<>();
        Map<String, Point> dimensions = new HashMap<>();
        StringBuilder svg = new StringBuilder(begin(definition, height));
        for (int index = 0; index < definition.getNodes().size(); index++) {
            DiagramNode node = definition.getNodes().get(index);
            int column = index % columns;
            int row = index / columns;
            int x = PADDING + column * (ENTITY_WIDTH + 48);
            int y = 72 + row * 210;
            int entityHeight = ENTITY_HEADER_HEIGHT + Math.max(1, node.getFields().size()) * FIELD_HEIGHT;
            renderEntity(svg, node, x, y, entityHeight, options);
            positions.put(node.getId(), new Point(x + ENTITY_WIDTH / 2, y + entityHeight / 2));
            dimensions.put(node.getId(), new Point(ENTITY_WIDTH / 2, entityHeight / 2));
        }
        for (DiagramEdge edge : definition.getEdges()) {
            Point fromCenter = positions.get(edge.getFrom());
            Point toCenter = positions.get(edge.getTo());
            svg.append(edge(edgePoint(fromCenter, toCenter, dimensions.get(edge.getFrom())),
                    edgePoint(toCenter, fromCenter, dimensions.get(edge.getTo())), edge.getLabel(), options));
        }
        return svg.append(end()).toString();
    }

    /**
     * 渲染包含字段列表的实体卡片。
     *
     * @param svg SVG 输出缓冲区
     * @param node 实体节点
     * @param x 卡片左上角横坐标
     * @param y 卡片左上角纵坐标
     * @param height 卡片高度
     * @param options SVG 配置
     */
    private void renderEntity(StringBuilder svg, DiagramNode node, int x, int y, int height,
                              SvgDiagramOptions options) {
        svg.append("<rect x=\"").append(x).append("\" y=\"").append(y).append("\" width=\"")
                .append(ENTITY_WIDTH).append("\" height=\"").append(height).append("\" rx=\"4\"/>\n")
                .append("<line x1=\"").append(x).append("\" y1=\"").append(y + ENTITY_HEADER_HEIGHT)
                .append("\" x2=\"").append(x + ENTITY_WIDTH).append("\" y2=\"")
                .append(y + ENTITY_HEADER_HEIGHT).append("\"/>\n")
                .append(text(x + ENTITY_WIDTH / 2, y + 22, node.getLabel(), "middle", 15, "700", options));
        if (node.getFields().isEmpty()) {
            svg.append(text(x + 12, y + ENTITY_HEADER_HEIGHT + 16, "-", "start", 13, "400", options));
            return;
        }
        for (int index = 0; index < node.getFields().size(); index++) {
            svg.append(text(x + 12, y + ENTITY_HEADER_HEIGHT + 16 + index * FIELD_HEIGHT,
                    node.getFields().get(index), "start", 13, "400", options));
        }
    }

    /**
     * 计算矩形实体边框上朝向目标实体的连接锚点。
     *
     * @param center 实体中心坐标
     * @param target 目标实体中心坐标
     * @param halfSize 实体半宽和半高
     * @return 实体边框连接锚点
     */
    private Point edgePoint(Point center, Point target, Point halfSize) {
        double horizontal = target.x - center.x;
        double vertical = target.y - center.y;
        double scale = Math.max(Math.abs(horizontal) / halfSize.x, Math.abs(vertical) / halfSize.y);
        if (scale == 0D) {
            return center;
        }
        return new Point((int) Math.round(center.x + horizontal / scale),
                (int) Math.round(center.y + vertical / scale));
    }
}
