package cn.bugstack.protocol.document.block;

/**
 * 图片块。当前使用受信任应用解析后的图片来源；通用图片 artifactId 将在后续工件能力中扩展。
 */
public final class ImageBlockSpec extends BlockSpec {

    private String source;
    private String alternativeText;
    private Integer width;
    private Integer height;
    private String caption;

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getAlternativeText() {
        return alternativeText;
    }

    public void setAlternativeText(String alternativeText) {
        this.alternativeText = alternativeText;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }
}
