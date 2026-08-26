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
import cn.bugstack.office.docx.render.AsposeWordsLicenseLoader;
import com.aspose.words.CellMerge;
import com.aspose.words.CellVerticalAlignment;
import com.aspose.words.Chart;
import com.aspose.words.ChartSeries;
import com.aspose.words.Document;
import com.aspose.words.DocumentBuilder;
import com.aspose.words.HeaderFooterType;
import com.aspose.words.LegendPosition;
import com.aspose.words.LineStyle;
import com.aspose.words.PaperSize;
import com.aspose.words.ParagraphAlignment;
import com.aspose.words.PreferredWidth;
import com.aspose.words.SaveFormat;
import com.aspose.words.Shape;
import com.aspose.words.StyleIdentifier;
import com.aspose.words.Table;
import com.aspose.words.TableAlignment;
import com.aspose.words.Underline;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

/**
 * Aspose.Words 原生写法与 omni-office 业务框架写法的可运行迁移对照示例。
 *
 * <p>两条生成路径共享同一个业务输入，并输出语义一致的 Word 文档。
 * {@link #generateWithAspose(MigrationInput, Path)} 保留传统游标式 Aspose 写法，
 * {@link #generateWithFramework(MigrationInput, Path)} 展示如何将游标、样式和节点操作
 * 替换为报告定义、强类型模块和语义 Builder。</p>
 */
public final class AsposeWordsMigrationComparisonExample {

    /** Aspose.Words 原生写法输出。 */
    public static final Path ASPOSE_OUTPUT =
            Path.of("target", "aspose-words-direct-migration-example.docx");

    /** omni-office 框架写法输出。 */
    public static final Path FRAMEWORK_OUTPUT =
            Path.of("target", "omni-office-migration-example.docx");

    private static final String ASCII_FONT = "Times New Roman";
    private static final String BODY_FAR_EAST_FONT = "宋体";
    private static final String HEADING_FAR_EAST_FONT = "黑体";

    private AsposeWordsMigrationComparisonExample() {
    }

    /**
     * 用两种写法各生成一份对照文档。
     *
     * @param args 命令行参数，当前未使用
     * @throws Exception Aspose 或框架导出失败时抛出
     */
    public static void main(String[] args) throws Exception {
        Files.createDirectories(ASPOSE_OUTPUT.getParent());
        MigrationInput input = MigrationInput.sample();
        generateWithAspose(input, ASPOSE_OUTPUT);
        generateWithFramework(input, FRAMEWORK_OUTPUT);
        System.out.println("Aspose direct example generated: " + ASPOSE_OUTPUT.toAbsolutePath());
        System.out.println("omni-office example generated: " + FRAMEWORK_OUTPUT.toAbsolutePath());
    }

    /**
     * 使用 Aspose.Words 原生 API 生成示例。
     *
     * <p>该方法故意保留 {@link DocumentBuilder} 游标、格式状态清理、单元格宽度计算、
     * 合并标记和图表系列组装，便于与框架写法逐项对照。</p>
     *
     * @param input 共享业务数据
     * @param output 输出路径
     * @throws Exception Word 构建或保存失败时抛出
     */
    public static void generateWithAspose(MigrationInput input, Path output) throws Exception {
        AsposeWordsLicenseLoader.applyConfiguredLicense();
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);

        document.getBuiltInDocumentProperties().setTitle(input.title);
        document.getBuiltInDocumentProperties().setAuthor(input.preparedBy);
        document.getBuiltInDocumentProperties().setSubject("从 Aspose.Words 迁移到 omni-office");
        configureAsposePage(builder);
        writeAsposeHeaderAndFooter(builder);

        applyAsposeParagraphStyle(builder, StyleIdentifier.TITLE, HEADING_FAR_EAST_FONT, 22D, true,
                ParagraphAlignment.LEFT);
        builder.writeln(input.title);
        applyAsposeParagraphStyle(builder, StyleIdentifier.HEADING_1, HEADING_FAR_EAST_FONT, 16D, true,
                ParagraphAlignment.LEFT);
        builder.writeln("Migration Comparison");

        beginAsposeBodyParagraph(builder);
        builder.write("Target: ");
        applyAsposeRun(builder, "#1F4E79", true, false, false);
        builder.write(input.target);
        applyAsposeRun(builder, "#000000", false, false, false);
        builder.write("; status: ");
        applyAsposeRun(builder, "#548235", true, false, false);
        builder.write(input.status);
        applyAsposeRun(builder, "#000000", false, false, false);
        builder.write("; migration principle: ");
        applyAsposeRun(builder, "#C00000", false, false, true);
        builder.write("business modules no longer manipulate the Word cursor");
        builder.writeln(".");

        writeAsposeBullet(builder, "Business data is passed through strongly typed objects.");
        writeAsposeBullet(builder, "The report blueprint owns page layout, styles, and output format.");
        writeAsposeBullet(builder, "Semantic builders compose paragraphs, tables, and charts.");

        writeAsposeCaption(builder, "Table 1 Component migration status");
        writeAsposeTable(builder, input);
        writeAsposeChart(builder, input);

        Files.createDirectories(output.toAbsolutePath().getParent());
        document.save(output.toString(), SaveFormat.DOCX);
    }

    /**
     * 使用 omni-office 报告定义、模块和语义 Builder 生成示例。
     *
     * @param input 共享业务数据
     * @param output 输出路径
     * @throws Exception 报告规划或导出失败时抛出
     */
    public static void generateWithFramework(MigrationInput input, Path output) throws Exception {
        ReportModuleRegistry modules = new ReportModuleRegistry(
                Collections.singletonList(new MigrationModule()));
        DefaultReportExporter exporter = new DefaultReportExporter(
                modules,
                new ReportPlanner(modules, new ReportConditionRegistry()),
                new ReportDocumentValidator(),
                new DocxReportCompiler());

        exporter.export(ReportRequest.<MigrationInput>builder()
                .definition(new MigrationDefinition())
                .input(input)
                .outputFormat(ReportOutputFormat.DOCX)
                .build(), output);
    }

    /** 设置 Aspose 页面纸张和页边距。 */
    private static void configureAsposePage(DocumentBuilder builder) throws Exception {
        com.aspose.words.PageSetup page = builder.getCurrentSection().getPageSetup();
        page.setPaperSize(PaperSize.A4);
        page.setTopMargin(72D);
        page.setRightMargin(54D);
        page.setBottomMargin(72D);
        page.setLeftMargin(54D);
    }

    /** 使用 Aspose 游标切换写入页眉和页码域。 */
    private static void writeAsposeHeaderAndFooter(DocumentBuilder builder) throws Exception {
        builder.moveToHeaderFooter(HeaderFooterType.HEADER_PRIMARY);
        builder.getParagraphFormat().setAlignment(ParagraphAlignment.CENTER);
        applyAsposeFont(builder, BODY_FAR_EAST_FONT, 9D, false);
        builder.write("omni-office · Aspose migration comparison");

        builder.moveToHeaderFooter(HeaderFooterType.FOOTER_PRIMARY);
        builder.getParagraphFormat().setAlignment(ParagraphAlignment.CENTER);
        applyAsposeFont(builder, BODY_FAR_EAST_FONT, 9D, false);
        builder.write("Page ");
        builder.insertField("PAGE", "1");
        builder.moveToDocumentEnd();
    }

    /** 写入 Aspose 项目符号列表项并手工结束列表状态。 */
    private static void writeAsposeBullet(DocumentBuilder builder, String text) throws Exception {
        beginAsposeBodyParagraph(builder);
        builder.getListFormat().applyBulletDefault();
        builder.writeln(text);
        builder.getListFormat().removeNumbers();
    }

    /** 写入居中题注。 */
    private static void writeAsposeCaption(DocumentBuilder builder, String text) throws Exception {
        applyAsposeParagraphStyle(builder, StyleIdentifier.CAPTION, BODY_FAR_EAST_FONT, 9D, false,
                ParagraphAlignment.CENTER);
        builder.writeln(text);
    }

    /** 使用 Aspose 手工计算列宽、设置区域样式并处理纵向合并。 */
    private static void writeAsposeTable(DocumentBuilder builder, MigrationInput input) throws Exception {
        double availableWidth = builder.getCurrentSection().getPageSetup().getPageWidth()
                - builder.getCurrentSection().getPageSetup().getLeftMargin()
                - builder.getCurrentSection().getPageSetup().getRightMargin();
        double[] widths = {availableWidth * 2D / 9D, availableWidth * 5D / 9D, availableWidth * 2D / 9D};

        builder.startTable();
        writeAsposeTableRow(builder, new String[]{"Stage", "Implementation path", "Status"}, widths, true,
                new int[]{CellMerge.NONE, CellMerge.NONE, CellMerge.NONE});
        writeAsposeTableRow(builder, new String[]{"Business composition", "ReportDefinition + ReportModule", "Migrated"},
                widths, false, new int[]{CellMerge.FIRST, CellMerge.NONE, CellMerge.NONE});
        writeAsposeTableRow(builder, new String[]{"", "ReportDocument + DocxReportCompiler", "Exportable"},
                widths, false, new int[]{CellMerge.PREVIOUS, CellMerge.NONE, CellMerge.NONE});
        Table table = builder.endTable();
        table.setAllowAutoFit(false);
        table.setPreferredWidth(PreferredWidth.fromPoints(availableWidth));
        table.setAlignment(TableAlignment.CENTER);
        table.getFirstRow().getRowFormat().setHeadingFormat(true);
        table.getRows().forEach(row -> row.getRowFormat().setAllowBreakAcrossPages(false));
        builder.writeln();
    }

    /** 写入一行 Aspose 表格单元格。 */
    private static void writeAsposeTableRow(DocumentBuilder builder, String[] values, double[] widths,
                                            boolean header, int[] verticalMerges) throws Exception {
        for (int column = 0; column < values.length; column++) {
            builder.insertCell();
            builder.getCellFormat().clearFormatting();
            builder.getCellFormat().getBorders().setLineStyle(LineStyle.SINGLE);
            builder.getCellFormat().getBorders().setLineWidth(0.5D);
            builder.getCellFormat().setWidth(widths[column]);
            builder.getCellFormat().setPreferredWidth(PreferredWidth.fromPoints(widths[column]));
            builder.getCellFormat().setVerticalAlignment(CellVerticalAlignment.CENTER);
            builder.getCellFormat().setVerticalMerge(verticalMerges[column]);
            builder.getParagraphFormat().setAlignment(ParagraphAlignment.CENTER);
            applyAsposeFont(builder, header ? HEADING_FAR_EAST_FONT : BODY_FAR_EAST_FONT, 10.5D, false);
            builder.write(values[column]);
        }
        builder.endRow();
    }

    /** 使用 Aspose 手工添加原生可编辑图表。 */
    private static void writeAsposeChart(DocumentBuilder builder, MigrationInput input) throws Exception {
        builder.getParagraphFormat().clearFormatting();
        builder.getParagraphFormat().setAlignment(ParagraphAlignment.CENTER);
        Shape shape = builder.insertChart(com.aspose.words.ChartType.COLUMN, 460D, 280D);
        Chart chart = shape.getChart();
        chart.getSeries().clear();
        addAsposeChartSeries(chart, "2025", input.metric2025, input.metrics);
        addAsposeChartSeries(chart, "2026", input.metric2026, input.metrics);
        chart.getTitle().setShow(true);
        chart.getTitle().setText("Annual business metric comparison");
        chart.getTitle().setOverlay(false);
        applyAsposeChartFont(chart.getTitle().getFont(), HEADING_FAR_EAST_FONT);
        chart.getLegend().setPosition(LegendPosition.BOTTOM);
        chart.getLegend().setOverlay(false);
        applyAsposeChartFont(chart.getLegend().getFont(), BODY_FAR_EAST_FONT);
        chart.getAxisX().getTitle().setShow(true);
        chart.getAxisX().getTitle().setText("Metric");
        chart.getAxisY().getTitle().setShow(true);
        chart.getAxisY().getTitle().setText("Score");
        applyAsposeChartFont(chart.getAxisX().getTitle().getFont(), BODY_FAR_EAST_FONT);
        applyAsposeChartFont(chart.getAxisY().getTitle().getFont(), BODY_FAR_EAST_FONT);
        builder.writeln();
        writeAsposeCaption(builder, "Figure 1 Annual business metric comparison");
    }

    /** 添加一个 Aspose 图表系列并显示数值标签。 */
    private static void addAsposeChartSeries(Chart chart, String name, Double[] sourceValues,
                                             String[] categories) throws Exception {
        double[] values = new double[sourceValues.length];
        for (int index = 0; index < sourceValues.length; index++) values[index] = sourceValues[index];
        ChartSeries series = chart.getSeries().add(name, categories, values);
        series.hasDataLabels(true);
        series.getDataLabels().setShowValue(true);
        applyAsposeChartFont(series.getDataLabels().getFont(), BODY_FAR_EAST_FONT);
    }

    /** 开始一个 Aspose 正文段落。 */
    private static void beginAsposeBodyParagraph(DocumentBuilder builder) throws Exception {
        applyAsposeParagraphStyle(builder, StyleIdentifier.BODY_TEXT, BODY_FAR_EAST_FONT, 10.5D, false,
                ParagraphAlignment.JUSTIFY);
    }

    /** 应用 Aspose 段落及基础字体状态。 */
    private static void applyAsposeParagraphStyle(DocumentBuilder builder, int styleIdentifier,
                                                  String farEastFont, double size, boolean bold,
                                                  int alignment) throws Exception {
        builder.getParagraphFormat().clearFormatting();
        builder.getFont().clearFormatting();
        builder.getParagraphFormat().setStyleIdentifier(styleIdentifier);
        builder.getParagraphFormat().setAlignment(alignment);
        applyAsposeFont(builder, farEastFont, size, bold);
    }

    /** 同时设置 Aspose 的 ASCII 和东亚字体槽位。 */
    private static void applyAsposeFont(DocumentBuilder builder, String farEastFont, double size,
                                        boolean bold) throws Exception {
        builder.getFont().setNameAscii(ASCII_FONT);
        builder.getFont().setNameFarEast(farEastFont);
        builder.getFont().setSize(size);
        builder.getFont().setBold(bold);
        builder.getFont().setItalic(false);
        builder.getFont().setUnderline(Underline.NONE);
        builder.getFont().setColor(Color.BLACK);
    }

    /** 应用 Aspose 行内文本样式。 */
    private static void applyAsposeRun(DocumentBuilder builder, String color, boolean bold,
                                       boolean italic, boolean underline) throws Exception {
        applyAsposeFont(builder, BODY_FAR_EAST_FONT, 10.5D, bold);
        builder.getFont().setItalic(italic);
        builder.getFont().setUnderline(underline ? Underline.SINGLE : Underline.NONE);
        builder.getFont().setColor(Color.decode(color));
    }

    /** 为 Aspose 图表文字设置中西文字体。 */
    private static void applyAsposeChartFont(com.aspose.words.Font font, String farEastFont) {
        font.setNameAscii(ASCII_FONT);
        font.setNameFarEast(farEastFont);
        font.setSize(10.5D);
    }

    /** 框架报告蓝图定义。 */
    private static final class MigrationDefinition extends AbstractReportDefinition<MigrationInput> {
        private MigrationDefinition() {
            super("aspose-migration-comparison", "Aspose.Words 迁移对照", "1.0");
        }

        @Override
        protected void configure(ReportBlueprint.Builder builder, MigrationInput input) {
            builder.title(input.title)
                    .metadata(input.preparedBy, "从 Aspose.Words 迁移到 omni-office")
                    .layout(ReportLayout.builder()
                            .styleProfile(ReportStyleProfile.DEFAULT)
                            .headingNumberingEnabled(false)
                            .bodyTitle(true)
                            .header("omni-office · Aspose migration comparison")
                            .footer("Page PAGE")
                            .pageSetup(DocxPaperSize.A4, DocxPageOrientation.PORTRAIT,
                                    72D, 54D, 72D, 54D)
                            .build())
                    .module(ModuleSlot.builder(MigrationModule.CODE).build());
        }

        @Override
        public void contributeData(ReportDataContext context, MigrationInput input) {
            context.put(MigrationModule.DATA_KEY, input);
        }
    }

    /** 使用业务语义 Builder 组合与 Aspose 示例一致的内容。 */
    private static final class MigrationModule extends AbstractReportModule<MigrationInput> {
        private static final String CODE = "migration-comparison";
        private static final ReportDataKey<MigrationInput> DATA_KEY =
                ReportDataKey.of(CODE, MigrationInput.class);
        private static final ModuleDescriptor<MigrationInput> DESCRIPTOR =
                ModuleDescriptor.of(CODE, "Migration Comparison", DATA_KEY);

        @Override
        public ModuleDescriptor<MigrationInput> descriptor() {
            return DESCRIPTOR;
        }

        @Override
        protected void composeContent(ReportSectionBuilder section, MigrationInput data,
                                      ReportModuleContext context) {
            section.richParagraph()
                    .style("BodyText")
                    .text("Target: ")
                    .text(data.target, style -> {
                        style.setFontColor("#1F4E79");
                        style.setBold(true);
                    })
                    .text("; status: ")
                    .text(data.status, style -> {
                        style.setFontColor("#548235");
                        style.setBold(true);
                    })
                    .text("; migration principle: ")
                    .text("business modules no longer manipulate the Word cursor", style -> {
                        style.setFontColor("#C00000");
                        style.setUnderline(true);
                    })
                    .text(".")
                    .end()
                    .bullet("Business data is passed through strongly typed objects.")
                    .bullet("The report blueprint owns page layout, styles, and output format.")
                    .bullet("Semantic builders compose paragraphs, tables, and charts.")
                    .table("Stage", "Implementation path", "Status")
                    .style("TableHeader")
                    .widths(2D, 5D, 2D)
                    .row("Business composition", "ReportDefinition + ReportModule", "Migrated")
                    .row("", "ReportDocument + DocxReportCompiler", "Exportable")
                    .merge(1, 0, 2, 1)
                    .caption("Table 1 Component migration status", false, CaptionPosition.ABOVE)
                    .end()
                    .chart(ReportChartType.COLUMN)
                    .title("Annual business metric comparison")
                    .categories(data.metrics)
                    .series("2025", data.metric2025)
                    .series("2026", data.metric2026)
                    .axisTitles("Metric", "Score")
                    .legend(true, ReportChartLegendPosition.BOTTOM)
                    .showValues(true)
                    .end()
                    .paragraph("Caption", "Figure 1 Annual business metric comparison");
        }
    }

    /** 两种生成方式共享的业务输入，避免对照时混入数据差异。 */
    public static final class MigrationInput {
        private final String title;
        private final String target;
        private final String preparedBy;
        private final String status;
        private final String[] metrics;
        private final Double[] metric2025;
        private final Double[] metric2026;

        private MigrationInput(String title, String target, String preparedBy, String status,
                               String[] metrics, Double[] metric2025, Double[] metric2026) {
            this.title = title;
            this.target = target;
            this.preparedBy = preparedBy;
            this.status = status;
            this.metrics = metrics;
            this.metric2025 = metric2025;
            this.metric2026 = metric2026;
        }

        /**
         * 创建固定对照数据。
         *
         * @return 可重复生成的示例输入
         */
        public static MigrationInput sample() {
            return new MigrationInput(
                    "Aspose.Words to omni-office Migration Comparison",
                    "Existing Aspose.Words reports",
                    "Document Platform Team",
                    "Ready for incremental migration",
                    new String[]{"Stability", "Maintainability", "Extensibility"},
                    new Double[]{82D, 68D, 72D},
                    new Double[]{91D, 92D, 95D});
        }
    }
}
