package cn.bugstack.office.diagram.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 与具体绘图工具无关的设计图语义定义。
 */
public final class DiagramDefinition {

    /** 图类型。 */
    private final DiagramType type;
    /** 图标题。 */
    private final String title;
    /** 图节点列表。 */
    private final List<DiagramNode> nodes;
    /** 图关系列表。 */
    private final List<DiagramEdge> edges;
    /** SVG 显示配置。 */
    private final SvgDiagramOptions options;

    /**
     * 使用构建器创建图定义。
     *
     * @param builder 已配置构建器
     */
    private DiagramDefinition(Builder builder) {
        this.type = builder.type;
        this.title = builder.title;
        this.nodes = Collections.unmodifiableList(new ArrayList<>(builder.nodes.values()));
        this.edges = Collections.unmodifiableList(new ArrayList<>(builder.edges));
        this.options = builder.options;
    }

    /**
     * 创建指定类型和标题的图定义构建器。
     *
     * @param type 图类型
     * @param title 图标题
     * @return 图定义构建器
     */
    public static Builder builder(DiagramType type, String title) {
        return new Builder(type, title);
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
     * 获取图标题。
     *
     * @return 图标题
     */
    public String getTitle() {
        return title;
    }

    /**
     * 获取图节点列表。
     *
     * @return 不可修改的节点列表
     */
    public List<DiagramNode> getNodes() {
        return nodes;
    }

    /**
     * 获取图关系列表。
     *
     * @return 不可修改的关系列表
     */
    public List<DiagramEdge> getEdges() {
        return edges;
    }

    /**
     * 获取 SVG 显示配置。
     *
     * @return SVG 显示配置
     */
    public SvgDiagramOptions getOptions() {
        return options;
    }

    /**
     * 设计图语义构建器。
     */
    public static final class Builder {

        /** 图类型。 */
        private final DiagramType type;
        /** 图标题。 */
        private final String title;
        /** 按插入顺序保存的图节点。 */
        private final Map<String, DiagramNode> nodes = new LinkedHashMap<>();
        /** 图关系列表。 */
        private final List<DiagramEdge> edges = new ArrayList<>();
        /** SVG 显示配置。 */
        private SvgDiagramOptions options = new SvgDiagramOptions();

        /**
         * 创建图语义构建器。
         *
         * @param type 图类型
         * @param title 图标题
         */
        private Builder(DiagramType type, String title) {
            if (type == null) {
                throw new IllegalArgumentException("diagram type must not be null");
            }
            if (title == null || title.trim().isEmpty()) {
                throw new IllegalArgumentException("diagram title must not be blank");
            }
            this.type = type;
            this.title = title.trim();
        }

        /**
         * 追加图节点。
         *
         * @param node 图节点
         * @return 当前构建器
         */
        public Builder node(DiagramNode node) {
            if (node == null) {
                throw new IllegalArgumentException("diagram node must not be null");
            }
            if (nodes.putIfAbsent(node.getId(), node) != null) {
                throw new IllegalArgumentException("duplicate diagram node id: " + node.getId());
            }
            return this;
        }

        /**
         * 追加图关系。
         *
         * @param edge 图关系
         * @return 当前构建器
         */
        public Builder edge(DiagramEdge edge) {
            if (edge == null) {
                throw new IllegalArgumentException("diagram edge must not be null");
            }
            edges.add(edge);
            return this;
        }

        /**
         * 设置 SVG 显示配置。
         *
         * @param options SVG 显示配置
         * @return 当前构建器
         */
        public Builder options(SvgDiagramOptions options) {
            if (options == null) {
                throw new IllegalArgumentException("svg diagram options must not be null");
            }
            this.options = options;
            return this;
        }

        /**
         * 校验关系引用并创建不可变图定义。
         *
         * @return 图定义
         */
        public DiagramDefinition build() {
            if (nodes.isEmpty()) {
                throw new IllegalStateException("diagram must contain at least one node");
            }
            for (DiagramEdge edge : edges) {
                if (!nodes.containsKey(edge.getFrom()) || !nodes.containsKey(edge.getTo())) {
                    throw new IllegalStateException("diagram edge references an unknown node: "
                            + edge.getFrom() + " -> " + edge.getTo());
                }
            }
            return new DiagramDefinition(this);
        }
    }
}
