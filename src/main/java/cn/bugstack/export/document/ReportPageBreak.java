package cn.bugstack.export.document;

/**
 * 报告中的显式分页符。
 */
public final class ReportPageBreak implements ReportElement {

    /**
     * 获取当前元素的语义类型。
     *
     * @return 分页符类型
     */
    @Override
    public ReportElementType getElementType() {
        return ReportElementType.PAGE_BREAK;
    }
}
