package cn.bugstack.export.document;

/**
 * 报告中的图片内容。
 */
public class ReportImage implements ReportElement {

    /** 图片来源，可以是文件路径、对象存储地址或 Base64 数据。 */
    private String source;

    /** 无法展示图片时使用的替代文本。 */
    private String alternativeText;

    /** 图片展示宽度，单位由具体渲染器解释。 */
    private Integer width;

    /** 图片展示高度，单位由具体渲染器解释。 */
    private Integer height;

    /** 图片题注。 */
    private ReportCaption caption;

    /**
     * 创建空图片元素，供序列化框架使用。
     */
    public ReportImage() {
    }

    /**
     * 创建图片元素。
     *
     * @param source 图片来源
     */
    public ReportImage(String source) {
        this.source = source;
    }

    /**
     * 获取当前元素的类型。
     *
     * @return 图片元素类型
     */
    @Override
    public ReportElementType getElementType() {
        return ReportElementType.IMAGE;
    }

    /**
     * 获取图片来源。
     *
     * @return 图片来源
     */
    public String getSource() {
        return source;
    }

    /**
     * 设置图片来源。
     *
     * @param source 图片来源
     */
    public void setSource(String source) {
        this.source = source;
    }

    /**
     * 获取替代文本。
     *
     * @return 替代文本
     */
    public String getAlternativeText() {
        return alternativeText;
    }

    /**
     * 设置替代文本。
     *
     * @param alternativeText 替代文本
     */
    public void setAlternativeText(String alternativeText) {
        this.alternativeText = alternativeText;
    }

    /**
     * 获取图片宽度。
     *
     * @return 图片宽度
     */
    public Integer getWidth() {
        return width;
    }

    /**
     * 设置图片宽度。
     *
     * @param width 图片宽度
     */
    public void setWidth(Integer width) {
        this.width = width;
    }

    /**
     * 获取图片高度。
     *
     * @return 图片高度
     */
    public Integer getHeight() {
        return height;
    }

    /**
     * 设置图片高度。
     *
     * @param height 图片高度
     */
    public void setHeight(Integer height) {
        this.height = height;
    }

    /**
     * 获取图片题注。
     *
     * @return 图片题注
     */
    public ReportCaption getCaption() {
        return caption;
    }

    /**
     * 设置图片题注。
     *
     * @param caption 图片题注
     */
    public void setCaption(ReportCaption caption) {
        this.caption = caption;
    }
}
