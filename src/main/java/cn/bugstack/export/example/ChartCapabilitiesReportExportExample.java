package cn.bugstack.export.example;

import cn.bugstack.export.api.ReportOutputFormat;
import cn.bugstack.export.api.ReportRequest;
import cn.bugstack.export.core.DefaultReportExporter;
import cn.bugstack.export.core.ReportDocumentValidator;
import cn.bugstack.export.core.ReportPlanner;
import cn.bugstack.export.definition.AbstractReportDefinition;
import cn.bugstack.export.definition.ModuleSlot;
import cn.bugstack.export.definition.ReportBlueprint;
import cn.bugstack.export.definition.ReportLayout;
import cn.bugstack.export.definition.ReportStyleProfile;
import cn.bugstack.export.docx.DocxReportCompiler;
import cn.bugstack.export.document.CaptionPosition;
import cn.bugstack.export.document.ReportChartLegendPosition;
import cn.bugstack.export.document.ReportChartType;
import cn.bugstack.export.document.ReportSectionBuilder;
import cn.bugstack.export.module.AbstractReportModule;
import cn.bugstack.export.module.ModuleDescriptor;
import cn.bugstack.export.module.ReportConditionRegistry;
import cn.bugstack.export.module.ReportDataContext;
import cn.bugstack.export.module.ReportDataKey;
import cn.bugstack.export.module.ReportModuleContext;
import cn.bugstack.export.module.ReportModuleRegistry;
import cn.bugstack.office.docx.model.DocxPageOrientation;
import cn.bugstack.office.docx.model.DocxPaperSize;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

/**
 * 柱状图、饼图、对比图、折线图和雷达图的完整 Export 示例。
 *
 * <p>所有图表均由业务数据直接生成，并作为 Word 原生图表写入，因此在 Microsoft Word 中
 * 可以继续编辑数据、图例和图表样式。对比图既可使用多系列柱状图表达，也可使用
 * 单分类、单系列的横向条形图表达单指标单样本结果。</p>
 */
public final class ChartCapabilitiesReportExportExample {

    /** 示例文档固定输出位置。 */
    public static final Path OUTPUT = Path.of("target", "chart-capabilities-report-example.docx");

    private ChartCapabilitiesReportExportExample() { }

    /**
     * 生成五类图表能力示例报告。
     *
     * @param args 命令行参数，当前未使用
     * @throws Exception 创建目录或导出 Word 失败时抛出
     */
    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUTPUT.getParent());
        ReportModuleRegistry modules = new ReportModuleRegistry(Collections.singletonList(new ChartModule()));
        DefaultReportExporter exporter = new DefaultReportExporter(
                modules,
                new ReportPlanner(modules, new ReportConditionRegistry()),
                new ReportDocumentValidator(),
                new DocxReportCompiler());

        exporter.export(ReportRequest.<ChartExampleInput>builder()
                .definition(new ChartExampleDefinition())
                .input(ChartExampleInput.sample())
                .outputFormat(ReportOutputFormat.DOCX)
                .build(), OUTPUT);
        System.out.println("Chart capabilities report generated: " + OUTPUT.toAbsolutePath());
    }

    /** 示例报告定义。 */
    private static final class ChartExampleDefinition extends AbstractReportDefinition<ChartExampleInput> {
        private ChartExampleDefinition() {
            super("chart-capabilities-report", "Word 原生图表能力示例", "1.0");
        }

        @Override
        protected void configure(ReportBlueprint.Builder builder, ChartExampleInput input) {
            builder.title(input.title)
                    .metadata("文档平台组", "Word 原生图表自动生成")
                    .layout(ReportLayout.builder()
                            .styleProfile(ReportStyleProfile.BUSINESS_BRIEF)
                            .headingNumberingEnabled(true)
                            .header("omni-office · Word 原生图表能力")
                            .pageSetup(DocxPaperSize.A4, DocxPageOrientation.PORTRAIT,
                                    54D, 54D, 54D, 54D)
                            .build())
                    .module(ModuleSlot.builder(ChartModule.CODE).build());
        }

        @Override
        public void contributeData(ReportDataContext context, ChartExampleInput input) {
            context.put(ChartModule.DATA_KEY, input);
        }
    }

    /** 将同一业务输入组合成五种图表及两种对比图形态。 */
    private static final class ChartModule extends AbstractReportModule<ChartExampleInput> {
        private static final String CODE = "native-charts";
        private static final ReportDataKey<ChartExampleInput> DATA_KEY =
                ReportDataKey.of(CODE, ChartExampleInput.class);
        private static final ModuleDescriptor<ChartExampleInput> DESCRIPTOR =
                ModuleDescriptor.of(CODE, "数据可视化", DATA_KEY);

        @Override
        public ModuleDescriptor<ChartExampleInput> descriptor() { return DESCRIPTOR; }

        @Override
        protected void composeContent(ReportSectionBuilder section, ChartExampleInput data,
                                      ReportModuleContext context) {
            section.paragraph("示例数据通过 ReportChartBuilder 输入，图表在 Word 中保持原生可编辑。").chart(ReportChartType.COLUMN)
                    .title("三级指标评估结果")
                    .categories(data.quarters1)
                    .series("", data.revenue1)
//                    .axisTitles("", "")
                    .legend(false, ReportChartLegendPosition.BOTTOM)
                    .showValues(true)
                    .caption("三级指标评估结果", CaptionPosition.BELOW)
                    .end()
                    .section("柱状图", child -> child
                            .chart(ReportChartType.COLUMN)
                            .title("2026 年季度收入")
                            .categories(data.quarters)
                            .series("收入（万元）", data.revenue)
                            .axisTitles("季度", "万元")
                            .legend(false, ReportChartLegendPosition.BOTTOM)
                            .showValues(true)
                            .caption("季度收入柱状图", CaptionPosition.BELOW)
                            .end())
                    .pageBreak()
                    .section("饼图", child -> child
                            .chart(ReportChartType.PIE)
                            .title("产品收入结构")
                            .categories(data.products)
                            .series("收入占比", data.productRevenue)
                            .legend(true, ReportChartLegendPosition.RIGHT)
                            .showPercentages(true)
                            .caption("产品收入结构饼图", CaptionPosition.BELOW)
                            .end())
                    .pageBreak()
                    .section("对比图", child -> child
                            .chart(ReportChartType.COLUMN)
                            .title("年度业务指标对比")
                            .categories(data.metrics)
                            .series("2025 年", data.metric2025)
                            .series("2026 年", data.metric2026)
                            .axisTitles("指标", "完成值")
                            .legend(true, ReportChartLegendPosition.BOTTOM)
                            .showValues(true)
                            .caption("两年度业务指标对比图", CaptionPosition.ABOVE)
                            .end())
                    .pageBreak()
                    .section("横向单指标单样本对比图", child -> child
                            .chart(ReportChartType.BAR)
                            .title("单项指标评估")
                            .categories(data.singleMetric)
                            .series("", data.singleSampleValue)
                            .legend(false, ReportChartLegendPosition.BOTTOM)
                            .showValues(true)
                            .caption("横向单指标单样本对比图", CaptionPosition.BELOW)
                            .end())
                    .pageBreak()
                    .section("折线图", child -> child
                            .chart(ReportChartType.LINE)
                            .title("月度活跃用户趋势")
                            .categories(data.months)
                            .series("2025 年", data.users2025)
                            .series("2026 年", data.users2026)
                            .axisTitles("月份", "用户数（千）")
                            .legend(true, ReportChartLegendPosition.BOTTOM)
                            .caption("月度活跃用户趋势折线图", CaptionPosition.BELOW)
                            .end())
                    .pageBreak()
                    .section("雷达图", child -> child
                            .chart(ReportChartType.RADAR)
                            .title("产品能力综合评估")
                            .categories(data.capabilities)
                            .series("当前版本", data.currentScores)
                            .series("目标版本", data.targetScores)
                            .legend(true, ReportChartLegendPosition.BOTTOM)
                            .caption("产品能力雷达图", CaptionPosition.BELOW)
                            .end());
        }
    }

    /** 示例业务数据；真实项目中可由数据库、接口或 AI 填充。 */
    private static final class ChartExampleInput {
        private final String title = "Word 原生图表能力示例";
        private final String[] quarters1 = {"指标1", "指标2", "指标3", "指标4"};
        private final Double[] revenue1 = {128D, 156D, 184D, 213D};

        private final String[] quarters = {"第一季度", "第二季度", "第三季度", "第四季度"};
        private final Double[] revenue = {128D, 156D, 184D, 213D};
        private final String[] products = {"文档生成", "图形生成", "模板服务", "外部工具"};
        private final Double[] productRevenue = {42D, 24D, 21D, 13D};
        private final String[] metrics = {"新增客户", "续约客户", "交付项目", "客户满意度"};
        private final Double[] metric2025 = {68D, 74D, 81D, 86D};
        private final Double[] metric2026 = {85D, 88D, 96D, 93D};
        private final String[] singleMetric = {"任务完成率（%）","完成时间"};
        private final Double[] singleSampleValue = {92D,24D};
        private final String[] months = {"1月", "2月", "3月", "4月", "5月", "6月"};
        private final Double[] users2025 = {42D, 48D, 53D, 61D, 67D, 75D};
        private final Double[] users2026 = {55D, 63D, 72D, 84D, 96D, 112D};
        private final String[] capabilities = {"易用性", "稳定性", "性能", "扩展性", "安全性"};
        private final Double[] currentScores = {82D, 88D, 84D, 91D, 86D};
        private final Double[] targetScores = {92D, 94D, 92D, 96D, 95D};

        /** @return 固定示例数据 */
        private static ChartExampleInput sample() { return new ChartExampleInput(); }
    }
}
