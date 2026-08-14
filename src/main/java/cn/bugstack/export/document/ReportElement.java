package cn.bugstack.export.document;

/**
 * 报告文档的基础元素。
 *
 * <p>实现类用于描述与具体 Word 框架无关的文档内容。</p>
 */
public interface ReportElement {

    /**
     * 获取元素类型。
     *
     * @return 元素类型
     */
    ReportElementType getElementType();
}
