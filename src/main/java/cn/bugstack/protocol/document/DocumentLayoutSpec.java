package cn.bugstack.protocol.document;

/**
 * DocumentSpec 中与输出格式无关的版式配置。
 */
public final class DocumentLayoutSpec {

    private DocumentStyleProfile styleProfile = DocumentStyleProfile.DEFAULT;
    private boolean headingNumberingEnabled = true;
    private boolean bodyTitleEnabled;
    private Integer tableOfContentsDepth;
    private String headerText;
    private String footerText;
    private boolean pageNumberFooterEnabled = true;
    private int bodyPageNumberStart = 1;
    private String paperSize = "A4";
    private String orientation = "PORTRAIT";
    private double topMarginPoints = 72D;
    private double rightMarginPoints = 72D;
    private double bottomMarginPoints = 72D;
    private double leftMarginPoints = 72D;

    public DocumentStyleProfile getStyleProfile() {
        return styleProfile;
    }

    public void setStyleProfile(DocumentStyleProfile styleProfile) {
        this.styleProfile = styleProfile;
    }

    public boolean isHeadingNumberingEnabled() {
        return headingNumberingEnabled;
    }

    public void setHeadingNumberingEnabled(boolean headingNumberingEnabled) {
        this.headingNumberingEnabled = headingNumberingEnabled;
    }

    public boolean isBodyTitleEnabled() {
        return bodyTitleEnabled;
    }

    /**
     * 设置无目录文档是否在正文前重复输出报告标题。
     *
     * <p>配置目录层级后该选项不生效，目录后直接衔接调用方定义的模块正文。</p>
     *
     * @param bodyTitleEnabled 是否输出正文标题
     */
    public void setBodyTitleEnabled(boolean bodyTitleEnabled) {
        this.bodyTitleEnabled = bodyTitleEnabled;
    }

    public Integer getTableOfContentsDepth() {
        return tableOfContentsDepth;
    }

    public void setTableOfContentsDepth(Integer tableOfContentsDepth) {
        this.tableOfContentsDepth = tableOfContentsDepth;
    }

    public String getHeaderText() {
        return headerText;
    }

    public void setHeaderText(String headerText) {
        this.headerText = headerText;
    }

    public String getFooterText() {
        return footerText;
    }

    public void setFooterText(String footerText) {
        this.footerText = footerText;
    }

    public boolean isPageNumberFooterEnabled() {
        return pageNumberFooterEnabled;
    }

    public void setPageNumberFooterEnabled(boolean pageNumberFooterEnabled) {
        this.pageNumberFooterEnabled = pageNumberFooterEnabled;
    }

    public int getBodyPageNumberStart() {
        return bodyPageNumberStart;
    }

    public void setBodyPageNumberStart(int bodyPageNumberStart) {
        this.bodyPageNumberStart = bodyPageNumberStart;
    }

    public String getPaperSize() { return paperSize; }
    public void setPaperSize(String paperSize) { this.paperSize = paperSize; }
    public String getOrientation() { return orientation; }
    public void setOrientation(String orientation) { this.orientation = orientation; }
    public double getTopMarginPoints() { return topMarginPoints; }
    public void setTopMarginPoints(double value) { this.topMarginPoints = value; }
    public double getRightMarginPoints() { return rightMarginPoints; }
    public void setRightMarginPoints(double value) { this.rightMarginPoints = value; }
    public double getBottomMarginPoints() { return bottomMarginPoints; }
    public void setBottomMarginPoints(double value) { this.bottomMarginPoints = value; }
    public double getLeftMarginPoints() { return leftMarginPoints; }
    public void setLeftMarginPoints(double value) { this.leftMarginPoints = value; }
}
