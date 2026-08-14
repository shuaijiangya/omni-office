package cn.bugstack.export.example.composable;

import cn.bugstack.export.api.ReportOutputFormat;
import cn.bugstack.export.api.ReportRequest;
import cn.bugstack.export.api.ReportResult;
import cn.bugstack.export.core.DefaultReportExporter;
import cn.bugstack.export.core.ReportDocumentValidator;
import cn.bugstack.export.core.ReportPlanner;
import cn.bugstack.export.docx.DocxReportCompiler;
import cn.bugstack.export.example.composable.module.ComparisonAnalysisReportModule;
import cn.bugstack.export.example.composable.module.AssessmentCalculationAnalysisReportModule;
import cn.bugstack.export.example.composable.module.FunctionalOptimizationAnalysisReportModule;
import cn.bugstack.export.example.composable.module.ImpactAnalysisReportModule;
import cn.bugstack.export.example.composable.module.AssessmentScenarioConstructionReportModule;
import cn.bugstack.export.example.composable.module.VulnerabilityAnalysisReportModule;
import cn.bugstack.export.example.composable.module.CombatProcessAnalysisReportModule;
import cn.bugstack.export.example.composable.module.ContributionRateAnalysisReportModule;
import cn.bugstack.export.module.ReportConditionRegistry;
import cn.bugstack.export.module.ReportModuleRegistry;

import java.nio.file.Path;
import java.util.Arrays;

/**
 * 可组合纯文本报告的业务导出门面。
 *
 * <p>该门面一次性注册全部八个模块；具体导出哪些模块由
 * {@link cn.bugstack.export.example.composable.model.ComposableReportModuleModel#getSelectedModules()} 决定。</p>
 */
public final class ComposableTextReportExporter {

    /** 底层通用报告导出器。 */
    private final DefaultReportExporter delegate;

    /** 创建已注册八个纯文本模块的导出器。 */
    public ComposableTextReportExporter() {
        ReportModuleRegistry modules = new ReportModuleRegistry(Arrays.asList(
                new AssessmentScenarioConstructionReportModule(),
                new AssessmentCalculationAnalysisReportModule(),
                new ContributionRateAnalysisReportModule(),
                new ImpactAnalysisReportModule(),
                new ComparisonAnalysisReportModule(),
                new CombatProcessAnalysisReportModule(),
                new VulnerabilityAnalysisReportModule(),
                new FunctionalOptimizationAnalysisReportModule()));
        this.delegate = new DefaultReportExporter(
                modules,
                new ReportPlanner(modules, new ReportConditionRegistry()),
                new ReportDocumentValidator(),
                new DocxReportCompiler());
    }

    /**
     * 将指定组合导出为 Word 文件。
     *
     * @param input 报告入参
     * @param outputPath 以 {@code .docx} 结尾的输出路径
     * @return 导出结果
     */
    public ReportResult export(ComposableReportInput input, Path outputPath) {
        return delegate.export(request(input), outputPath);
    }

    /**
     * 将指定组合导出为 Word 字节，适用于 HTTP 下载。
     *
     * @param input 报告入参
     * @return 完整 docx 字节
     */
    public byte[] exportToBytes(ComposableReportInput input) {
        return delegate.exportToBytes(request(input));
    }

    /**
     * 创建底层通用报告请求。
     *
     * @param input 报告入参
     * @return DOCX 报告请求
     */
    private ReportRequest<ComposableReportInput> request(ComposableReportInput input) {
        return ReportRequest.<ComposableReportInput>builder()
                .definition(new ComposableTextReportDefinition())
                .input(input)
                .outputFormat(ReportOutputFormat.DOCX)
                .build();
    }
}
