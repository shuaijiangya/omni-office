package cn.bugstack.office.docx.builder;

import cn.bugstack.office.docx.model.DocxPageOrientation;
import cn.bugstack.office.docx.model.DocxPageSetup;
import cn.bugstack.office.docx.model.DocxPaperSize;

/**
 * 页面设置 Builder。
 */
public class PageSetupBuilder {

    /** 正在配置的页面设置。 */
    private final DocxPageSetup pageSetup;

    /**
     * 创建页面设置 Builder。
     *
     * @param pageSetup 页面设置模型
     */
    public PageSetupBuilder(DocxPageSetup pageSetup) {
        this.pageSetup = pageSetup;
    }

    /**
     * 设置纸张大小。
     *
     * @param paperSize 纸张大小
     * @return 当前 Builder
     */
    public PageSetupBuilder paper(DocxPaperSize paperSize) {
        pageSetup.setPaperSize(paperSize);
        return this;
    }

    /**
     * 设置为横向页面。
     *
     * @return 当前 Builder
     */
    public PageSetupBuilder landscape() {
        pageSetup.setOrientation(DocxPageOrientation.LANDSCAPE);
        return this;
    }

    /**
     * 设置为纵向页面。
     *
     * @return 当前 Builder
     */
    public PageSetupBuilder portrait() {
        pageSetup.setOrientation(DocxPageOrientation.PORTRAIT);
        return this;
    }

    /**
     * 设置页边距。
     *
     * @param top 上边距，单位为 point
     * @param right 右边距，单位为 point
     * @param bottom 下边距，单位为 point
     * @param left 左边距，单位为 point
     * @return 当前 Builder
     */
    public PageSetupBuilder margins(double top, double right, double bottom, double left) {
        pageSetup.setMargins(top, right, bottom, left);
        return this;
    }
}
