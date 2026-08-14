package cn.bugstack.export.context;

/**
 * 导出前的段落数据。
 */
public class ReportParagraphData {

    /** 段落正文。 */
    private String text;

    /** 目标段落样式名称。 */
    private String styleName;

    /**
     * 创建空段落数据，供序列化框架使用。
     */
    public ReportParagraphData() {
    }

    /**
     * 创建段落数据。
     *
     * @param text 段落正文
     */
    public ReportParagraphData(String text) {
        this.text = text;
    }

    /**
     * 获取段落正文。
     *
     * @return 段落正文
     */
    public String getText() {
        return text;
    }

    /**
     * 设置段落正文。
     *
     * @param text 段落正文
     */
    public void setText(String text) {
        this.text = text;
    }

    /**
     * 获取目标段落样式名称。
     *
     * @return 目标段落样式名称
     */
    public String getStyleName() {
        return styleName;
    }

    /**
     * 设置目标段落样式名称。
     *
     * @param styleName 目标段落样式名称
     */
    public void setStyleName(String styleName) {
        this.styleName = styleName;
    }
}
