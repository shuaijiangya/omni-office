package cn.bugstack.export.docx;

import cn.bugstack.export.api.ReportOutputFormat;
import cn.bugstack.export.core.ReportDocumentRenderer;
import cn.bugstack.export.definition.ReportBlueprint;
import cn.bugstack.export.definition.ReportLayout;
import cn.bugstack.export.document.CaptionTargetType;
import cn.bugstack.export.document.CaptionPosition;
import cn.bugstack.export.document.ReportCaption;
import cn.bugstack.export.document.ReportChart;
import cn.bugstack.export.document.ReportChartSeries;
import cn.bugstack.export.document.ReportClassDesignTable;
import cn.bugstack.export.document.ReportDocument;
import cn.bugstack.export.document.ReportDiagram;
import cn.bugstack.export.document.ReportDiagramEmbedMode;
import cn.bugstack.export.document.ReportElement;
import cn.bugstack.export.document.ReportImage;
import cn.bugstack.export.document.ReportListItem;
import cn.bugstack.export.document.ReportListType;
import cn.bugstack.export.document.ReportPageBreak;
import cn.bugstack.export.document.ReportParagraph;
import cn.bugstack.export.document.ReportSection;
import cn.bugstack.export.document.ReportTable;
import cn.bugstack.export.document.ReportTableMerge;
import cn.bugstack.export.document.ReportTextRange;
import cn.bugstack.export.document.ReportTextRangeStyle;
import cn.bugstack.office.docx.api.DocxDocument;
import cn.bugstack.office.docx.builder.SectionBuilder;
import cn.bugstack.office.docx.model.ChartLegendPosition;
import cn.bugstack.office.docx.model.ChartType;
import cn.bugstack.office.docx.model.TableHorizontalAlignment;
import cn.bugstack.office.docx.model.TableVerticalMerge;
import cn.bugstack.office.docx.style.RunStyle;
import cn.bugstack.office.docx.render.AsposeWordsLicenseLoader;
import com.aspose.words.Document;
import com.aspose.words.SaveFormat;
import com.aspose.words.HtmlSaveOptions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 报告语义文档到现有 {@link DocxDocument} 的编译适配器。
 *
 * <p>该类是 export 与 office.docx 的唯一直接耦合点。报告模块不感知 Aspose，
 * 因而同一语义文档可以复用为 DOCX、PDF 和 HTML 输出。</p>
 */
public final class DocxReportCompiler implements ReportDocumentRenderer {

    /** 默认报告样式画像到 DOCX 文档的创建策略。 */
    private static final DocxDocumentFactory DEFAULT_DOCUMENT_FACTORY = DocxReportCompiler::createDefaultDocument;
    /** 用于创建本次报告 DOCX 文档的可配置策略。 */
    private final DocxDocumentFactory documentFactory;
    /** 自定义报告语义元素编译器。 */
    private final List<ReportElementCompiler<?>> customElementCompilers = new ArrayList<>();

    /**
     * 创建仅包含内置语义块支持的 docx 编译器。
     */
    public DocxReportCompiler() {
        this(DEFAULT_DOCUMENT_FACTORY, null);
    }

    /**
     * 创建包含自定义语义块编译器的 docx 编译器。
     *
     * @param customElementCompilers 自定义元素编译器
     */
    public DocxReportCompiler(Iterable<? extends ReportElementCompiler<?>> customElementCompilers) {
        this(DEFAULT_DOCUMENT_FACTORY, customElementCompilers);
    }

    /**
     * 创建使用指定 DOCX 文档工厂的报告编译器。
     *
     * @param documentFactory DOCX 文档创建策略
     */
    public DocxReportCompiler(DocxDocumentFactory documentFactory) {
        this(documentFactory, null);
    }

    /**
     * 创建使用指定 DOCX 文档工厂和自定义元素编译器的报告编译器。
     *
     * @param documentFactory DOCX 文档创建策略
     * @param customElementCompilers 自定义元素编译器
     */
    public DocxReportCompiler(DocxDocumentFactory documentFactory,
                              Iterable<? extends ReportElementCompiler<?>> customElementCompilers) {
        if (documentFactory == null) {
            throw new IllegalArgumentException("docx document factory must not be null");
        }
        this.documentFactory = documentFactory;
        if (customElementCompilers != null) {
            for (ReportElementCompiler<?> compiler : customElementCompilers) {
                register(compiler);
            }
        }
    }

    /**
     * 注册自定义语义块编译器。
     *
     * @param compiler 元素编译器
     * @return 当前编译器
     */
    public DocxReportCompiler register(ReportElementCompiler<?> compiler) {
        if (compiler == null || compiler.supportedType() == null) {
            throw new IllegalArgumentException("report element compiler and supported type must not be null");
        }
        for (ReportElementCompiler<?> existing : customElementCompilers) {
            if (existing.supportedType().equals(compiler.supportedType())) {
                throw new IllegalStateException("duplicate report element compiler: " + compiler.supportedType().getName());
            }
        }
        customElementCompilers.add(compiler);
        return this;
    }

    /**
     * 将报告语义树渲染为 DOCX、PDF 或 HTML 文件。
     *
     * <p>渲染过程先写入同目录临时文件，只有目标格式生成成功后才替换最终输出文件，
     * 避免失败导出破坏已有报告。</p>
     *
     * @param document 报告语义文档
     * @param blueprint 报告蓝图
     * @param format 输出格式
     * @param outputPath 最终输出文件路径
     */
    @Override
    public void render(ReportDocument document, ReportBlueprint blueprint, ReportOutputFormat format, Path outputPath) {
        if (document == null || blueprint == null || format == null || outputPath == null) {
            throw new IllegalArgumentException("report, blueprint, format and output path must not be null");
        }
        Path temporaryOutput = null;
        try {
            validateOutputPath(format, outputPath);
            Path parent = outputPath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            temporaryOutput = Files.createTempFile(parent, "report-export-", suffixFor(format));
            if (format == ReportOutputFormat.DOCX) compile(document, blueprint).save(temporaryOutput);
            else Files.write(temporaryOutput, renderToBytes(document, blueprint, format));
            moveCompletedFile(temporaryOutput, outputPath);
        } catch (Exception e) {
            throw new IllegalStateException("failed to render report to " + format + ": " + outputPath, e);
        } finally {
            if (temporaryOutput != null) {
                try {
                    Files.deleteIfExists(temporaryOutput);
                } catch (IOException ignored) {
                    // The final output was already moved, or cleanup will be handled by the operating system.
                }
            }
        }
    }

    /**
     * 将报告语义树渲染为内存字节。
     *
     * <p>DOCX 直接由封装层写入内存；PDF 与 HTML 基于同一份 DOCX 字节完成转换，保证
     * 三种输出使用一致的样式、页眉页脚和报告内容。</p>
     *
     * @param document 报告语义文档
     * @param blueprint 报告蓝图
     * @param format 输出格式
     * @return 完整输出文件字节
     */
    @Override
    public byte[] renderToBytes(ReportDocument document, ReportBlueprint blueprint, ReportOutputFormat format) {
        if (document == null || blueprint == null || format == null) {
            throw new IllegalArgumentException("report, blueprint and format must not be null");
        }
        try {
            byte[] docxBytes = compile(document, blueprint).toByteArray();
            if (format == ReportOutputFormat.DOCX) return docxBytes;
            if (format == ReportOutputFormat.PDF) return convertToPdf(docxBytes);
            if (format == ReportOutputFormat.HTML) return convertToHtml(docxBytes);
            throw new IllegalArgumentException("unsupported report output format: " + format);
        } catch (Exception e) {
            throw new IllegalStateException("failed to render report to " + format + " bytes", e);
        }
    }

    /**
     * 将报告语义文档编译为 docx 门面对象。
     *
     * @param report 报告语义文档
     * @param blueprint 报告蓝图
     * @return 已完成节点组装的 docx 文档
     */
    public DocxDocument compile(ReportDocument report, ReportBlueprint blueprint) {
        DocxDocument document = createDocument(blueprint);
        // 应用样式以及设置封面
        applyLayout(document, blueprint, report);
        SectionBuilder section = document.section();
        boolean tableOfContentsEnabled = blueprint.getLayout().getTableOfContentsDepth() != null;
        if (!tableOfContentsEnabled && blueprint.getLayout().isBodyTitleEnabled()) {
            section.title(report.getTitle());
        }
        if (!tableOfContentsEnabled) {
            writeBasicInfo(section, report);
        }
        for (ReportSection reportSection : report.getSections()) {
            writeSection(section, reportSection, 1, blueprint);
        }
        return section.end();
    }

    /**
     * 根据报告样式画像创建对应的 DOCX 文档门面。
     *
     * @param blueprint 报告蓝图
     * @return 已应用样式画像的文档对象
     */
    private DocxDocument createDocument(ReportBlueprint blueprint) {
        DocxDocument document = documentFactory.create(blueprint);
        if (document == null) {
            throw new IllegalStateException("docx document factory returned null document");
        }
        return document;
    }

    /**
     * 使用报告配置的内置或业务自定义样式画像创建默认 DOCX 文档。
     *
     * @param blueprint 报告蓝图
     * @return 已应用默认样式画像的 DOCX 文档
     */
    private static DocxDocument createDefaultDocument(ReportBlueprint blueprint) {
        return DocxDocument.create().useStyleProfile(blueprint.getLayout().getStyleProfile());
    }

    /**
     * 将报告布局、元数据、封面和页眉页脚应用到目标文档。
     *
     * @param document 目标 DOCX 文档
     * @param blueprint 报告蓝图
     * @param report 报告语义文档
     */
    private void applyLayout(DocxDocument document, ReportBlueprint blueprint, ReportDocument report) {
        ReportLayout layout = blueprint.getLayout();
        document.metadata(report.getTitle(), blueprint.getAuthor(), blueprint.getSubject());
        document.pageSetup(setup -> setup.paper(layout.getPaperSize())
                .margins(layout.getTopMarginPoints(), layout.getRightMarginPoints(),
                        layout.getBottomMarginPoints(), layout.getLeftMarginPoints()));
        if (layout.getOrientation() == cn.bugstack.office.docx.model.DocxPageOrientation.LANDSCAPE) {
            document.pageSetup(cn.bugstack.office.docx.builder.PageSetupBuilder::landscape);
        }
        if (layout.isHeadingNumberingEnabled()) {
            document.enableHeadingNumbering();
        }
        if (layout.getTableOfContentsDepth() != null) {
            document.tableOfContents("目  录", layout.getTableOfContentsDepth());
            document.tableOfContentsFooter(layout.getTableOfContentsFooterText());
        }
        if (hasText(layout.getHeaderText())) {
            document.header(layout.getHeaderText());
        }
        if (hasText(layout.getFooterText())) {
            document.footer(layout.getFooterText());
        } else if (layout.isPageNumberFooterEnabled()) {
            document.footer(ReportLayout.DEFAULT_PAGE_NUMBER_FOOTER);
        }
        document.modulePageNumberStart(layout.getModulePageNumberStart());
        if (hasText(layout.getCoverDocumentName())) {
            document.cover(layout.getCoverDocumentName(), layout.getCoverProjectName(), layout.getCoverVersion());
        } else if (layout.getCoverTemplate() != null) {
            writeTemplateCover(document, layout.getCoverTemplate(), blueprint);
        }
        if (!layout.getRevisions().isEmpty()) {
            document.revisionHistory(history -> layout.getRevisions().forEach(item -> history.revision(
                    item.getVersion(), item.getDate(), item.getDescription(), item.getAuthor())));
        }
        if (!layout.getApprovals().isEmpty()) {
            document.approvalPage(approval -> layout.getApprovals().forEach(item -> approval.approval(
                    item.getRole(), item.getPerson(), item.getDate())));
        }
    }

    /** 将动态封面模板编译到目录之前的独立封面 Section。 */
    private void writeTemplateCover(DocxDocument document, cn.bugstack.export.definition.ReportCoverTemplate template,
                                    ReportBlueprint blueprint) {
        List<ReportElement> elements = template.createElements();
        if (elements == null || elements.isEmpty()) {
            throw new IllegalArgumentException("report cover template must provide at least one element");
        }
        SectionBuilder cover = document.templateCover();
        for (ReportElement element : elements) {
            if (element == null) {
                throw new IllegalArgumentException("report cover template element must not be null");
            }
            writeElement(cover, element, 0, blueprint);
        }
        cover.end();
    }

    /**
     * 将无目录报告的基础信息写入两列表格。
     *
     * <p>启用目录的正式报告不会调用此方法，保证框架不会在目录与模块正文之间插入前导内容。
     * 需要在正式报告中展示的基础信息应由封面模板或显式业务模块承载。</p>
     *
     * @param section 目标章节
     * @param report 报告语义文档
     */
    private void writeBasicInfo(SectionBuilder section, ReportDocument report) {
        if (report.getBasicInfo() == null) {
            return;
        }
        List<String[]> rows = new ArrayList<>();
        addInfo(rows, "报告编号", report.getBasicInfo().getReportNo());
        addInfo(rows, "评估对象", report.getBasicInfo().getAssessmentTarget());
        addInfo(rows, "编制人", report.getBasicInfo().getPreparedBy());
        if (report.getBasicInfo().getGeneratedTime() != null) {
            addInfo(rows, "生成时间", report.getBasicInfo().getGeneratedTime().toString());
        }
        report.getBasicInfo().getProperties().forEach((key, value) -> addInfo(rows, key, value));
        if (rows.isEmpty()) {
            return;
        }
        cn.bugstack.office.docx.builder.TableBuilder<SectionBuilder> table = section.table().style("TableHeader")
                .widths(110, 390).headers("项目", "内容");
        for (String[] row : rows) {
            table.row(row);
        }
        table.end();
    }

    /**
     * 在值有效时向基础信息行集合追加键值对。
     *
     * @param rows 信息行集合
     * @param key 信息项名称
     * @param value 信息项值
     */
    private void addInfo(List<String[]> rows, String key, String value) {
        if (hasText(value)) {
            rows.add(new String[]{key, value});
        }
    }

    /**
     * 递归编译报告章节及其子元素。
     *
     * @param target DOCX 章节构建器
     * @param section 报告章节
     * @param level 当前标题层级
     * @param blueprint 报告蓝图
     */
    private void writeSection(SectionBuilder target, ReportSection section, int level, ReportBlueprint blueprint) {
        if (level > 9) {
            throw new IllegalArgumentException("report section depth must not exceed 9");
        }
        target.heading(level, section.getTitle());
        for (ReportElement element : section.getElements()) {
            writeElement(target, element, level, blueprint);
        }
    }

    /**
     * 按语义元素类型将内容编译为 DOCX 节点。
     *
     * @param target DOCX 章节构建器
     * @param element 待编译元素
     * @param parentLevel 父章节层级
     * @param blueprint 报告蓝图
     */
    private void writeElement(SectionBuilder target, ReportElement element, int parentLevel, ReportBlueprint blueprint) {
        if (element instanceof ReportSection) {
            writeSection(target, (ReportSection) element, parentLevel + 1, blueprint);
        } else if (element instanceof ReportParagraph) {
            writeParagraph(target, (ReportParagraph) element);
        } else if (element instanceof ReportListItem) {
            writeListItem(target, (ReportListItem) element);
        } else if (element instanceof ReportTable) {
            writeTable(target, (ReportTable) element);
        } else if (element instanceof ReportImage) {
            writeImage(target, (ReportImage) element);
        } else if (element instanceof ReportDiagram) {
            writeDiagram(target, (ReportDiagram) element);
        } else if (element instanceof ReportChart) {
            writeChart(target, (ReportChart) element);
        } else if (element instanceof ReportPageBreak) {
            target.pageBreak();
        } else if (element instanceof ReportClassDesignTable) {
            writeClassDesignTable(target, (ReportClassDesignTable) element, parentLevel);
        } else {
            writeCustomElement(target, element, parentLevel, blueprint);
        }
    }

    /**
     * 编译普通报告段落。
     *
     * @param target DOCX 章节构建器
     * @param paragraph 报告段落
     */
    private void writeParagraph(SectionBuilder target, ReportParagraph paragraph) {
        cn.bugstack.office.docx.builder.ParagraphBuilder<SectionBuilder> builder = target.paragraph();
        if (hasText(paragraph.getStyleName())) {
            builder.style(paragraph.getStyleName());
        }
        if (paragraph.getTextRanges().isEmpty()) {
            builder.text(paragraph.getText(), paragraph.getFontColor());
        } else {
            for (ReportTextRange range : paragraph.getTextRanges()) {
                builder.text(range.getText(), toRunStyle(range.getStyle(), paragraph.getFontColor()));
            }
        }
        builder.end();
    }

    /** 将 Report 文本范围样式转换为底层 run 样式。 */
    private RunStyle toRunStyle(ReportTextRangeStyle source, String inheritedParagraphColor) {
        if (source == null && !hasText(inheritedParagraphColor)) return null;
        RunStyle target = new RunStyle();
        if (hasText(inheritedParagraphColor)) target.setColor(inheritedParagraphColor);
        if (source == null) return target;
        target.setFontFamily(source.getFontFamily());
        target.setAsciiFontFamily(source.getAsciiFontFamily());
        target.setFarEastFontFamily(source.getFarEastFontFamily());
        target.setFontSize(source.getFontSize());
        if (hasText(source.getFontColor())) target.setColor(source.getFontColor());
        if (source.getBold() != null) target.setBold(source.getBold());
        if (source.getItalic() != null) target.setItalic(source.getItalic());
        if (source.getUnderline() != null) target.setUnderline(source.getUnderline());
        return target;
    }

    /**
     * 编译项目符号或编号列表项。
     *
     * @param target DOCX 章节构建器
     * @param item 报告列表项
     */
    private void writeListItem(SectionBuilder target, ReportListItem item) {
        cn.bugstack.office.docx.builder.ParagraphBuilder<SectionBuilder> builder = target.paragraph();
        if (item.getListType() == ReportListType.BULLET) {
            builder.bullet();
        } else {
            builder.numbered();
        }
        if (hasText(item.getStyleName())) {
            builder.style(item.getStyleName());
        }
        builder.text(item.getText(), item.getFontColor()).end();
    }

    /**
     * 编译报告表格及其题注。
     *
     * @param target DOCX 章节构建器
     * @param reportTable 报告表格
     */
    private void writeTable(SectionBuilder target, ReportTable reportTable) {
        writeCaption(target, reportTable.getCaption(), CaptionPosition.ABOVE);
        cn.bugstack.office.docx.builder.TableBuilder<SectionBuilder> table = target.table()
                .style(hasText(reportTable.getStyleName()) ? reportTable.getStyleName() : "TableHeader")
                .alignment(TableHorizontalAlignment.valueOf(reportTable.getAlignment().name()));
        if (reportTable.getHeaderTextStyle() != null) {
            table.headerTextStyle(toRunStyle(reportTable.getHeaderTextStyle(), null));
        }
        if (reportTable.getBodyTextStyle() != null) {
            table.bodyTextStyle(toRunStyle(reportTable.getBodyTextStyle(), null));
        }
        if (reportTable.getColumnWidths().length > 0) {
            table.widths(reportTable.getColumnWidths());
        }
        List<List<String>> grid = new ArrayList<>();
        grid.add(reportTable.getHeaders());
        grid.addAll(reportTable.getRows());
        for (int rowIndex = 0; rowIndex < grid.size(); rowIndex++) {
            writeTableRow(table, grid.get(rowIndex), rowIndex, reportTable);
        }
        table.end();
        writeCaption(target, reportTable.getCaption(), CaptionPosition.BELOW);
    }

    /** 将逻辑表格行转换为带横向和纵向合并标记的 DOCX 单元格。 */
    private void writeTableRow(cn.bugstack.office.docx.builder.TableBuilder<SectionBuilder> table,
                               List<String> values, int rowIndex, ReportTable reportTable) {
        table.row(row -> {
            int columnIndex = 0;
            while (columnIndex < values.size()) {
                ReportTableMerge merge = findMerge(reportTable.getMerges(), rowIndex, columnIndex);
                if (merge != null && columnIndex != merge.getStartColumn()) {
                    columnIndex++;
                    continue;
                }
                final int currentColumn = columnIndex;
                final int columnSpan = merge == null ? 1 : merge.getColumnSpan();
                final TableVerticalMerge verticalMerge = toVerticalMerge(merge, rowIndex);
                final String text = merge != null && rowIndex > merge.getStartRow()
                        ? "" : values.get(currentColumn);
                row.cell(columnSpan, cell -> {
                    cell.verticalMerge(verticalMerge);
                    cell.paragraph().text(text, reportTable.getFontColor()).end();
                });
                columnIndex += columnSpan;
            }
        });
    }

    /** 查找覆盖指定逻辑单元格的合并区域。 */
    private ReportTableMerge findMerge(List<ReportTableMerge> merges, int row, int column) {
        for (ReportTableMerge merge : merges) {
            if (merge.contains(row, column)) {
                return merge;
            }
        }
        return null;
    }

    /** 计算当前合并行需要写入的纵向合并标记。 */
    private TableVerticalMerge toVerticalMerge(ReportTableMerge merge, int rowIndex) {
        if (merge == null || merge.getRowSpan() == 1) {
            return TableVerticalMerge.NONE;
        }
        return rowIndex == merge.getStartRow() ? TableVerticalMerge.FIRST : TableVerticalMerge.PREVIOUS;
    }

    /**
     * 编译报告图片及其题注。
     *
     * @param target DOCX 章节构建器
     * @param image 报告图片
     */
    private void writeImage(SectionBuilder target, ReportImage image) {
        writeCaption(target, image.getCaption(), CaptionPosition.ABOVE);
        if (image.getWidth() == null) {
            target.paragraph().image(image.getSource()).end();
        } else {
            target.paragraph().image(image.getSource(), image.getWidth(), image.getHeight()).end();
        }
        writeCaption(target, image.getCaption(), CaptionPosition.BELOW);
    }

    /** 将图形写为普通预览图或内嵌可编辑 VSDX 的 OLE 对象。 */
    private void writeDiagram(SectionBuilder target, ReportDiagram diagram) {
        writeCaption(target, diagram.getCaption(), CaptionPosition.ABOVE);
        if (diagram.getEmbedMode() == ReportDiagramEmbedMode.PREVIEW_IMAGE) {
            target.paragraph().image(diagram.getPreviewSource(), diagram.getMaxWidthPoints(),
                    diagram.getMaxHeightPoints()).end();
        } else {
            target.paragraph().editableVisio(diagram.getVsdxSource(), diagram.getPreviewSource(),
                    diagram.getMaxWidthPoints(), diagram.getMaxHeightPoints()).end();
        }
        writeCaption(target, diagram.getCaption(), CaptionPosition.BELOW);
    }

    /** 将报告图表编译为 DOCX 原生可编辑图表。 */
    private void writeChart(SectionBuilder target, ReportChart chart) {
        writeCaption(target, chart.getCaption(), CaptionPosition.ABOVE);
        cn.bugstack.office.docx.builder.ParagraphBuilder<SectionBuilder> paragraph = target.paragraph();
        cn.bugstack.office.docx.builder.ChartBuilder<
                cn.bugstack.office.docx.builder.ParagraphBuilder<SectionBuilder>> builder = paragraph
                .chart(ChartType.valueOf(chart.getChartType().name()))
                .title(chart.getTitle())
                .categories(chart.getCategories().toArray(new String[0]))
                .size(chart.getWidthPoints(), chart.getHeightPoints())
                .legend(chart.isLegendVisible(), ChartLegendPosition.valueOf(chart.getLegendPosition().name()))
                .showValues(chart.isShowValues())
                .showPercentages(chart.isShowPercentages())
                .axisTitles(chart.getCategoryAxisTitle(), chart.getValueAxisTitle());
        for (ReportChartSeries series : chart.getSeries()) {
            builder.series(series.getName(), series.getValues().toArray(new Double[0]));
        }
        builder.end().end();
        writeCaption(target, chart.getCaption(), CaptionPosition.BELOW);
    }

    /**
     * 编译类设计表格，并根据父章节确定标题层级。
     *
     * @param target DOCX 章节构建器
     * @param element 类设计表格元素
     * @param parentLevel 父章节层级
     */
    private void writeClassDesignTable(SectionBuilder target, ReportClassDesignTable element, int parentLevel) {
        target.classDesignTable(parentLevel + 1, element.getTitle(), config -> config
                .sourceRoot(element.getSourceRoot())
                .className(element.getClassName())
                .includeFields(element.isIncludeFields())
                .includeMethods(element.isIncludeMethods())
                .includePrivate(element.isIncludePrivate())
                .includeGetterSetter(element.isIncludeGetterSetter()));
    }

    /**
     * 写入题注或非自动编号的说明文字。
     *
     * @param target DOCX 章节构建器
     * @param caption 报告题注
     */
    private void writeCaption(SectionBuilder target, ReportCaption caption, CaptionPosition position) {
        if (caption == null || caption.getPosition() != position || !hasText(caption.getText())) {
            return;
        }
        if (!caption.isAutoNumbered()) {
            target.paragraph().style("Caption").text(caption.getText()).end();
            return;
        }
        if (caption.getTargetType() == CaptionTargetType.TABLE) {
            target.tableCaption(caption.getText());
        } else {
            target.figureCaption(caption.getText());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    /**
     * 使用已注册的扩展编译器编译自定义语义元素。
     *
     * @param target DOCX 章节构建器
     * @param element 自定义语义元素
     * @param level 当前章节层级
     * @param blueprint 报告蓝图
     */
    private void writeCustomElement(SectionBuilder target, ReportElement element, int level, ReportBlueprint blueprint) {
        for (ReportElementCompiler compiler : customElementCompilers) {
            if (compiler.supportedType().isInstance(element)) {
                compiler.compile(element, new DocxReportCompileContext(target, blueprint, level));
                return;
            }
        }
        throw new IllegalArgumentException("no docx compiler registered for report element: " + element.getClass().getName());
    }

    /**
     * 将报告渲染为 PDF 并写入目标路径。
     *
     * @param report 报告语义文档
     * @param blueprint 报告蓝图
     * @param outputPath PDF 输出路径
     * @throws IOException 写入文件失败时抛出
     */
    private void renderPdf(ReportDocument report, ReportBlueprint blueprint, Path outputPath) throws IOException {
        Files.write(outputPath, renderToBytes(report, blueprint, ReportOutputFormat.PDF));
    }

    /**
     * 将 DOCX 字节转换为 PDF 字节。
     *
     * @param docxBytes DOCX 文件字节
     * @return PDF 文件字节
     * @throws Exception Aspose 转换失败时抛出
     */
    private byte[] convertToPdf(byte[] docxBytes) throws Exception {
        AsposeWordsLicenseLoader.applyConfiguredLicense();
        try (ByteArrayInputStream input = new ByteArrayInputStream(docxBytes);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(input);
            document.save(output, SaveFormat.PDF);
            return output.toByteArray();
        }
    }

    private byte[] convertToHtml(byte[] docxBytes) throws Exception {
        AsposeWordsLicenseLoader.applyConfiguredLicense();
        try (ByteArrayInputStream input = new ByteArrayInputStream(docxBytes);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(input);
            HtmlSaveOptions options = new HtmlSaveOptions(SaveFormat.HTML);
            options.setExportImagesAsBase64(true);
            document.save(output, options);
            return output.toByteArray();
        }
    }

    /**
     * 校验输出文件扩展名与目标格式匹配。
     *
     * @param format 目标输出格式
     * @param outputPath 输出路径
     */
    private void validateOutputPath(ReportOutputFormat format, Path outputPath) {
        String fileName = outputPath.getFileName() == null ? "" : outputPath.getFileName().toString().toLowerCase();
        String expectedSuffix = suffixFor(format);
        if (!fileName.endsWith(expectedSuffix)) {
            throw new IllegalArgumentException("report output path must end with " + expectedSuffix);
        }
    }

    /**
     * 获取目标格式使用的临时文件后缀。
     *
     * @param format 目标输出格式
     * @return 文件后缀
     */
    private String suffixFor(ReportOutputFormat format) {
        switch (format) {
            case DOCX: return ".docx";
            case PDF: return ".pdf";
            case HTML: return ".html";
            default: throw new IllegalArgumentException("unsupported report output format: " + format);
        }
    }

    /**
     * 原子替换或移动完成的临时输出文件。
     *
     * @param source 已生成的临时文件
     * @param outputPath 最终输出路径
     * @throws IOException 文件移动失败时抛出
     */
    private void moveCompletedFile(Path source, Path outputPath) throws IOException {
        try {
            Files.move(source, outputPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(source, outputPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * 判断字符串是否包含非空白内容。
     *
     * @param value 待判断文本
     * @return 包含有效文本时返回 {@code true}
     */
    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
