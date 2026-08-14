package cn.bugstack.office.diagram.render;

import cn.bugstack.office.diagram.model.DiagramDefinition;
import cn.bugstack.office.diagram.model.DiagramEdge;
import cn.bugstack.office.diagram.model.DiagramNode;
import cn.bugstack.office.diagram.model.DiagramNodeType;
import cn.bugstack.office.diagram.model.DiagramType;
import cn.bugstack.office.diagram.model.SvgDiagramOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * 自上而下流程图的 SVG 布局策略。
 */
public final class FlowSvgLayout extends AbstractSvgDiagramLayout {

    /**
     * 获取该布局支持的图类型。
     *
     * @return 流程图类型
     */
    @Override
    public DiagramType supportedType() {
        return DiagramType.FLOW;
    }

    /**
     * 按节点声明顺序生成自上而下流程图。
     *
     * @param definition 流程图定义
     * @return SVG 文本
     */
    @Override
    public String render(DiagramDefinition definition) {
        SvgDiagramOptions options = definition.getOptions();
        int height = Math.max(options.getHeight(), 100 + definition.getNodes().size() * 118);
        int x = options.getWidth() / 2;
        Map<String, Point> positions = new HashMap<>();
        Map<String, DiagramNode> nodes = new HashMap<>();
        Map<String, Integer> nodeIndexes = new HashMap<>();
        StringBuilder svg = new StringBuilder(begin(definition, height));
        for (int index = 0; index < definition.getNodes().size(); index++) {
            DiagramNode node = definition.getNodes().get(index);
            int y = 92 + index * 112;
            renderNode(svg, node, x, y, options);
            positions.put(node.getId(), new Point(x, y));
            nodes.put(node.getId(), node);
            nodeIndexes.put(node.getId(), index);
        }
        for (DiagramEdge edge : definition.getEdges()) {
            svg.append(renderEdge(edge, positions, nodes, nodeIndexes, options));
        }
        return svg.append(end()).toString();
    }

    /**
     * 根据节点类型渲染流程图形状。
     *
     * @param svg SVG 输出缓冲区
     * @param node 流程节点
     * @param x 节点中心横坐标
     * @param y 节点中心纵坐标
     * @param options SVG 配置
     */
    private void renderNode(StringBuilder svg, DiagramNode node, int x, int y, SvgDiagramOptions options) {
        if (node.getType() == DiagramNodeType.START || node.getType() == DiagramNodeType.END) {
            svg.append("<ellipse cx=\"").append(x).append("\" cy=\"").append(y)
                    .append("\" rx=\"78\" ry=\"28\"/>\n");
        } else if (node.getType() == DiagramNodeType.DECISION) {
            svg.append("<polygon points=\"").append(x).append(",").append(y - 40).append(" ")
                    .append(x + 86).append(",").append(y).append(" ")
                    .append(x).append(",").append(y + 40).append(" ")
                    .append(x - 86).append(",").append(y).append("\"/>\n");
        } else {
            svg.append("<rect x=\"").append(x - 118).append("\" y=\"").append(y - 28)
                    .append("\" width=\"236\" height=\"56\" rx=\"6\"/>\n");
        }
        svg.append(text(x, y + 5, node.getLabel(), "middle", 14, "400", options));
    }

    /**
     * 渲染流程节点之间的连接线；跨越中间节点的边使用右侧旁路，避免穿过其他节点。
     *
     * @param edge 流程关系边
     * @param positions 节点中心坐标
     * @param nodes 节点映射
     * @param nodeIndexes 节点声明顺序映射
     * @param options SVG 配置
     * @return SVG 连接线文本
     */
    private String renderEdge(DiagramEdge edge, Map<String, Point> positions, Map<String, DiagramNode> nodes,
                              Map<String, Integer> nodeIndexes, SvgDiagramOptions options) {
        Point fromCenter = positions.get(edge.getFrom());
        Point toCenter = positions.get(edge.getTo());
        Point from = edgePoint(nodes.get(edge.getFrom()), fromCenter, toCenter);
        Point to = edgePoint(nodes.get(edge.getTo()), toCenter, fromCenter);
        if (Math.abs(nodeIndexes.get(edge.getTo()) - nodeIndexes.get(edge.getFrom())) == 1) {
            return edge(from, to, edge.getLabel(), options);
        }
        int laneX = options.getWidth() - PADDING - 24;
        Point routeFrom = edgePoint(nodes.get(edge.getFrom()), fromCenter, new Point(laneX, fromCenter.y));
        Point routeTo = edgePoint(nodes.get(edge.getTo()), toCenter, new Point(laneX, toCenter.y));
        return polyline(new Point[]{routeFrom, new Point(laneX, routeFrom.y), new Point(laneX, routeTo.y), routeTo},
                edge.getLabel(), options);
    }

    /**
     * 计算流程节点边框上朝向目标节点的连接锚点。
     *
     * @param node 当前流程节点
     * @param center 当前节点中心坐标
     * @param target 目标节点中心坐标
     * @return 节点边框连接锚点
     */
    private Point edgePoint(DiagramNode node, Point center, Point target) {
        int halfWidth = node.getType() == DiagramNodeType.DECISION ? 86
                : node.getType() == DiagramNodeType.START || node.getType() == DiagramNodeType.END ? 78 : 118;
        int halfHeight = node.getType() == DiagramNodeType.DECISION ? 40 : 28;
        double horizontal = target.x - center.x;
        double vertical = target.y - center.y;
        double scale = Math.max(Math.abs(horizontal) / halfWidth, Math.abs(vertical) / halfHeight);
        if (scale == 0D) {
            return center;
        }
        return new Point((int) Math.round(center.x + horizontal / scale),
                (int) Math.round(center.y + vertical / scale));
    }
}
