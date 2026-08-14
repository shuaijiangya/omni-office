package cn.bugstack.export.context;

/**
 * 导出前的图片数据。
 */
public class ReportImageData {

    /** 图片来源，可以是文件路径、对象存储地址或 Base64 数据。 */
    private String source;

    /** 图片替代文本。 */
    private String alternativeText;

    /** 图片渲染宽度。 */
    private Integer width;

    /** 图片渲染高度。 */
    private Integer height;

    /** 图片题注正文。 */
    private String caption;

    /** 是否自动编号图片题注。 */
    private boolean captionAutoNumbered = true;

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
     * 获取图片替代文本。
     *
     * @return 图片替代文本
     */
    public String getAlternativeText() {
        return alternativeText;
    }

    /**
     * 设置图片替代文本。
     *
     * @param alternativeText 图片替代文本
     */
    public void setAlternativeText(String alternativeText) {
        this.alternativeText = alternativeText;
    }

    /**
     * 获取图片渲染宽度。
     *
     * @return 图片渲染宽度
     */
    public Integer getWidth() {
        return width;
    }

    /**
     * 设置图片渲染宽度。
     *
     * @param width 图片渲染宽度
     */
    public void setWidth(Integer width) {
        this.width = width;
    }

    /**
     * 获取图片渲染高度。
     *
     * @return 图片渲染高度
     */
    public Integer getHeight() {
        return height;
    }

    /**
     * 设置图片渲染高度。
     *
     * @param height 图片渲染高度
     */
    public void setHeight(Integer height) {
        this.height = height;
    }

    /**
     * 获取图片题注正文。
     *
     * @return 图片题注正文
     */
    public String getCaption() {
        return caption;
    }

    /**
     * 设置图片题注正文。
     *
     * @param caption 图片题注正文
     */
    public void setCaption(String caption) {
        this.caption = caption;
    }

    /**
     * 判断是否自动编号图片题注。
     *
     * @return {@code true} 表示自动编号
     */
    public boolean isCaptionAutoNumbered() {
        return captionAutoNumbered;
    }

    /**
     * 设置是否自动编号图片题注。
     *
     * @param captionAutoNumbered 是否自动编号
     */
    public void setCaptionAutoNumbered(boolean captionAutoNumbered) {
        this.captionAutoNumbered = captionAutoNumbered;
    }
}
