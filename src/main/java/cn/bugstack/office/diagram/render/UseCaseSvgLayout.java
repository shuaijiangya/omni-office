package cn.bugstack.office.diagram.render;

import cn.bugstack.office.diagram.model.DiagramDefinition;
import cn.bugstack.office.diagram.model.DiagramEdge;
import cn.bugstack.office.diagram.model.DiagramNode;
import cn.bugstack.office.diagram.model.DiagramNodeType;
import cn.bugstack.office.diagram.model.DiagramType;
import cn.bugstack.office.diagram.model.SvgDiagramOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用例图的三列 SVG 布局策略。
 */
public final class UseCaseSvgLayout extends AbstractSvgDiagramLayout {

    /**
     * 获取该布局支持的图类型。
     *
     * @return 用例图类型
     */
    @Override
    public DiagramType supportedType() {
        return DiagramType.USE_CASE;
    }

    /**
     * 以参与者和用例分列的方式生成 SVG。
     *
     * @param definition 用例图定义
     * @return SVG 文本
     */
    @Override
    public String render(DiagramDefinition definition) {
        List<DiagramNode> actors = nodesOf(definition, DiagramNodeType.ACTOR);
        List<DiagramNode> useCases = nodesOf(definition, DiagramNodeType.USE_CASE);
        List<DiagramNode> others = otherNodes(definition);
        int rows = Math.max(1, Math.max(actors.size(), Math.max(useCases.size(), others.size())));
        int height = Math.max(definition.getOptions().getHeight(), 100 + rows * 96);
        SvgDiagramOptions options = definition.getOptions();
        Map<String, Point> positions = new HashMap<>();
        StringBuilder svg = new StringBuilder(begin(definition, height));
        renderActors(svg, actors, 120, positions, options);
        renderUseCases(svg, useCases, options.getWidth() / 2, positions, options);
        renderUseCases(svg, others, options.getWidth() - 130, positions, options);
        for (DiagramEdge edge : definition.getEdges()) {
            DiagramNode from = nodeOf(definition, edge.getFrom());
            DiagramNode to = nodeOf(definition, edge.getTo());
            Point fromCenter = positions.get(edge.getFrom());
            Point toCenter = positions.get(edge.getTo());
            svg.append(edge(edgePoint(from, fromCenter, toCenter), edgePoint(to, toCenter, fromCenter),
                    edge.getLabel(), options));
        }
        return svg.append(end()).toString();
    }

    /**
     * 筛选指定类型节点。
     *
     * @param definition 图定义
     * @param type 节点类型
     * @return 筛选后的节点
     */
    private List<DiagramNode> nodesOf(DiagramDefinition definition, DiagramNodeType type) {
        List<DiagramNode> nodes = new ArrayList<>();
        for (DiagramNode node : definition.getNodes()) {
            if (node.getType() == type) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    /**
     * 筛选未按参与者或用例定义的节点。
     *
     * @param definition 图定义
     * @return 其他节点
     */
    private List<DiagramNode> otherNodes(DiagramDefinition definition) {
        List<DiagramNode> nodes = new ArrayList<>();
        for (DiagramNode node : definition.getNodes()) {
            if (node.getType() != DiagramNodeType.ACTOR && node.getType() != DiagramNodeType.USE_CASE) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    /**
     * 根据节点标识获取图节点。
     *
     * @param definition 图定义
     * @param nodeId 节点标识
     * @return 匹配的图节点
     */
    private DiagramNode nodeOf(DiagramDefinition definition, String nodeId) {
        for (DiagramNode node : definition.getNodes()) {
            if (node.getId().equals(nodeId)) {
                return node;
            }
        }
        throw new IllegalStateException("diagram node does not exist: " + nodeId);
    }

    /**
     * 计算从节点边框出发并朝向目标节点的连接锚点。
     *
     * @param node 当前节点
     * @param center 当前节点中心坐标
     * @param target 目标节点中心坐标
     * @return 节点边框上的连接锚点
     */
    private Point edgePoint(DiagramNode node, Point center, Point target) {
        int radiusX = node.getType() == DiagramNodeType.ACTOR ? 18 : 118;
        int radiusY = node.getType() == DiagramNodeType.ACTOR ? 44 : 34;
        double horizontal = target.x - center.x;
        double vertical = target.y - center.y;
        double denominator = Math.sqrt(horizontal * horizontal / (radiusX * radiusX)
                + vertical * vertical / (radiusY * radiusY));
        if (denominator == 0D) {
            return center;
        }
        return new Point((int) Math.round(center.x + horizontal / denominator),
                (int) Math.round(center.y + vertical / denominator));
    }

    /**
     * 渲染参与者形状。
     *
     * @param svg SVG 输出缓冲区
     * @param actors 参与者列表
     * @param x 横坐标
     * @param positions 节点坐标映射
     * @param options SVG 配置
     */
    private void renderActors(StringBuilder svg, List<DiagramNode> actors, int x, Map<String, Point> positions,
                              SvgDiagramOptions options) {
        for (int index = 0; index < actors.size(); index++) {
            int y = 105 + index * 96;
            DiagramNode actor = actors.get(index);
            svg.append("<circle cx=\"").append(x).append("\" cy=\"").append(y - 24)
                    .append("\" r=\"12\"/>\n<line x1=\"").append(x).append("\" y1=\"").append(y - 12)
                    .append("\" x2=\"").append(x).append("\" y2=\"").append(y + 24).append("\"/>\n")
                    .append("<line x1=\"").append(x - 18).append("\" y1=\"").append(y)
                    .append("\" x2=\"").append(x + 18).append("\" y2=\"").append(y).append("\"/>\n")
                    .append("<line x1=\"").append(x).append("\" y1=\"").append(y + 24)
                    .append("\" x2=\"").append(x - 16).append("\" y2=\"").append(y + 44).append("\"/>\n")
                    .append("<line x1=\"").append(x).append("\" y1=\"").append(y + 24)
                    .append("\" x2=\"").append(x + 16).append("\" y2=\"").append(y + 44).append("\"/>\n")
                    .append(text(x, y + 66, actor.getLabel(), "middle", 14, "400", options));
            positions.put(actor.getId(), new Point(x, y + 8));
        }
    }

    /**
     * 渲染椭圆用例形状。
     *
     * @param svg SVG 输出缓冲区
     * @param nodes 用例节点列表
     * @param x 横坐标
     * @param positions 节点坐标映射
     * @param options SVG 配置
     */
    private void renderUseCases(StringBuilder svg, List<DiagramNode> nodes, int x, Map<String, Point> positions,
                                SvgDiagramOptions options) {
        for (int index = 0; index < nodes.size(); index++) {
            int y = 105 + index * 96;
            DiagramNode node = nodes.get(index);
            svg.append("<ellipse cx=\"").append(x).append("\" cy=\"").append(y)
                    .append("\" rx=\"118\" ry=\"34\"/>\n")
                    .append(text(x, y + 5, node.getLabel(), "middle", 14, "400", options));
            positions.put(node.getId(), new Point(x, y));
        }
    }
}
