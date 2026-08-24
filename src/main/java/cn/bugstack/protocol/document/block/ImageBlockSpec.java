package cn.bugstack.protocol.document.block;

/**
 * 图片块。内部调用可使用受信路径，外部协议应使用租户隔离的 {@code assetId}。
 */
public final class ImageBlockSpec extends BlockSpec {

    private String source;
    private String assetId;
    private String alternativeText;
    private Integer width;
    private Integer height;
    private String caption;
    private String captionPosition = "BELOW";

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getAssetId() {
        return assetId;
    }

    public void setAssetId(String assetId) {
        this.assetId = assetId;
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

    public String getCaptionPosition() {
        return captionPosition;
    }

    public void setCaptionPosition(String captionPosition) {
        this.captionPosition = captionPosition;
    }
}
