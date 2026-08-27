package cn.bugstack.export.example;

import cn.bugstack.export.api.ReportOutputFormat;
import cn.bugstack.export.api.ReportRequest;
import cn.bugstack.export.context.ReportBasicInfo;
import cn.bugstack.export.core.DefaultReportExporter;
import cn.bugstack.export.core.ReportDocumentValidator;
import cn.bugstack.export.core.ReportPlanner;
import cn.bugstack.export.definition.AbstractReportDefinition;
import cn.bugstack.export.definition.ModuleSlot;
import cn.bugstack.export.definition.ReportBlueprint;
import cn.bugstack.export.definition.ReportLayout;
import cn.bugstack.export.definition.ReportStyleProfile;
import cn.bugstack.export.docx.DocxReportCompiler;
import cn.bugstack.export.document.ReportSectionBuilder;
import cn.bugstack.export.module.AbstractReportModule;
import cn.bugstack.export.module.ModuleDescriptor;
import cn.bugstack.export.module.ReportConditionRegistry;
import cn.bugstack.export.module.ReportDataContext;
import cn.bugstack.export.module.ReportDataKey;
import cn.bugstack.export.module.ReportModuleContext;
import cn.bugstack.export.module.ReportModuleRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 外部系统接入报告导出框架的完整示例。
 *
 * <p>应用只需要定义输入对象、报告定义和若干模块，再在启动期将模块注册到
 * {@link ReportModuleRegistry}。业务模块只写报告语义，不依赖 Aspose、Spring 或数据库实体。</p>
 */
public final class AssessmentReportExportExample {

    private AssessmentReportExportExample() {
    }

    /**
     * 生成一份包含基础信息、段落、列表、表格、分页和类设计表格的 docx 报告。
     *
     * @param args 命令行参数，当前未使用
     * @throws Exception 文件生成失败时抛出
     */
    public static void main(String[] args) throws Exception {
        Path output = Path.of("target", "assessment-report-example.docx");
        Files.createDirectories(output.getParent());

        ReportModuleRegistry modules = new ReportModuleRegistry(Arrays.asList(
                new OverviewModule(), new RiskModule(), new ConclusionModule()));
        DefaultReportExporter exporter = new DefaultReportExporter(
                modules,
                new ReportPlanner(modules, new ReportConditionRegistry()),
                new ReportDocumentValidator(),
                new DocxReportCompiler());

        AssessmentInput input = AssessmentInput.sample();
        exporter.export(ReportRequest.<AssessmentInput>builder()
                .definition(new AssessmentReportDefinition())
                .input(input)
                .outputFormat(ReportOutputFormat.DOCX)
                .build(), output);
    }

    /**
     * 示例评估报告的蓝图定义与模块数据装配器。
     */
    private static final class AssessmentReportDefinition extends AbstractReportDefinition<AssessmentInput> {

        private AssessmentReportDefinition() {
            super("assessment-report", "系统评估报告", "1.0");
        }

        @Override
        protected void configure(ReportBlueprint.Builder builder, AssessmentInput input) {
            builder.title(input.getTitle())
                    .metadata(input.getPreparedBy(), "系统评估结果")
                    .basicInfo(input.toBasicInfo())
                    .layout(ReportLayout.builder()
                            .styleProfile(ReportStyleProfile.GJB_438C)
                            .headingNumberingEnabled(true)
                            .tableOfContents(3)
                            .header("系统评估报告")
                            .cover("系统评估报告", input.getAssessmentTarget(), "V1.0")
                            .build())
                    .module(ModuleSlot.builder(OverviewModule.CODE).build())
                    .module(ModuleSlot.builder(RiskModule.CODE).dependsOn(OverviewModule.CODE).build())
                    .module(ModuleSlot.builder(ConclusionModule.CODE).dependsOn(RiskModule.CODE).build());
        }

        @Override
        public void contributeData(ReportDataContext context, AssessmentInput input) {
            context.put(OverviewModule.DATA_KEY, input);
            context.put(RiskModule.DATA_KEY, input.getRisks());
            context.put(ConclusionModule.DATA_KEY, input.getConclusion());
        }
    }

    /**
     * 输出评估范围、依据及概要结论的一级模块。
     */
    private static final class OverviewModule extends AbstractReportModule<AssessmentInput> {

        /** 概述模块的唯一编码。 */
        private static final String CODE = "overview";
        /** 概述模块输入数据的键。 */
        private static final ReportDataKey<AssessmentInput> DATA_KEY = ReportDataKey.of("overview", AssessmentInput.class);
        /** 概述模块的静态描述符。 */
        private static final ModuleDescriptor<AssessmentInput> DESCRIPTOR =
                ModuleDescriptor.of(CODE, "评估概述", DATA_KEY);

        @Override
        public ModuleDescriptor<AssessmentInput> descriptor() {
            return DESCRIPTOR;
        }

        @Override
        protected void composeContent(ReportSectionBuilder section, AssessmentInput data, ReportModuleContext context) {
            section.paragraph("本报告对 " + data.getAssessmentTarget() + " 的当前版本进行结构、风险与交付评估。")
                    .bullet("评估范围：核心服务、接口边界和主要数据流。")
                    .bullet("评估依据：需求基线、设计说明和测试记录。")
                    .table("评估项", "结论", "说明")
                    .style("TableHeader")
                    .widths(130, 100, 260)
                    .row("架构边界", "通过", "模块职责清晰，导出层与渲染层已隔离。")
                    .row("可维护性", "通过", "新增模块可通过策略注册表独立接入。")
                    .merge(1,1,2,1)
                    .caption("评估概览", true)
                    .end();
        }
    }

    /**
     * 输出风险清单及关键导出类设计的一级模块。
     */
    private static final class RiskModule extends AbstractReportModule<List<RiskItem>> {

        /** 风险模块的唯一编码。 */
        private static final String CODE = "risk";
        /** 风险模块输入数据的键。 */
        @SuppressWarnings("unchecked")
        private static final ReportDataKey<List<RiskItem>> DATA_KEY =
                ReportDataKey.of("risks", (Class<List<RiskItem>>) (Class<?>) List.class);
        /** 风险模块的静态描述符。 */
        private static final ModuleDescriptor<List<RiskItem>> DESCRIPTOR =
                ModuleDescriptor.of(CODE, "风险项", DATA_KEY);

        @Override
        public ModuleDescriptor<List<RiskItem>> descriptor() {
            return DESCRIPTOR;
        }

        @Override
        protected void composeContent(ReportSectionBuilder section, List<RiskItem> data, ReportModuleContext context) {
            section.paragraph("以下风险项按当前优先级列示，需在发布前完成责任分配和闭环验证。");
            cn.bugstack.export.document.ReportTableBuilder table = section.table("编号", "风险描述", "等级", "处置措施")
                    .style("TableHeader")
                    .widths(65, 210, 70, 215);
            for (RiskItem item : data) {
                table.row(item.getId(), item.getDescription(), item.getLevel(), item.getMitigation());
            }
            table.caption("风险清单", true).end();
            section.section("风险分析", analysis -> analysis
                    .paragraph("风险分析从影响范围、发生概率和处置优先级三个维度进行判断。")
                    .section("处置建议", recommendation -> recommendation
                            .paragraph("对高优先级风险应先明确责任人、完成时限和验证方式。")
                            .numbered("制定处置计划并分配责任人。")
                            .numbered("完成处置后执行回归验证并记录结果。")));
            section.pageBreak()
                    .section("关键类设计", child -> child.classDesignTable(
                            "DefaultReportExporter 类设计",
                            Path.of("src/main/java"),
                            "cn.bugstack.export.core.DefaultReportExporter"));
        }
    }

    /**
     * 输出报告结论与后续处置事项的一级模块。
     */
    private static final class ConclusionModule extends AbstractReportModule<String> {

        /** 结论模块的唯一编码。 */
        private static final String CODE = "conclusion";
        /** 结论模块输入数据的键。 */
        private static final ReportDataKey<String> DATA_KEY = ReportDataKey.of("conclusion", String.class);
        /** 结论模块的静态描述符。 */
        private static final ModuleDescriptor<String> DESCRIPTOR =
                ModuleDescriptor.of(CODE, "结论", DATA_KEY);

        @Override
        public ModuleDescriptor<String> descriptor() {
            return DESCRIPTOR;
        }

        @Override
        protected void composeContent(ReportSectionBuilder section, String data, ReportModuleContext context) {
            section.paragraph(data)
                    .numbered("完成高优先级风险处置后，执行回归测试。")
                    .numbered("由评审责任人确认结果并归档本报告。");
        }
    }

    /**
     * 示例报告的聚合业务输入。
     */
    private static final class AssessmentInput {

        /** 报告标题。 */
        private final String title;
        /** 被评估对象。 */
        private final String assessmentTarget;
        /** 报告编制人。 */
        private final String preparedBy;
        /** 风险项列表。 */
        private final List<RiskItem> risks;
        /** 评估结论。 */
        private final String conclusion;

        private AssessmentInput(String title, String assessmentTarget, String preparedBy,
                                List<RiskItem> risks, String conclusion) {
            this.title = title;
            this.assessmentTarget = assessmentTarget;
            this.preparedBy = preparedBy;
            this.risks = risks;
            this.conclusion = conclusion;
        }

        /**
         * 创建用于演示报告导出的固定输入数据。
         *
         * @return 示例评估输入
         */
        private static AssessmentInput sample() {
            return new AssessmentInput("系统评估报告", "omni-office", "架构组",
                    Arrays.asList(
                            new RiskItem("R-001", "外部模块数据不完整", "中", "在计划阶段校验必填数据。"),
                            new RiskItem("R-002", "渲染格式扩展受限", "低", "通过语义树和编译器 SPI 隔离格式实现。")),
                    "当前设计满足模块化导出要求，可作为后续 PDF、HTML 等编译目标的稳定基础。");
        }

        /**
         * 将输入对象转换为报告基础信息。
         *
         * @return 报告基础信息
         */
        private ReportBasicInfo toBasicInfo() {
            ReportBasicInfo basicInfo = new ReportBasicInfo();
            basicInfo.setReportNo("ASSESSMENT-20260806-001");
            basicInfo.setAssessmentTarget(assessmentTarget);
            basicInfo.setPreparedBy(preparedBy);
            basicInfo.setGeneratedTime(LocalDateTime.of(2026, 8, 6, 15, 0));
            basicInfo.getProperties().put("评估版本", "V1.0");
            return basicInfo;
        }

        /**
         * 获取报告标题。
         *
         * @return 报告标题
         */
        private String getTitle() {
            return title;
        }

        /**
         * 获取评估对象。
         *
         * @return 评估对象
         */
        private String getAssessmentTarget() {
            return assessmentTarget;
        }

        /**
         * 获取报告编制人。
         *
         * @return 报告编制人
         */
        private String getPreparedBy() {
            return preparedBy;
        }

        /**
         * 获取风险项列表。
         *
         * @return 风险项列表
         */
        private List<RiskItem> getRisks() {
            return risks;
        }

        /**
         * 获取评估结论。
         *
         * @return 评估结论
         */
        private String getConclusion() {
            return conclusion;
        }
    }

    /**
     * 示例风险清单中的单条风险数据。
     */
    private static final class RiskItem {

        /** 风险项编号。 */
        private final String id;
        /** 风险描述。 */
        private final String description;
        /** 风险等级。 */
        private final String level;
        /** 风险处置措施。 */
        private final String mitigation;

        private RiskItem(String id, String description, String level, String mitigation) {
            this.id = id;
            this.description = description;
            this.level = level;
            this.mitigation = mitigation;
        }

        /**
         * 获取风险项编号。
         *
         * @return 风险项编号
         */
        private String getId() {
            return id;
        }

        /**
         * 获取风险描述。
         *
         * @return 风险描述
         */
        private String getDescription() {
            return description;
        }

        /**
         * 获取风险等级。
         *
         * @return 风险等级
         */
        private String getLevel() {
            return level;
        }

        /**
         * 获取风险处置措施。
         *
         * @return 风险处置措施
         */
        private String getMitigation() {
            return mitigation;
        }
    }
}
