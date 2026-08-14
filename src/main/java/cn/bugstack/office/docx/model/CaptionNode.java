package cn.bugstack.office.docx.model;

/**
 * 题注块级节点。
 *
 * <p>题注作为独立段落块渲染，通常紧跟图片、Visio 预览图或表格。</p>
 */
public class CaptionNode implements DocxBlock {

    /** 题注类型。 */
    private final CaptionType type;
    /** 题注正文。 */
    private final String text;
    /** 可被交叉引用的题注标识。 */
    private final String id;
    /** 题注段落样式名称。 */
    private String styleName = "Caption";

    /**
     * 创建题注节点。
     *
     * @param type 题注类型
     * @param text 题注文本
     */
    public CaptionNode(CaptionType type, String text) {
        this(type, null, text);
    }

    /**
     * 创建带业务标识的题注节点。
     *
     * @param type 题注类型
     * @param id 题注业务标识
     * @param text 题注文本
     */
    public CaptionNode(CaptionType type, String id, String text) {
        this.type = type;
        this.id = id;
        this.text = text;
    }

    /**
     * 获取题注类型。
     *
     * @return 题注类型
     */
    public CaptionType getType() {
        return type;
    }

    /**
     * 获取题注文本。
     *
     * @return 题注文本
     */
    public String getText() {
        return text;
    }

    /**
     * 获取题注业务标识。
     *
     * @return 题注业务标识；未设置时返回 {@code null}
     */
    public String getId() {
        return id;
    }

    /**
     * 获取题注段落样式名称。
     *
     * @return 样式名称
     */
    public String getStyleName() {
        return styleName;
    }

    /**
     * 设置题注段落样式名称。
     *
     * @param styleName 样式名称
     */
    public void setStyleName(String styleName) {
        this.styleName = styleName;
    }
}
