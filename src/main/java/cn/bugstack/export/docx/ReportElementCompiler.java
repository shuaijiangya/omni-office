package cn.bugstack.export.docx;

import cn.bugstack.export.document.ReportElement;

/**
 * 自定义报告语义块到 docx 的编译扩展点。
 *
 * @param <E> 支持的报告元素类型
 */
public interface ReportElementCompiler<E extends ReportElement> {

    /**
     * 获取支持的元素类型。
     *
     * @return 元素类型
     */
    Class<E> supportedType();

    /**
     * 将元素写入当前 docx 章节。
     *
     * @param element 语义元素
     * @param context 编译上下文
     */
    void compile(E element, DocxReportCompileContext context);
}
