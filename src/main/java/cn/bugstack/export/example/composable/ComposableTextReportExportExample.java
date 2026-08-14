package cn.bugstack.export.example.composable;

import cn.bugstack.export.example.composable.model.FunctionalOptimizationAnalysisModuleData;
import cn.bugstack.export.example.composable.model.ImpactAnalysisModuleData;
import cn.bugstack.export.example.composable.model.AssessmentScenarioConstructionModuleData;
import cn.bugstack.export.example.composable.model.CombatProcessAnalysisModuleData;
import cn.bugstack.export.example.composable.model.ComposableReportCoverModel;
import cn.bugstack.export.example.composable.model.ComposableReportModuleModel;

import java.nio.file.Path;

/**
 * 按入参任意组合八个对象型模块的 Word 导出示例。
 */
public final class ComposableTextReportExportExample {

    private ComposableTextReportExportExample() {
    }

    /**
     * 演示只选择八个模块中的四个，并按调用顺序导出。
     *
     * @param args 命令行参数，当前未使用
     */
    public static void main(String[] args) {
        ComposableReportCoverModel cover = new ComposableReportCoverModel(
                "评估分析报告", "联合任务方案", "V1.0");
        ComposableReportModuleModel modules = ComposableReportModuleModel.builder()
//                .header("评估分析报告")
                .module(new AssessmentScenarioConstructionModuleData("根据任务目标、参评对象和环境约束构设评估场景。"))
                .module(new ImpactAnalysisModuleData("分析关键要素变化对任务结果产生的影响。"))
                .module(new CombatProcessAnalysisModuleData("围绕任务阶段梳理作战流程及关键活动。"))
                .module(new FunctionalOptimizationAnalysisModuleData("结合分析结论提出功能优化方向和改进建议。"))
                .build();
        ComposableReportInput input = ComposableReportInput.builder(cover, modules)
                .preparedBy("评估分析组")
                .build();

        new ComposableTextReportExporter().export(
                input, Path.of("target", "composable-text-report-example.docx"));
    }
}
