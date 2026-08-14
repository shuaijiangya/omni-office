package cn.bugstack.office.diagram.model;

/**
 * 图中两个节点之间的有向关系。
 */
public final class DiagramEdge {

    /** 起始节点标识。 */
    private final String from;
    /** 目标节点标识。 */
    private final String to;
    /** 关系显示文本。 */
    private final String label;

    /**
     * 创建无标签关系。
     *
     * @param from 起始节点标识
     * @param to 目标节点标识
     */
    public DiagramEdge(String from, String to) {
        this(from, to, null);
    }

    /**
     * 创建带标签关系。
     *
     * @param from 起始节点标识
     * @param to 目标节点标识
     * @param label 关系显示文本
     */
    public DiagramEdge(String from, String to, String label) {
        this.from = requiredText(from, "diagram edge from");
        this.to = requiredText(to, "diagram edge to");
        this.label = label == null || label.trim().isEmpty() ? null : label.trim();
    }

    /**
     * 获取起始节点标识。
     *
     * @return 起始节点标识
     */
    public String getFrom() {
        return from;
    }

    /**
     * 获取目标节点标识。
     *
     * @return 目标节点标识
     */
    public String getTo() {
        return to;
    }

    /**
     * 获取关系显示文本。
     *
     * @return 关系文本；未设置时返回 {@code null}
     */
    public String getLabel() {
        return label;
    }

    /**
     * 校验并规范化必填文本。
     *
     * @param value 原始文本
     * @param name 参数名称
     * @return 规范化后的文本
     */
    private static String requiredText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
