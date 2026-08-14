package cn.bugstack.export.core;

import cn.bugstack.export.api.ReportOutputFormat;
import cn.bugstack.export.api.ReportRequest;
import cn.bugstack.export.api.ReportResult;
import cn.bugstack.export.definition.ReportDefinition;
import cn.bugstack.export.docx.DocxReportCompiler;
import cn.bugstack.export.module.ReportConditionRegistry;
import cn.bugstack.export.module.ReportModule;
import cn.bugstack.export.module.ReportModuleRegistry;

import java.nio.file.Path;

/**
 * 固定一类强类型报告定义及其模块注册表的导出门面父类。
 *
 * <p>该父类只封装导出器装配和请求创建，不参与报告蓝图定义，也不处理业务数据。</p>
 *
 * @param <I> 报告业务入参类型
 */
public abstract class AbstractReportExportFacade<I> {

    private final ReportDefinition<I> definition;
    private final DefaultReportExporter exporter;

    /** 使用一份报告定义和它支持的全部模块创建导出门面。 */
    protected AbstractReportExportFacade(ReportDefinition<I> definition,
                                         Iterable<? extends ReportModule<?>> modules) {
        if (definition == null) {
            throw new IllegalArgumentException("report definition must not be null");
        }
        ReportModuleRegistry moduleRegistry = new ReportModuleRegistry(modules);
        this.definition = definition;
        this.exporter = new DefaultReportExporter(
                moduleRegistry,
                new ReportPlanner(moduleRegistry, new ReportConditionRegistry()),
                new ReportDocumentValidator(),
                new DocxReportCompiler());
    }

    /** 使用 DOCX 格式导出到文件。 */
    public final ReportResult export(I input, Path outputPath) {
        return exporter.export(request(input), outputPath);
    }

    /** 使用 DOCX 格式导出为内存字节。 */
    public final byte[] exportToBytes(I input) {
        return exporter.exportToBytes(request(input));
    }

    /** 获取当前门面固定使用的报告定义，便于应用层预览蓝图。 */
    public final ReportDefinition<I> getDefinition() {
        return definition;
    }

    private ReportRequest<I> request(I input) {
        return ReportRequest.<I>builder()
                .definition(definition)
                .input(input)
                .outputFormat(ReportOutputFormat.DOCX)
                .build();
    }
}
