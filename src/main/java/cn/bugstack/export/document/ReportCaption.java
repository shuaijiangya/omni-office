package cn.bugstack.export.document;

/**
 * 表格或图片的题注。
 */
public class ReportCaption {

    /** 题注目标类型。 */
    private CaptionTargetType targetType;

    /** 题注正文。 */
    private String text;

    /** 是否在渲染时自动生成编号。 */
    private boolean autoNumbered = true;

    /**
     * 创建空题注，供序列化框架使用。
     */
    public ReportCaption() {
    }

    /**
     * 创建题注。
     *
     * @param targetType 题注目标类型
     * @param text 题注正文
     */
    public ReportCaption(CaptionTargetType targetType, String text) {
        this.targetType = targetType;
        this.text = text;
    }

    /**
     * 获取题注目标类型。
     *
     * @return 题注目标类型
     */
    public CaptionTargetType getTargetType() {
        return targetType;
    }

    /**
     * 设置题注目标类型。
     *
     * @param targetType 题注目标类型
     */
    public void setTargetType(CaptionTargetType targetType) {
        this.targetType = targetType;
    }

    /**
     * 获取题注正文。
     *
     * @return 题注正文
     */
    public String getText() {
        return text;
    }

    /**
     * 设置题注正文。
     *
     * @param text 题注正文
     */
    public void setText(String text) {
        this.text = text;
    }

    /**
     * 判断是否自动编号。
     *
     * @return {@code true} 表示自动编号
     */
    public boolean isAutoNumbered() {
        return autoNumbered;
    }

    /**
     * 设置是否自动编号。
     *
     * @param autoNumbered 是否自动编号
     */
    public void setAutoNumbered(boolean autoNumbered) {
        this.autoNumbered = autoNumbered;
    }
}
