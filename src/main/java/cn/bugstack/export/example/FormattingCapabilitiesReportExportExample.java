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
import cn.bugstack.export.document.ReportSectionBuilder;
import cn.bugstack.export.document.ReportTableBuilder;
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
import java.util.Arrays;

/**
 * 报告 Export 入口使用富文本段落和响应式表格的完整示例。
 *
 * <p>示例结构与 {@link AssessmentReportExportExample} 一致：业务侧定义输入对象、报告蓝图和模块，
 * 模块只组合报告语义，不直接依赖 Aspose。生成结果展示同段多文本范围、页面宽度自适应表格、
 * 默认居中文字、表头与表内容独立样式、单元格合并以及题注上下位置。</p>
 */
public final class FormattingCapabilitiesReportExportExample {

    /** 示例文档的固定输出位置。 */
    public static final Path OUTPUT = Path.of("target", "formatting-capabilities-report-example.docx");

    private FormattingCapabilitiesReportExportExample() {
    }

    /**
     * 生成格式能力示例报告。
     *
     * @param args 命令行参数，当前未使用
     * @throws Exception 输出目录或 Word 文件创建失败时抛出
     */
    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUTPUT.getParent());

        ReportModuleRegistry modules = new ReportModuleRegistry(Arrays.asList(
                new RichTextModule(), new ResponsiveTableModule()));
        DefaultReportExporter exporter = new DefaultReportExporter(
                modules,
                new ReportPlanner(modules, new ReportConditionRegistry()),
                new ReportDocumentValidator(),
                new DocxReportCompiler());

        exporter.export(ReportRequest.<FormattingExampleInput>builder()
                .definition(new FormattingExampleDefinition())
                .input(FormattingExampleInput.sample())
                .outputFormat(ReportOutputFormat.DOCX)
                .build(), OUTPUT);

        System.out.println("Formatting capabilities report generated: " + OUTPUT.toAbsolutePath());
    }

    /** 示例报告蓝图及模块数据装配器。 */
    private static final class FormattingExampleDefinition
            extends AbstractReportDefinition<FormattingExampleInput> {

        private FormattingExampleDefinition() {
            super("formatting-capabilities-report", "Word 格式能力示例报告", "1.0");
        }

        @Override
        protected void configure(ReportBlueprint.Builder builder, FormattingExampleInput input) {
            builder.title(input.title)
                    .metadata(input.preparedBy, "富文本与响应式表格能力")
                    .layout(ReportLayout.builder()
                            .styleProfile(ReportStyleProfile.DEFAULT)
                            .headingNumberingEnabled(true)
                            .header("omni-office · Export 格式能力示例")
                            .pageSetup(DocxPaperSize.A4, DocxPageOrientation.PORTRAIT,
                                    72D, 54D, 72D, 54D)
                            .build())
                    .module(ModuleSlot.builder(RichTextModule.CODE).build())
                    .module(ModuleSlot.builder(ResponsiveTableModule.CODE)
                            .dependsOn(RichTextModule.CODE)
                            .build());
        }

        @Override
        public void contributeData(ReportDataContext context, FormattingExampleInput input) {
            context.put(RichTextModule.DATA_KEY, input);
            context.put(ResponsiveTableModule.DATA_KEY, input);
        }
    }

    /** 输出同一段落内多个独立样式文本范围。 */
    private static final class RichTextModule extends AbstractReportModule<FormattingExampleInput> {

        private static final String CODE = "rich-text";
        private static final ReportDataKey<FormattingExampleInput> DATA_KEY =
                ReportDataKey.of(CODE, FormattingExampleInput.class);
        private static final ModuleDescriptor<FormattingExampleInput> DESCRIPTOR =
                ModuleDescriptor.of(CODE, "富文本段落", DATA_KEY);

        @Override
        public ModuleDescriptor<FormattingExampleInput> descriptor() {
            return DESCRIPTOR;
        }

        @Override
        protected void composeContent(ReportSectionBuilder section, FormattingExampleInput data,
                                      ReportModuleContext context) {
            section.paragraph("一个 paragraph 可以顺序组合多个 textRange，未设置的属性继承段落样式。");
            section.richParagraph()
                    .style("BodyText")
                    .text("评估对象：")
                    .text(data.target, style -> {
                        style.setFontColor("#1F4E79");
                        style.setBold(true);
                    })
                    .text("；当前状态：")
                    .text(data.status, style -> {
                        style.setFontColor("#548235");
                        style.setBold(true);
                    })
                    .text("；风险提示", style -> {
                        style.setFontColor("#C00000");
                        style.setUnderline(true);
                    })
                    .text("；Arial 14pt", style -> {
                        style.setFontFamily("Arial");
                        style.setFontSize(14D);
                        style.setItalic(true);
                    })
                    .end();
        }
    }

    /** 输出按页面正文宽度自适应且文字默认居中的表格。 */
    private static final class ResponsiveTableModule extends AbstractReportModule<FormattingExampleInput> {

        private static final String CODE = "responsive-table";
        private static final ReportDataKey<FormattingExampleInput> DATA_KEY =
                ReportDataKey.of(CODE, FormattingExampleInput.class);
        private static final ModuleDescriptor<FormattingExampleInput> DESCRIPTOR =
                ModuleDescriptor.of(CODE, "响应式表格", DATA_KEY);

        @Override
        public ModuleDescriptor<FormattingExampleInput> descriptor() {
            return DESCRIPTOR;
        }

        @Override
        protected void composeContent(ReportSectionBuilder section, FormattingExampleInput data,
                                      ReportModuleContext context) {
            section.paragraph("columnWidths 仅表示列宽比例，表格总宽度根据纸张方向和页边距自动计算。");
            section.table("能力", "说明", "结果")
                    .style("TableHeader")
                    .widths(2, 5, 2)
                    .row("页面自适应", "A4 页面左右边距各 54pt，表格自动占满正文宽度。", "通过")
                    .row("默认居中", "表头与普通单元格文字默认水平、垂直居中。", "通过")
                    .row("区域样式", "表头默认中文黑体、不加粗、黑色；内容默认中文宋体。", "通过")
                    .caption("响应式列宽与默认居中", true, CaptionPosition.ABOVE)
                    .end();

            ReportTableBuilder mergedTable = section.table("业务域", "组件", "状态")
                    .style("TableHeader")
                    .widths(2, 4, 2)
                    .headerTextStyle(style -> {
                        style.setAsciiFontFamily("Arial");
                        style.setFarEastFontFamily("微软雅黑");
                        style.setFontColor("#000000");
                        style.setBold(false);
                    })
                    .bodyTextStyle(style -> {
                        style.setAsciiFontFamily("Calibri");
                        style.setFarEastFontFamily("仿宋");
                        style.setFontColor("#000000");
                    })
                    .row("文档生成", "DocumentSpec → ReportDocument", "正常")
                    .row("", "ReportDocument → Word", "正常")
                    .merge(1, 0, 2, 1)
                    .caption("纵向合并与下方题注", true, CaptionPosition.BELOW);
            mergedTable.end();
        }
    }

    /** 示例报告输入对象。 */
    private static final class FormattingExampleInput {

        private final String title;
        private final String target;
        private final String preparedBy;
        private final String status;

        private FormattingExampleInput(String title, String target, String preparedBy, String status) {
            this.title = title;
            this.target = target;
            this.preparedBy = preparedBy;
            this.status = status;
        }

        /**
         * 创建固定示例数据。
         *
         * @return 格式能力示例输入
         */
        private static FormattingExampleInput sample() {
            return new FormattingExampleInput(
                    "Word 格式能力示例报告", "omni-office", "文档平台组", "能力可用");
        }
    }
}
