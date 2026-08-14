package cn.bugstack.office.docx.model;

/**
 * 图片行内节点。
 *
 * <p>图片在 Word/docx 中通常作为段落内的 drawing，因此该节点实现
 * {@link DocxInline}，不作为 Section 的直接 child。</p>
 */
public class ImageInline implements DocxInline {

    /** 图片数据源。 */
    private final String source;
    /** 图片目标宽度，单位为磅；为空时使用原始比例。 */
    private final Double widthPoints;
    /** 图片目标高度，单位为磅；为空时使用原始比例。 */
    private final Double heightPoints;

    /**
     * 创建图片行内节点。
     *
     * @param source 图片路径或 Aspose 可识别的图片来源
     */
    public ImageInline(String source) {
        this(source, null, null);
    }

    /**
     * 创建带尺寸的图片行内节点。
     *
     * @param source 图片路径或 Aspose 可识别的图片来源
     * @param widthPoints 图片宽度，单位为 point
     * @param heightPoints 图片高度，单位为 point
     */
    public ImageInline(String source, Double widthPoints, Double heightPoints) {
        this.source = source;
        this.widthPoints = widthPoints;
        this.heightPoints = heightPoints;
    }

    /**
     * 获取图片来源。
     *
     * @return 图片路径或来源
     */
    public String getSource() {
        return source;
    }

    /**
     * 获取图片宽度。
     *
     * @return 图片宽度，单位为 point；未设置时返回 {@code null}
     */
    public Double getWidthPoints() {
        return widthPoints;
    }

    /**
     * 获取图片高度。
     *
     * @return 图片高度，单位为 point；未设置时返回 {@code null}
     */
    public Double getHeightPoints() {
        return heightPoints;
    }
}
