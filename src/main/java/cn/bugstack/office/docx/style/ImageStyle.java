package cn.bugstack.office.docx.style;

/**
 * 图片样式定义。
 */
public class ImageStyle {

    /** 图片样式名称。 */
    private final String name;
    /** 默认图片宽度，单位为磅。 */
    private Double widthPoints;
    /** 默认图片高度，单位为磅。 */
    private Double heightPoints;

    /**
     * 创建图片样式。
     *
     * @param name 样式名称
     */
    public ImageStyle(String name) {
        this.name = name;
    }

    /**
     * 创建当前图片样式的副本。
     *
     * @return 样式副本
     */
    public ImageStyle copy() {
        ImageStyle copy = new ImageStyle(name);
        copy.widthPoints = widthPoints;
        copy.heightPoints = heightPoints;
        return copy;
    }

    /**
     * 获取样式名称。
     *
     * @return 样式名称
     */
    public String getName() {
        return name;
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
     * 设置图片宽度。
     *
     * @param widthPoints 图片宽度，单位为 point
     */
    public void setWidthPoints(Double widthPoints) {
        this.widthPoints = widthPoints;
    }

    /**
     * 获取图片高度。
     *
     * @return 图片高度，单位为 point；未设置时返回 {@code null}
     */
    public Double getHeightPoints() {
        return heightPoints;
    }

    /**
     * 设置图片高度。
     *
     * @param heightPoints 图片高度，单位为 point
     */
    public void setHeightPoints(Double heightPoints) {
        this.heightPoints = heightPoints;
    }
}
