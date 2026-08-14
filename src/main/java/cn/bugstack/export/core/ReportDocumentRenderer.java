package cn.bugstack.export.core;

import cn.bugstack.export.api.ReportOutputFormat;
import cn.bugstack.export.definition.ReportBlueprint;
import cn.bugstack.export.document.ReportDocument;

import java.nio.file.Path;

/**
 * 将报告语义文档渲染到具体格式的输出适配器。
 */
public interface ReportDocumentRenderer {

    /**
     * 渲染报告文档。
     *
     * @param document 报告语义文档
     * @param blueprint 报告蓝图
     * @param format 输出格式
     * @param outputPath 输出文件路径
     */
    void render(ReportDocument document, ReportBlueprint blueprint, ReportOutputFormat format, Path outputPath);

    /**
     * 将报告语义文档渲染为内存字节。
     *
     * <p>实现类应返回与 {@code format} 一致的完整文件内容。默认实现保留了已有
     * 文件渲染器的兼容性；需要使用字节流导出时，渲染器必须覆写该方法。</p>
     *
     * @param document 报告语义文档
     * @param blueprint 报告蓝图
     * @param format 输出格式
     * @return 输出文件字节
     */
    default byte[] renderToBytes(ReportDocument document, ReportBlueprint blueprint, ReportOutputFormat format) {
        throw new UnsupportedOperationException("report renderer does not support byte output");
    }
}
