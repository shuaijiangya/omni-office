package cn.bugstack.export.example.composable;

import cn.bugstack.export.composable.AbstractComposableReportDefinition;
import cn.bugstack.export.example.composable.model.ComparisonAnalysisModuleData;
import cn.bugstack.export.example.composable.model.AssessmentCalculationAnalysisModuleData;
import cn.bugstack.export.example.composable.model.FunctionalOptimizationAnalysisModuleData;
import cn.bugstack.export.example.composable.model.ImpactAnalysisModuleData;
import cn.bugstack.export.example.composable.model.AssessmentScenarioConstructionModuleData;
import cn.bugstack.export.example.composable.model.VulnerabilityAnalysisModuleData;
import cn.bugstack.export.example.composable.model.CombatProcessAnalysisModuleData;
import cn.bugstack.export.example.composable.model.ContributionRateAnalysisModuleData;
import cn.bugstack.export.example.composable.module.ComparisonAnalysisReportModule;
import cn.bugstack.export.example.composable.module.AssessmentCalculationAnalysisReportModule;
import cn.bugstack.export.example.composable.module.FunctionalOptimizationAnalysisReportModule;
import cn.bugstack.export.example.composable.module.ImpactAnalysisReportModule;
import cn.bugstack.export.example.composable.module.AssessmentScenarioConstructionReportModule;
import cn.bugstack.export.example.composable.module.VulnerabilityAnalysisReportModule;
import cn.bugstack.export.example.composable.module.CombatProcessAnalysisReportModule;
import cn.bugstack.export.example.composable.module.ContributionRateAnalysisReportModule;
import cn.bugstack.export.module.ReportDataContext;

/**
 * 根据业务入参动态选择模块的报告定义。
 */
public final class ComposableTextReportDefinition
        extends AbstractComposableReportDefinition<ComposableReportInput> {

    /** 创建可组合文本报告定义。 */
    public ComposableTextReportDefinition() {
        super("composable-text-report", "评估报告", "1.0");
    }

    /**
     * 只为已选择模块装配纯文本数据。
     *
     * @param context 报告数据上下文
     * @param input 可组合报告入参
     */
    @Override
    public void contributeData(ReportDataContext context, ComposableReportInput input) {
        requireInput(input);
        for (ComposableReportModule module : input.getModuleModel().getSelectedModules()) {
            contributeModuleData(context, input, module);
        }
    }

    /**
     * 将八种不同类型的业务对象写入各自模块的数据键。
     *
     * @param context 报告数据上下文
     * @param input 报告入参
     * @param module 当前模块类型
     */
    private void contributeModuleData(ReportDataContext context, ComposableReportInput input,
                                      ComposableReportModule module) {
        switch (module) {
            case ASSESSMENT_SCENARIO_CONSTRUCTION:
                context.put(AssessmentScenarioConstructionReportModule.DATA_KEY,
                        input.getModuleModel().requireModuleData(module, AssessmentScenarioConstructionModuleData.class));
                break;
            case ASSESSMENT_CALCULATION_ANALYSIS:
                context.put(AssessmentCalculationAnalysisReportModule.DATA_KEY,
                        input.getModuleModel().requireModuleData(module, AssessmentCalculationAnalysisModuleData.class));
                break;
            case CONTRIBUTION_RATE_ANALYSIS:
                context.put(ContributionRateAnalysisReportModule.DATA_KEY,
                        input.getModuleModel().requireModuleData(module, ContributionRateAnalysisModuleData.class));
                break;
            case IMPACT_ANALYSIS:
                context.put(ImpactAnalysisReportModule.DATA_KEY,
                        input.getModuleModel().requireModuleData(module, ImpactAnalysisModuleData.class));
                break;
            case COMPARISON_ANALYSIS:
                context.put(ComparisonAnalysisReportModule.DATA_KEY,
                        input.getModuleModel().requireModuleData(module, ComparisonAnalysisModuleData.class));
                break;
            case COMBAT_PROCESS_ANALYSIS:
                context.put(CombatProcessAnalysisReportModule.DATA_KEY,
                        input.getModuleModel().requireModuleData(module, CombatProcessAnalysisModuleData.class));
                break;
            case VULNERABILITY_ANALYSIS:
                context.put(VulnerabilityAnalysisReportModule.DATA_KEY,
                        input.getModuleModel().requireModuleData(module, VulnerabilityAnalysisModuleData.class));
                break;
            case FUNCTIONAL_OPTIMIZATION_ANALYSIS:
                context.put(FunctionalOptimizationAnalysisReportModule.DATA_KEY,
                        input.getModuleModel().requireModuleData(module, FunctionalOptimizationAnalysisModuleData.class));
                break;
            default:
                throw new IllegalArgumentException("unsupported report module: " + module);
        }
    }

    /**
     * 校验报告入参。
     *
     * @param input 待校验入参
     */
    private void requireInput(ComposableReportInput input) {
        if (input == null) {
            throw new IllegalArgumentException("composable report input must not be null");
        }
    }

}
