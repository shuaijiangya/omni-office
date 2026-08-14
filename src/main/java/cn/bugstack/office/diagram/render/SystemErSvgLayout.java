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
 * Chen 表示法系统 E-R 图的 SVG 布局策略。
 *
 * <p>使用矩形表示实体、菱形表示关系、椭圆表示属性。该策略服务于系统分析阶段的概念
 * 数据建模，与 {@link ErSvgLayout} 的数据库表结构表达相互独立。</p>
 *
 * @author luojiang
 */
public final class SystemErSvgLayout extends AbstractSvgDiagramLayout {

    /** 实体矩形宽度。 */
    private static final int ENTITY_WIDTH = 112;
    /** 实体矩形高度。 */
    private static final int ENTITY_HEIGHT = 48;
    /** 关系菱形半宽。 */
    private static final int RELATIONSHIP_HALF_WIDTH = 42;
    /** 关系菱形半高。 */
    private static final int RELATIONSHIP_HALF_HEIGHT = 28;
    /** 属性椭圆横向半径。 */
    private static final int ATTRIBUTE_RADIUS_X = 44;
    /** 属性椭圆纵向半径。 */
    private static final int ATTRIBUTE_RADIUS_Y = 21;

    /**
     * 获取该布局支持的图类型。
     *
     * @return 系统 E-R 图类型
     */
    @Override
    public DiagramType supportedType() {
        return DiagramType.SYSTEM_ER;
    }

    /**
     * 生成系统 E-R 图 SVG。
     *
     * @param definition 系统 E-R 图定义
     * @return SVG 文本
     */
    @Override
    public String render(DiagramDefinition definition) {
        SvgDiagramOptions options = definition.getOptions();
        int height = Math.max(options.getHeight(), 680);
        Map<String, DiagramNode> nodes = nodeIndex(definition);
        Map<String, Point> positions = positions(definition, options.getWidth(), height, nodes);
        StringBuilder svg = new StringBuilder(begin(definition, height));
        for (DiagramEdge edge : definition.getEdges()) {
            Point from = positions.get(edge.getFrom());
            Point to = positions.get(edge.getTo());
            svg.append(association(from, to, edge.getLabel(), options));
        }
        for (DiagramNode node : definition.getNodes()) {
            renderNode(svg, node, positions.get(node.getId()), options);
        }
        return svg.append(end()).toString();
    }

    /**
     * 计算各系统 E-R 图元的中心坐标。
     *
     * @param definition 图定义
     * @param width 画布宽度
     * @param height 画布高度
     * @param nodes 节点索引
     * @return 节点标识到中心坐标的映射
     */
    private Map<String, Point> positions(DiagramDefinition definition, int width, int height,
                                         Map<String, DiagramNode> nodes) {
        Map<String, Point> positions = new HashMap<>();
        List<DiagramNode> entities = nodesOf(definition, DiagramNodeType.ENTITY);
        int[][] offsets = {{0, 0}, {270, -156}, {270, 156}, {-270, -156}, {-270, 156}};
        Point center = new Point(width / 2, height / 2);
        for (int index = 0; index < entities.size(); index++) {
            int[] offset = offsets[Math.min(index, offsets.length - 1)];
            positions.put(entities.get(index).getId(), new Point(center.x + offset[0], center.y + offset[1]));
        }
        for (DiagramNode relation : nodesOf(definition, DiagramNodeType.RELATIONSHIP)) {
            List<Point> related = relatedEntityPositions(relation.getId(), definition, nodes, positions);
            positions.put(relation.getId(), related.size() >= 2
                    ? new Point((related.get(0).x + related.get(1).x) / 2, (related.get(0).y + related.get(1).y) / 2)
                    : new Point(center.x + 132, center.y - 76));
        }
        Map<String, Integer> indexes = new HashMap<>();
        for (DiagramNode attribute : nodesOf(definition, DiagramNodeType.ATTRIBUTE)) {
            String ownerId = relatedEntityId(attribute.getId(), definition, nodes);
            Point owner = positions.get(ownerId);
            int index = indexes.getOrDefault(ownerId, 0);
            indexes.put(ownerId, index + 1);
            double angle = Math.toRadians(new int[] {-90, 180, 135, 90, -135, 0}[index % 6]);
            positions.put(attribute.getId(), new Point(owner.x + (int) Math.round(Math.cos(angle) * 138D),
                    owner.y + (int) Math.round(Math.sin(angle) * 100D)));
        }
        return positions;
    }

    /**
     * 渲染一个系统 E-R 节点。
     *
     * @param svg SVG 输出缓冲区
     * @param node 节点定义
     * @param position 节点中心坐标
     * @param options SVG 配置
     */
    private void renderNode(StringBuilder svg, DiagramNode node, Point position, SvgDiagramOptions options) {
        if (node.getType() == DiagramNodeType.RELATIONSHIP) {
            svg.append("<polygon points=\"").append(position.x).append(',').append(position.y - RELATIONSHIP_HALF_HEIGHT)
                    .append(' ').append(position.x + RELATIONSHIP_HALF_WIDTH).append(',').append(position.y)
                    .append(' ').append(position.x).append(',').append(position.y + RELATIONSHIP_HALF_HEIGHT)
                    .append(' ').append(position.x - RELATIONSHIP_HALF_WIDTH).append(',').append(position.y)
                    .append("\"/>\n");
        } else if (node.getType() == DiagramNodeType.ATTRIBUTE) {
            svg.append("<ellipse cx=\"").append(position.x).append("\" cy=\"").append(position.y)
                    .append("\" rx=\"").append(ATTRIBUTE_RADIUS_X).append("\" ry=\"")
                    .append(ATTRIBUTE_RADIUS_Y).append("\"/>\n");
        } else {
            svg.append("<rect x=\"").append(position.x - ENTITY_WIDTH / 2).append("\" y=\"")
                    .append(position.y - ENTITY_HEIGHT / 2).append("\" width=\"").append(ENTITY_WIDTH)
                    .append("\" height=\"").append(ENTITY_HEIGHT).append("\"/>\n");
        }
        svg.append(text(position.x, position.y + 5, node.getLabel(), "middle", 13, "400", options));
    }

    /**
     * 生成无箭头系统 E-R 关联线及可选基数。
     *
     * @param from 起点坐标
     * @param to 终点坐标
     * @param label 基数文本
     * @param options SVG 配置
     * @return SVG 关联线文本
     */
    private String association(Point from, Point to, String label, SvgDiagramOptions options) {
        StringBuilder svg = new StringBuilder("<line x1=\"").append(from.x).append("\" y1=\"").append(from.y)
                .append("\" x2=\"").append(to.x).append("\" y2=\"").append(to.y).append("\" fill=\"none\"/>\n");
        if (label != null) {
            svg.append(text(from.x + (to.x - from.x) / 4, from.y + (to.y - from.y) / 4 - 6, label,
                    "middle", 12, "400", options));
        }
        return svg.toString();
    }

    /**
     * 获取与指定关系节点直接相连的实体位置。
     *
     * @param relationshipId 关系节点标识
     * @param definition 图定义
     * @param nodes 节点索引
     * @param positions 已计算的位置
     * @return 关联实体位置列表
     */
    private List<Point> relatedEntityPositions(String relationshipId, DiagramDefinition definition,
                                               Map<String, DiagramNode> nodes, Map<String, Point> positions) {
        List<Point> related = new ArrayList<>();
        for (DiagramEdge edge : definition.getEdges()) {
            String otherId = relationshipId.equals(edge.getFrom()) ? edge.getTo()
                    : relationshipId.equals(edge.getTo()) ? edge.getFrom() : null;
            if (otherId != null && nodes.get(otherId).getType() == DiagramNodeType.ENTITY) {
                related.add(positions.get(otherId));
            }
        }
        return related;
    }

    /**
     * 获取属性归属的实体标识。
     *
     * @param attributeId 属性节点标识
     * @param definition 图定义
     * @param nodes 节点索引
     * @return 所属实体标识
     */
    private String relatedEntityId(String attributeId, DiagramDefinition definition, Map<String, DiagramNode> nodes) {
        for (DiagramEdge edge : definition.getEdges()) {
            String otherId = attributeId.equals(edge.getFrom()) ? edge.getTo()
                    : attributeId.equals(edge.getTo()) ? edge.getFrom() : null;
            if (otherId != null && nodes.get(otherId).getType() == DiagramNodeType.ENTITY) {
                return otherId;
            }
        }
        throw new IllegalStateException("system ER attribute must connect to an entity: " + attributeId);
    }

    /**
     * 获取指定类型节点列表。
     *
     * @param definition 图定义
     * @param type 节点类型
     * @return 匹配节点列表
     */
    private List<DiagramNode> nodesOf(DiagramDefinition definition, DiagramNodeType type) {
        List<DiagramNode> result = new ArrayList<>();
        for (DiagramNode node : definition.getNodes()) {
            if (node.getType() == type) {
                result.add(node);
            }
        }
        return result;
    }

    /**
     * 创建节点索引。
     *
     * @param definition 图定义
     * @return 节点标识到定义的映射
     */
    private Map<String, DiagramNode> nodeIndex(DiagramDefinition definition) {
        Map<String, DiagramNode> index = new HashMap<>();
        for (DiagramNode node : definition.getNodes()) {
            index.put(node.getId(), node);
        }
        return index;
    }
}
