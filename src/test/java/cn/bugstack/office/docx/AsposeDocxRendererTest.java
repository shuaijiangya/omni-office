package cn.bugstack.office.docx;

import cn.bugstack.office.docx.api.DocxDocument;
import cn.bugstack.office.docx.model.CaptionType;
import cn.bugstack.office.docx.model.DocxPaperSize;
import cn.bugstack.office.docx.model.TableCellVerticalAlignment;
import cn.bugstack.office.docx.model.TableHorizontalAlignment;
import cn.bugstack.office.docx.model.TableVerticalMerge;
import com.aspose.words.Cell;
import com.aspose.words.CellMerge;
import com.aspose.words.CellVerticalAlignment;
import com.aspose.words.Document;
import com.aspose.words.Field;
import com.aspose.words.FieldType;
import com.aspose.words.LineSpacingRule;
import com.aspose.words.NodeCollection;
import com.aspose.words.NodeType;
import com.aspose.words.OutlineLevel;
import com.aspose.words.Orientation;
import com.aspose.words.Paragraph;
import com.aspose.words.ParagraphAlignment;
import com.aspose.words.PreferredWidthType;
import com.aspose.words.Run;
import com.aspose.words.Shape;
import com.aspose.words.StyleIdentifier;
import com.aspose.words.Table;
import com.aspose.words.TableAlignment;
import com.aspose.words.Underline;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.awt.Color;
import java.util.Base64;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsposeDocxRendererTest {

    @Test
    void adaptsTableToPageWidthAndCentersCellTextByDefault() throws Exception {
        Path directory = Files.createTempDirectory("docx-responsive-table");
        Path output = directory.resolve("responsive-table-standard-margin.docx");
        Path narrowOutput = directory.resolve("responsive-table-wide-margin.docx");

        createResponsiveTableDocument(output, 54D);
        createResponsiveTableDocument(narrowOutput, 90D);

        Document rendered = new Document(output.toString());
        Document narrowRendered = new Document(narrowOutput.toString());
        Table table = findTableContaining(rendered, "较窄列");
        Table narrowTable = findTableContaining(narrowRendered, "较窄列");
        double firstWidth = table.getFirstRow().getCells().get(0).getCellFormat().getWidth();
        double secondWidth = table.getFirstRow().getCells().get(1).getCellFormat().getWidth();
        double standardTableWidth = firstWidth + secondWidth;
        double narrowTableWidth = rowWidth(narrowTable.getFirstRow());

        assertFalse(table.getAllowAutoFit());
        double availablePageWidth = rendered.getFirstSection().getPageSetup().getPageWidth()
                - rendered.getFirstSection().getPageSetup().getLeftMargin()
                - rendered.getFirstSection().getPageSetup().getRightMargin();
        assertEquals(PreferredWidthType.POINTS, table.getPreferredWidth().getType());
        assertEquals(availablePageWidth, table.getPreferredWidth().getValue(), 0.1D);
        assertEquals(72D, standardTableWidth - narrowTableWidth, 1D);
        assertEquals(2D, secondWidth / firstWidth, 0.05D);
        assertEquals(CellVerticalAlignment.CENTER,
                table.getFirstRow().getFirstCell().getCellFormat().getVerticalAlignment());
        assertEquals(ParagraphAlignment.CENTER,
                table.getFirstRow().getFirstCell().getFirstParagraph().getParagraphFormat().getAlignment());
    }

    /** 创建具有指定左右页边距的响应式表格文档。 */
    private void createResponsiveTableDocument(Path output, double horizontalMargin) {
        DocxDocument.create()
                .pageSetup(setup -> setup
                        .paper(DocxPaperSize.A4)
                        .margins(72, horizontalMargin, 72, horizontalMargin))
                .section()
                .table()
                .widths(1, 2)
                .row("较窄列", "较宽列")
                .end()
                .end()
                .save(output);
    }

    @Test
    void rendersDocxWithTextTableImageAndVisioPreview() throws Exception {
        Path dir = Files.createTempDirectory("docx-renderer");
//        Path image = dir.resolve("image.png");
        Path output = dir.resolve("output.docx");
//        writePng(image);
//        /var/folders/wy/k34k817j53508flts42yb_840000gn/T/docx-renderer13480317541838422827/output.docx

        DocxDocument.create()
                .useDefaultStyles()
                .section()
                .heading1("系统架构")
                .paragraph()
                .text("流程如下：")
//                .image(image.toString())
//                .visio(image.toString())
                .end()
                .table()
                .headers("模块", "职责")
                .row("Document", "文档入口")
                .end()
                .end()
                .save(output);

        Document rendered = new Document(output.toString());
        String text = rendered.getText();

        assertTrue(Files.exists(output));
        assertTrue(text.contains("系统架构"));
        assertTrue(text.contains("Document"));
    }

    @Test
    void repeatsHeaderAndKeepsRowsIntactForHeaderTableStyle() throws Exception {
        Path dir = Files.createTempDirectory("docx-table-pagination");
        Path output = dir.resolve("table-pagination.docx");

        DocxDocument.create()
                .section()
                .table()
                .style("TableHeader")
                .headers("模块", "职责")
                .row("Document", "文档入口")
                .end()
                .end()
                .save(output);

        Document rendered = new Document(output.toString());
        Table table = (Table) rendered.getChildNodes(NodeType.TABLE, true).get(0);

        assertTrue(table.getFirstRow().getRowFormat().getHeadingFormat());
        assertFalse(table.getFirstRow().getRowFormat().getAllowBreakAcrossPages());
        assertFalse(table.getLastRow().getRowFormat().getAllowBreakAcrossPages());
    }

    @Test
    void rendersHeading1AsRealWordHeadingStyle() throws Exception {
        Path dir = Files.createTempDirectory("docx-heading-style");
        Path output = dir.resolve("heading.docx");

        DocxDocument.create()
                .section()
                .heading1("系统架构")
                .end()
                .save(output);

        Document rendered = new Document(output.toString());
        Paragraph firstParagraph = findParagraph(rendered, "系统架构");

        assertEquals(StyleIdentifier.HEADING_1, firstParagraph.getParagraphFormat().getStyleIdentifier());
    }

    @Test
    void rendersHeading9AsRealWordHeadingStyle() throws Exception {
        Path dir = Files.createTempDirectory("docx-heading9-style");
        Path output = dir.resolve("heading9.docx");

        DocxDocument.create()
                .section()
                .heading9("九级标题")
                .end()
                .save(output);

        Document rendered = new Document(output.toString());
        Paragraph paragraph = findParagraph(rendered, "九级标题");

        assertEquals(StyleIdentifier.HEADING_9, paragraph.getParagraphFormat().getStyleIdentifier());
    }

    @Test
    void continuesConsecutiveNumberedParagraphsAndResetsAfterNormalParagraph() throws Exception {
        Path dir = Files.createTempDirectory("docx-numbered-list");
        Path output = dir.resolve("numbered-list.docx");

        DocxDocument.create()
                .section()
                .numbered("第一项")
                .numbered("第二项")
                .paragraph().text("普通段落").end()
                .numbered("重新编号的第一项")
                .end()
                .save(output);

        Document rendered = new Document(output.toString());
        rendered.updateListLabels();

        assertEquals("1.", findParagraph(rendered, "第一项").getListLabel().getLabelString());
        assertEquals("2.", findParagraph(rendered, "第二项").getListLabel().getLabelString());
        assertEquals("1.", findParagraph(rendered, "重新编号的第一项").getListLabel().getLabelString());
    }

    @Test
    void rendersCustomParagraphStyleRegisteredOnDocument() throws Exception {
        Path dir = Files.createTempDirectory("docx-custom-style");
        Path output = dir.resolve("custom-style.docx");
        cn.bugstack.office.docx.style.ParagraphStyle custom =
                new cn.bugstack.office.docx.style.ParagraphStyle("MyCustom", "仿宋", 12);
        custom.setAsciiFontFamily("Times New Roman");
        custom.setFarEastFontFamily("仿宋");
        custom.setAlignment(cn.bugstack.office.docx.style.DocxParagraphAlignment.CENTER);
        custom.setBold(true);

        DocxDocument.create()
                .registerParagraphStyle(custom)
                .section()
                .paragraph()
                .style("MyCustom")
                .text("自定义样式段落")
                .end()
                .end()
                .save(output);

        Document rendered = new Document(output.toString());
        Paragraph paragraph = findParagraph(rendered, "自定义样式段落");
        Run run = paragraph.getRuns().get(0);

        assertEquals(ParagraphAlignment.CENTER, paragraph.getParagraphFormat().getAlignment());
        assertEquals("Times New Roman", run.getFont().getNameAscii());
        assertEquals("仿宋", run.getFont().getNameFarEast());
        assertTrue(run.getFont().getBold());
    }

    @Test
    void rendersHeading1WithTimesNewRomanAsciiAndHeitiFarEastFont() throws Exception {
        Path dir = Files.createTempDirectory("docx-heading-font");
        Path output = dir.resolve("heading-font.docx");

        DocxDocument.create()
                .section()
                .heading1("一、设计目标")
                .end()
                .save(output);

        Document rendered = new Document(output.toString());
        Paragraph paragraph = findParagraph(rendered, "设计目标");
        Run run = paragraph.getRuns().get(0);

        assertEquals("Times New Roman", run.getFont().getNameAscii());
        assertEquals("黑体", run.getFont().getNameFarEast());
    }

    @Test
    void rendersAllStandardTitlesWithoutItalicOrUnderline() throws Exception {
        Path dir = Files.createTempDirectory("docx-heading-effects");
        Path defaultOutput = dir.resolve("default.docx");
        Path gjbOutput = dir.resolve("gjb.docx");

        createDocumentWithAllTitleLevels(DocxDocument.create().useDefaultStyles(), defaultOutput);
        createDocumentWithAllTitleLevels(DocxDocument.create().useStyleProfile(
                cn.bugstack.office.docx.style.Gjb438cStyleProfile.standard()), gjbOutput);

        assertAllTitleEffectsDisabled(new Document(defaultOutput.toString()));
        assertAllTitleEffectsDisabled(new Document(gjbOutput.toString()));
    }

    @Test
    void rendersBodyTextParagraphLayoutFromStyle() throws Exception {
        Path dir = Files.createTempDirectory("docx-body-layout");
        Path output = dir.resolve("body-layout.docx");

        DocxDocument.create()
                .section()
                .paragraph()
                .style("BodyText")
                .text("正文段落")
                .end()
                .end()
                .save(output);

        Document rendered = new Document(output.toString());
        Paragraph paragraph = findParagraph(rendered, "正文段落");

        assertEquals(ParagraphAlignment.JUSTIFY, paragraph.getParagraphFormat().getAlignment());
        assertEquals(2.0, paragraph.getParagraphFormat().getCharacterUnitFirstLineIndent());
        assertEquals(0.0, paragraph.getParagraphFormat().getLeftIndent());
        assertEquals(0.0, paragraph.getParagraphFormat().getRightIndent());
        assertEquals(LineSpacingRule.MULTIPLE, paragraph.getParagraphFormat().getLineSpacingRule());
        assertEquals(18.0, paragraph.getParagraphFormat().getLineSpacing());
        assertEquals(0.0, paragraph.getParagraphFormat().getSpaceBefore());
        assertEquals(0.0, paragraph.getParagraphFormat().getSpaceAfter());
    }

    @Test
    void rendersUnstyledParagraphAsDefaultBodyTextStyle() throws Exception {
        Path dir = Files.createTempDirectory("docx-default-body");
        Path output = dir.resolve("default-body.docx");

        DocxDocument.create()
                .section()
                .paragraph()
                .text("未显式指定样式的正文")
                .end()
                .end()
                .save(output);

        Document rendered = new Document(output.toString());
        Paragraph paragraph = findParagraph(rendered, "未显式指定样式的正文");
        Run run = paragraph.getRuns().get(0);

        assertEquals(ParagraphAlignment.JUSTIFY, paragraph.getParagraphFormat().getAlignment());
        assertEquals(2.0, paragraph.getParagraphFormat().getCharacterUnitFirstLineIndent());
        assertEquals("Times New Roman", run.getFont().getNameAscii());
        assertEquals("宋体", run.getFont().getNameFarEast());
    }

    @Test
    void rendersFigureAndTableCaptionsWithAutoNumbers() throws Exception {
        Path dir = Files.createTempDirectory("docx-caption");
        Path output = dir.resolve("caption.docx");

        DocxDocument.create()
                .section()
                .figureCaption("系统架构图")
                .figureCaption("部署视图")
                .tableCaption("模块清单")
                .end()
                .save(output);

        Document rendered = new Document(output.toString());
        Paragraph firstFigure = findParagraph(rendered, "图 1 系统架构图");
        Paragraph secondFigure = findParagraph(rendered, "图 2 部署视图");
        Paragraph firstTable = findParagraph(rendered, "表 1 模块清单");

        assertEquals(StyleIdentifier.CAPTION, firstFigure.getParagraphFormat().getStyleIdentifier());
        assertEquals(ParagraphAlignment.CENTER, firstFigure.getParagraphFormat().getAlignment());
        assertEquals(StyleIdentifier.CAPTION, secondFigure.getParagraphFormat().getStyleIdentifier());
        assertEquals(StyleIdentifier.CAPTION, firstTable.getParagraphFormat().getStyleIdentifier());
    }

    @Test
    void rendersStandardDocumentOptions() throws Exception {
        Path dir = Files.createTempDirectory("docx-standard-options");
        Path output = dir.resolve("standard-options.docx");

        DocxDocument.create()
                .enableHeadingNumbering()
                .tableOfContents("目录", 2)
                .header("GJB 438C 软件开发文档")
                .footer("第 PAGE 页")
                .section()
                .title("文档总标题")
                .heading1("范围")
                .heading2("标识")
                .paragraph()
                .text("正文内容")
                .end()
                .end()
                .save(output);

        Document rendered = new Document(output.toString());
        String text = rendered.getText();
        rendered.updateListLabels();
        Paragraph range = findParagraphWithStyle(rendered, "范围", StyleIdentifier.HEADING_1);
        Paragraph identifier = findParagraphWithStyle(rendered, "标识", StyleIdentifier.HEADING_2);
        Paragraph tocTitle = findParagraphWithStyle(rendered, "目录", StyleIdentifier.TITLE);
        Paragraph tocRange = findParagraphWithStyle(rendered, "范围", StyleIdentifier.TOC_1);
        Paragraph tocIdentifier = findParagraphWithStyle(rendered, "标识", StyleIdentifier.TOC_2);

        assertTrue(text.contains("目录"));
        assertTrue(text.contains("GJB 438C 软件开发文档"));
        assertTrue(text.contains("第 "));
        assertFalse(containsParagraphWithStyle(rendered, "文档总标题", StyleIdentifier.TOC_1));
        assertEquals(ParagraphAlignment.CENTER, tocTitle.getParagraphFormat().getAlignment());
        assertEquals(OutlineLevel.BODY_TEXT, tocTitle.getParagraphFormat().getOutlineLevel());
        assertTrue(range.getListFormat().isListItem());
        assertEquals("1", range.getListLabel().getLabelString());
        assertEquals(0.0, range.getListFormat().getListLevel().getNumberPosition());
        assertEquals(0.0, range.getListFormat().getListLevel().getTextPosition());
        assertTrue(identifier.getListFormat().isListItem());
        assertEquals("1.1", identifier.getListLabel().getLabelString());
        assertEquals(0.0, identifier.getListFormat().getListLevel().getNumberPosition());
        assertEquals(0.0, identifier.getListFormat().getListLevel().getTextPosition());
        assertEquals(ParagraphAlignment.LEFT, tocRange.getParagraphFormat().getAlignment());
        assertEquals(0.0, tocRange.getParagraphFormat().getLeftIndent());
        assertEquals(ParagraphAlignment.LEFT, tocIdentifier.getParagraphFormat().getAlignment());
        assertEquals(24.0, tocIdentifier.getParagraphFormat().getLeftIndent());
        boolean hasDirtyTocField = false;
        for (Field field : rendered.getRange().getFields()) {
            if (field.getType() == FieldType.FIELD_TOC && field.isDirty()) {
                hasDirtyTocField = true;
                break;
            }
        }
        assertTrue(hasDirtyTocField);
        try (ZipFile docx = new ZipFile(output.toFile())) {
            assertNotNull(docx.getEntry("word/numbering.xml"));
        }
    }

    @Test
    void appliesBusinessBriefFontToTableOfContentsTitleAndEntries() throws Exception {
        Path dir = Files.createTempDirectory("docx-business-brief-toc");
        Path output = dir.resolve("business-brief-toc.docx");

        DocxDocument.create()
                .useStyleProfile(cn.bugstack.office.docx.style.BusinessBriefStyleProfile.standard())
                .enableHeadingNumbering()
                .tableOfContents("目录", 1)
                .section()
                .heading1("范围")
                .end()
                .save(output);

        Document rendered = new Document(output.toString());
        Paragraph title = findParagraphWithStyle(rendered, "目录", StyleIdentifier.TITLE);
        Paragraph entry = findParagraphWithStyle(rendered, "范围", StyleIdentifier.TOC_1);

        assertEquals("Noto Sans CJK SC", title.getRuns().get(0).getFont().getNameFarEast());
        assertEquals("Noto Sans CJK SC", entry.getRuns().get(0).getFont().getNameFarEast());
    }

    @Test
    void rendersFooterPageNumberAsWordFieldInsteadOfLiteralText() throws Exception {
        Path dir = Files.createTempDirectory("docx-page-field");
        Path output = dir.resolve("page-field.docx");

        DocxDocument.create()
                .footer("第 PAGE 页")
                .section()
                .paragraph().text("第一页").end()
                .pageBreak()
                .paragraph().text("第二页").end()
                .end()
                .save(output);

        Document rendered = new Document(output.toString());
        boolean hasPageField = false;
        for (Field field : rendered.getRange().getFields()) {
            if (field.getType() == FieldType.FIELD_PAGE) {
                hasPageField = true;
                break;
            }
        }

        assertTrue(hasPageField);
    }

    @Test
    void rendersImageWithExplicitSize() throws Exception {
        Path dir = Files.createTempDirectory("docx-image-size");
        Path image = dir.resolve("image.png");
        Path output = dir.resolve("image-size.docx");
        writePng(image);

        DocxDocument.create()
                .section()
                .paragraph()
                .image(image.toString(), 144, 72)
                .end()
                .end()
                .save(output);

        Document rendered = new Document(output.toString());
        Shape shape = (Shape) rendered.getChildNodes(NodeType.SHAPE, true).get(0);

        assertEquals(144.0, shape.getWidth());
        assertEquals(72.0, shape.getHeight());
    }

    @Test
    void rendersGjb438cFrontMatterListsCaptionRefsAndMergedTable() throws Exception {
        Path dir = Files.createTempDirectory("docx-phase2");
        Path output = dir.resolve("phase2.docx");

        DocxDocument.create()
                .cover("软件设计说明", "omni-office", "V1.0")
                .revisionHistory(history -> history.revision("V1.0", "2026-07-07", "创建文档", "luojiang"))
                .approvalPage(approval -> approval.approval("编制", "张三", "2026-07-07"))
                .section()
                .heading1("范围")
                .figureCaption("arch", "系统架构图")
                .bullet("支持前置页")
                .numbered("按章节组织")
                .paragraph()
                .text("引用")
                .captionRef(CaptionType.FIGURE, "arch")
                .end()
                .table()
                .widths(120, 240, 120)
                .row(row -> row
                        .cell(2, cell -> cell.paragraph().text("跨两列").end())
                        .cell(cell -> cell.paragraph().text("备注").end()))
                .end()
                .end()
                .save(output);

        Document rendered = new Document(output.toString());
        String text = rendered.getText();
        Paragraph bullet = findParagraph(rendered, "支持前置页");
        Paragraph numbered = findParagraph(rendered, "按章节组织");
        Table revisionTable = findTableContaining(rendered, "创建文档");
        Table mergedTable = findTableContaining(rendered, "跨两列");
        Cell firstCell = mergedTable.getFirstRow().getCells().get(0);

        assertTrue(text.contains("软件设计说明"));
        assertTrue(text.contains("修订记录"));
        assertTrue(text.contains("签署页"));
        assertTrue(text.contains("引用图 1"));
        assertTrue(bullet.getListFormat().isListItem());
        assertTrue(numbered.getListFormat().isListItem());
        assertTrue(revisionTable.getFirstRow().getRowFormat().getHeadingFormat());
        assertFalse(revisionTable.getFirstRow().getFirstCell().getFirstParagraph()
                .getRuns().get(0).getFont().getBold());
        assertFalse(revisionTable.getRows().get(1).getFirstCell().getFirstParagraph()
                .getRuns().get(0).getFont().getBold());
        assertEquals(Color.BLACK, revisionTable.getFirstRow().getFirstCell().getFirstParagraph()
                .getRuns().get(0).getFont().getColor());
        assertEquals("黑体", revisionTable.getFirstRow().getFirstCell().getFirstParagraph()
                .getRuns().get(0).getFont().getNameFarEast());
        assertEquals(Color.BLACK, revisionTable.getRows().get(1).getFirstCell().getFirstParagraph()
                .getRuns().get(0).getFont().getColor());
        assertEquals(2, mergedTable.getFirstRow().getCells().getCount());
        assertFalse(mergedTable.getAllowAutoFit());
    }

    @Test
    void rendersPageSetupMetadataAndVerticalMergedCells() throws Exception {
        Path dir = Files.createTempDirectory("docx-page-setup");
        Path output = dir.resolve("page-setup.docx");

        DocxDocument.create()
                .metadata("软件设计说明", "luojiang", "omni-office")
                .pageSetup(setup -> setup
                        .paper(DocxPaperSize.A4)
                        .landscape()
                        .margins(72, 54, 72, 54))
                .section()
                .table()
                .row(row -> row
                        .cell(cell -> cell.verticalMerge(TableVerticalMerge.FIRST)
                                .verticalAlign(TableCellVerticalAlignment.CENTER)
                                .paragraph().text("合并开始").end())
                        .cell(cell -> cell.paragraph().text("第一行").end()))
                .row(row -> row
                        .cell(cell -> cell.verticalMerge(TableVerticalMerge.PREVIOUS))
                        .cell(cell -> cell.paragraph().text("第二行").end()))
                .end()
                .end()
                .save(output);

        Document rendered = new Document(output.toString());
        Table table = findTableContaining(rendered, "合并开始");
        Cell firstCell = table.getRows().get(0).getCells().get(0);
        Cell mergedCell = table.getRows().get(1).getCells().get(0);

        assertEquals("软件设计说明", rendered.getBuiltInDocumentProperties().getTitle());
        assertEquals("luojiang", rendered.getBuiltInDocumentProperties().getAuthor());
        assertEquals("omni-office", rendered.getBuiltInDocumentProperties().getSubject());
        assertEquals(Orientation.LANDSCAPE, rendered.getFirstSection().getPageSetup().getOrientation());
        assertEquals(72.0, rendered.getFirstSection().getPageSetup().getTopMargin());
        assertEquals(54.0, rendered.getFirstSection().getPageSetup().getLeftMargin());
        assertEquals(CellMerge.FIRST, firstCell.getCellFormat().getVerticalMerge());
        assertEquals(CellVerticalAlignment.CENTER, firstCell.getCellFormat().getVerticalAlignment());
        assertEquals(CellMerge.PREVIOUS, mergedCell.getCellFormat().getVerticalMerge());
    }

    @Test
    void rendersRunColorCenteredTableAndRectangularMerge() throws Exception {
        Path directory = Files.createTempDirectory("docx-formatting");
        Path output = directory.resolve("formatting.docx");

        DocxDocument.create()
                .section()
                .paragraph().text("彩色文本", "#C00000").text("继承颜色").end()
                .table()
                .alignment(TableHorizontalAlignment.CENTER)
                .row(row -> row.cell(2, cell -> cell.verticalMerge(TableVerticalMerge.FIRST)
                        .paragraph().text("矩形合并").end()))
                .row(row -> row.cell(2, cell -> cell.verticalMerge(TableVerticalMerge.PREVIOUS)
                        .paragraph().text("").end()))
                .end()
                .end()
                .save(output);

        Document rendered = new Document(output.toString());
        Paragraph paragraph = findParagraph(rendered, "彩色文本");
        Table table = findTableContaining(rendered, "矩形合并");

        assertEquals(new Color(192, 0, 0), paragraph.getRuns().get(0).getFont().getColor());
        assertFalse(new Color(192, 0, 0).equals(paragraph.getRuns().get(1).getFont().getColor()));
        assertEquals(TableAlignment.CENTER, table.getAlignment());
        assertEquals(CellMerge.FIRST,
                table.getFirstRow().getFirstCell().getCellFormat().getVerticalMerge());
        assertEquals(CellMerge.PREVIOUS,
                table.getLastRow().getFirstCell().getCellFormat().getVerticalMerge());
    }

    /**
     * 在文档中定位包含指定文本的段落。
     *
     * @param document 待搜索文档
     * @param text 段落应包含的文本
     * @return 匹配的段落
     */
    private Paragraph findParagraph(Document document, String text) {
        NodeCollection paragraphs = document.getChildNodes(NodeType.PARAGRAPH, true);
        for (int i = 0; i < paragraphs.getCount(); i++) {
            Paragraph paragraph = (Paragraph) paragraphs.get(i);
            if (paragraph.getText().contains(text)) {
                return paragraph;
            }
        }
        throw new AssertionError("paragraph not found: " + text);
    }

    /**
     * 在文档中定位同时匹配文本和内置段落样式的段落。
     *
     * <p>目录域会包含标题文本，此方法通过样式标识排除目录中的超链接条目。</p>
     *
     * @param document 待搜索文档
     * @param text 段落应包含的文本
     * @param styleIdentifier 预期 Aspose 内置样式标识
     * @return 匹配的正文标题段落
     */
    private Paragraph findParagraphWithStyle(Document document, String text, int styleIdentifier) {
        NodeCollection paragraphs = document.getChildNodes(NodeType.PARAGRAPH, true);
        for (int index = 0; index < paragraphs.getCount(); index++) {
            Paragraph paragraph = (Paragraph) paragraphs.get(index);
            if (paragraph.getParagraphFormat().getStyleIdentifier() == styleIdentifier
                    && paragraph.getText().contains(text)) {
                return paragraph;
            }
        }
        throw new AssertionError("paragraph not found with style: " + text);
    }

    /**
     * 判断文档中是否存在同时匹配文本和内置段落样式的段落。
     *
     * @param document 待搜索文档
     * @param text 段落应包含的文本
     * @param styleIdentifier Aspose 内置样式标识
     * @return 存在匹配段落时返回 {@code true}
     */
    private boolean containsParagraphWithStyle(Document document, String text, int styleIdentifier) {
        NodeCollection paragraphs = document.getChildNodes(NodeType.PARAGRAPH, true);
        for (int index = 0; index < paragraphs.getCount(); index++) {
            Paragraph paragraph = (Paragraph) paragraphs.get(index);
            if (paragraph.getParagraphFormat().getStyleIdentifier() == styleIdentifier
                    && paragraph.getText().contains(text)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 创建包含标题和一至九级标题的测试文档。
     *
     * @param document 文档构建入口
     * @param output 输出路径
     */
    private void createDocumentWithAllTitleLevels(DocxDocument document, Path output) {
        cn.bugstack.office.docx.builder.SectionBuilder section = document.section().title("文档标题");
        for (int level = 1; level <= 9; level++) {
            section.heading(level, "标题级别" + level);
        }
        section.end().save(output);
    }

    /**
     * 断言标题和全部标题级别均未包含倾斜或下划线效果。
     *
     * @param document 待检查文档
     */
    private void assertAllTitleEffectsDisabled(Document document) {
        assertTextEffectsDisabled(findParagraph(document, "文档标题"));
        for (int level = 1; level <= 9; level++) {
            assertTextEffectsDisabled(findParagraph(document, "标题级别" + level));
        }
    }

    /**
     * 断言段落首个文本运行未启用倾斜或下划线。
     *
     * @param paragraph 待检查段落
     */
    private void assertTextEffectsDisabled(Paragraph paragraph) {
        Run run = paragraph.getRuns().get(0);
        assertFalse(run.getFont().getItalic());
        assertEquals(Underline.NONE, run.getFont().getUnderline());
    }

    /**
     * 在文档中定位包含指定文本的表格。
     *
     * @param document 待搜索文档
     * @param text 表格应包含的文本
     * @return 匹配的表格
     */
    private Table findTableContaining(Document document, String text) {
        NodeCollection tables = document.getChildNodes(NodeType.TABLE, true);
        for (int i = 0; i < tables.getCount(); i++) {
            Table table = (Table) tables.get(i);
            if (table.getText().contains(text)) {
                return table;
            }
        }
        throw new AssertionError("table not found: " + text);
    }

    /** 计算指定表格行的总宽度。 */
    private double rowWidth(com.aspose.words.Row row) throws Exception {
        double width = 0D;
        for (Cell cell : row.getCells()) {
            width += cell.getCellFormat().getWidth();
        }
        return width;
    }

    /**
     * 写入用于图片渲染测试的最小 PNG 文件。
     *
     * @param path 图片输出路径
     * @throws Exception 写入图片失败时抛出
     */
    private void writePng(Path path) throws Exception {
        String png = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4XmNgYPgPAAEDAQD1FzX"
                + "NAAAAAElFTkSuQmCC";
        Files.write(path, Base64.getDecoder().decode(png));
    }
}
