package cn.bugstack.application.document;

import cn.bugstack.export.api.ReportOutputFormat;
import cn.bugstack.protocol.document.DocumentSpec;

import java.nio.file.Path;

/**
 * 无业务模板的 DocumentSpec 导出入口。
 */
public interface DynamicDocumentExporter {

    byte[] exportToBytes(DocumentSpec spec, ReportOutputFormat format);

    void export(DocumentSpec spec, ReportOutputFormat format, Path outputPath);
}
