package cn.bugstack.office.docx.model;

/**
 * 题注引用行内节点。
 *
 * <p>该节点用于在正文中引用已经渲染过的图题或表题，例如 {@code 图 1}、{@code 表 2}。</p>
 */
public class CaptionRefInline implements DocxInline {

    /** 被引用题注的类型。 */
    private final CaptionType type;
    /** 被引用题注的唯一标识。 */
    private final String captionId;

    /**
     * 创建题注引用节点。
     *
     * @param type 题注类型
     * @param captionId 题注业务标识
     */
    public CaptionRefInline(CaptionType type, String captionId) {
        this.type = type;
        this.captionId = captionId;
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
     * 获取题注业务标识。
     *
     * @return 题注业务标识
     */
    public String getCaptionId() {
        return captionId;
    }
}
