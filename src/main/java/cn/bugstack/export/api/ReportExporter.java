package cn.bugstack.export.api;

import java.nio.file.Path;

/**
 * 报告导出门面。
 */
public interface ReportExporter {

    /**
     * 按请求生成报告文件。
     *
     * @param request 报告请求
     * @param outputPath 输出文件路径
     * @param <T> 报告入口业务数据类型
     * @return 导出结果
     */
    <T> ReportResult export(ReportRequest<T> request, Path outputPath);

    /**
     * 按请求生成内存中的报告字节。
     *
     * <p>返回内容的格式由 {@link ReportRequest#getOutputFormat()} 决定。该方法适用于
     * HTTP 下载、对象存储上传和消息附件等不需要先落地本地文件的场景。</p>
     *
     * @param request 报告请求
     * @param <T> 报告入口业务数据类型
     * @return 生成后的 docx 或 PDF 字节数组
     */
    <T> byte[] exportToBytes(ReportRequest<T> request);
}
