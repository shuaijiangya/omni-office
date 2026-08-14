package cn.bugstack.export.definition;

import cn.bugstack.export.document.ReportElement;

import java.util.List;

/**
 * 报告封面模板。
 *
 * <p>模板返回与输出格式无关的文档元素，因此调用方可以动态组合段落、表格等封面内容，
 * DOCX 编译器负责将其写入目录之前的独立封面 Section。</p>
 */
public interface ReportCoverTemplate {

    /**
     * 获取报告文档名称，同时作为报告主标题。
     *
     * @return 文档名称
     */
    String getDocumentName();

    /**
     * 创建本次导出需要写入封面的有序元素。
     *
     * @return 封面元素列表
     */
    List<ReportElement> createElements();
}
