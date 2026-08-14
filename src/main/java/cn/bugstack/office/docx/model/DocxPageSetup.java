package cn.bugstack.office.docx.model;

/**
 * 文档页面设置。
 */
public class DocxPageSetup {

    /** 纸张规格，默认 A4。 */
    private DocxPaperSize paperSize = DocxPaperSize.A4;
    /** 页面方向，默认纵向。 */
    private DocxPageOrientation orientation = DocxPageOrientation.PORTRAIT;
    /** 上边距，单位为磅。 */
    private double topMarginPoints = 72.0;
    /** 右边距，单位为磅。 */
    private double rightMarginPoints = 72.0;
    /** 下边距，单位为磅。 */
    private double bottomMarginPoints = 72.0;
    /** 左边距，单位为磅。 */
    private double leftMarginPoints = 72.0;

    /**
     * 创建默认页面设置。
     */
    public DocxPageSetup() {
    }

    /**
     * 获取纸张大小。
     *
     * @return 纸张大小
     */
    public DocxPaperSize getPaperSize() {
        return paperSize;
    }

    /**
     * 设置纸张大小。
     *
     * @param paperSize 纸张大小
     */
    public void setPaperSize(DocxPaperSize paperSize) {
        this.paperSize = paperSize;
    }

    /**
     * 获取页面方向。
     *
     * @return 页面方向
     */
    public DocxPageOrientation getOrientation() {
        return orientation;
    }

    /**
     * 设置页面方向。
     *
     * @param orientation 页面方向
     */
    public void setOrientation(DocxPageOrientation orientation) {
        this.orientation = orientation;
    }

    /**
     * 获取上边距。
     *
     * @return 上边距，单位为 point
     */
    public double getTopMarginPoints() {
        return topMarginPoints;
    }

    /**
     * 获取右边距。
     *
     * @return 右边距，单位为 point
     */
    public double getRightMarginPoints() {
        return rightMarginPoints;
    }

    /**
     * 获取下边距。
     *
     * @return 下边距，单位为 point
     */
    public double getBottomMarginPoints() {
        return bottomMarginPoints;
    }

    /**
     * 获取左边距。
     *
     * @return 左边距，单位为 point
     */
    public double getLeftMarginPoints() {
        return leftMarginPoints;
    }

    /**
     * 设置页边距。
     *
     * @param topMarginPoints 上边距，单位为 point
     * @param rightMarginPoints 右边距，单位为 point
     * @param bottomMarginPoints 下边距，单位为 point
     * @param leftMarginPoints 左边距，单位为 point
     */
    public void setMargins(double topMarginPoints, double rightMarginPoints,
                           double bottomMarginPoints, double leftMarginPoints) {
        this.topMarginPoints = topMarginPoints;
        this.rightMarginPoints = rightMarginPoints;
        this.bottomMarginPoints = bottomMarginPoints;
        this.leftMarginPoints = leftMarginPoints;
    }
}
