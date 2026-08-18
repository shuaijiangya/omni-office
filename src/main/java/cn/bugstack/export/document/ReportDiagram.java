package cn.bugstack.export.document;

/** 已解析、可供目标格式编译器写入的图形语义元素。 */
public final class ReportDiagram implements ReportElement {

    public static final double DEFAULT_MAX_WIDTH_POINTS = 420D;
    public static final double DEFAULT_MAX_HEIGHT_POINTS = 260D;

    private String vsdxSource;
    private String previewSource;
    private ReportDiagramEmbedMode embedMode = ReportDiagramEmbedMode.EDITABLE_VISIO;
    private double maxWidthPoints = DEFAULT_MAX_WIDTH_POINTS;
    private double maxHeightPoints = DEFAULT_MAX_HEIGHT_POINTS;
    private ReportCaption caption;

    @Override
    public ReportElementType getElementType() {
        return ReportElementType.DIAGRAM;
    }

    public String getVsdxSource() {
        return vsdxSource;
    }

    public void setVsdxSource(String vsdxSource) {
        this.vsdxSource = vsdxSource;
    }

    public String getPreviewSource() {
        return previewSource;
    }

    public void setPreviewSource(String previewSource) {
        this.previewSource = previewSource;
    }

    public ReportDiagramEmbedMode getEmbedMode() {
        return embedMode;
    }

    public void setEmbedMode(ReportDiagramEmbedMode embedMode) {
        this.embedMode = embedMode;
    }

    public double getMaxWidthPoints() {
        return maxWidthPoints;
    }

    public void setMaxWidthPoints(double maxWidthPoints) {
        this.maxWidthPoints = maxWidthPoints;
    }

    public double getMaxHeightPoints() {
        return maxHeightPoints;
    }

    public void setMaxHeightPoints(double maxHeightPoints) {
        this.maxHeightPoints = maxHeightPoints;
    }

    public ReportCaption getCaption() {
        return caption;
    }

    public void setCaption(ReportCaption caption) {
        this.caption = caption;
    }
}
