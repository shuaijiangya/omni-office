package cn.bugstack.export.example.composable;

import cn.bugstack.export.core.AbstractReportExportFacade;
import cn.bugstack.export.example.composable.module.AssessmentCalculationAnalysisReportModule;
import cn.bugstack.export.example.composable.module.AssessmentScenarioConstructionReportModule;
import cn.bugstack.export.example.composable.module.CombatProcessAnalysisReportModule;
import cn.bugstack.export.example.composable.module.ComparisonAnalysisReportModule;
import cn.bugstack.export.example.composable.module.ContributionRateAnalysisReportModule;
import cn.bugstack.export.example.composable.module.FunctionalOptimizationAnalysisReportModule;
import cn.bugstack.export.example.composable.module.ImpactAnalysisReportModule;
import cn.bugstack.export.example.composable.module.VulnerabilityAnalysisReportModule;

import java.util.Arrays;

/**
 * 可组合纯文本报告的业务导出门面。
 *
 * <p>导出门面只负责固定报告定义和注册全部可用模块，具体模块选择与数据装配仍由
 * {@link ComposableTextReportDefinition} 完成。</p>
 */
public final class ComposableTextReportExporter
        extends AbstractReportExportFacade<ComposableReportInput> {

    /** 创建已注册八个纯文本模块的导出器。 */
    public ComposableTextReportExporter() {
        super(new ComposableTextReportDefinition(), Arrays.asList(
                new AssessmentScenarioConstructionReportModule(),
                new AssessmentCalculationAnalysisReportModule(),
                new ContributionRateAnalysisReportModule(),
                new ImpactAnalysisReportModule(),
                new ComparisonAnalysisReportModule(),
                new CombatProcessAnalysisReportModule(),
                new VulnerabilityAnalysisReportModule(),
                new FunctionalOptimizationAnalysisReportModule()));
    }
}
