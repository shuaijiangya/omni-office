package cn.bugstack.protocol.document;

/**
 * DocumentSpec 中与输出格式无关的版式配置。
 */
public final class DocumentLayoutSpec {

    private DocumentStyleProfile styleProfile = DocumentStyleProfile.DEFAULT;
    private boolean headingNumberingEnabled = true;
    private boolean bodyTitleEnabled = true;
    private Integer tableOfContentsDepth;
    private String headerText;
    private String footerText;
    private boolean pageNumberFooterEnabled = true;
    private int bodyPageNumberStart = 1;

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
}
