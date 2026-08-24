package cn.bugstack.application.document;

import cn.bugstack.export.api.ReportOutputFormat;
import cn.bugstack.protocol.document.DocumentMetadataSpec;
import cn.bugstack.protocol.document.DocumentSpec;
import cn.bugstack.protocol.document.SectionSpec;
import cn.bugstack.protocol.document.block.BulletListBlockSpec;
import cn.bugstack.protocol.document.block.ParagraphBlockSpec;
import cn.bugstack.protocol.document.block.TableBlockSpec;
import cn.bugstack.protocol.document.block.TableMergeSpec;
import cn.bugstack.protocol.document.block.TextRangeSpec;
import cn.bugstack.protocol.document.block.TextRangeStyleSpec;
import com.aspose.words.CellMerge;
import com.aspose.words.Document;
import com.aspose.words.NodeCollection;
import com.aspose.words.NodeType;
import com.aspose.words.Paragraph;
import com.aspose.words.ParagraphAlignment;
import com.aspose.words.PreferredWidthType;
import com.aspose.words.Table;
import com.aspose.words.TableAlignment;
import com.aspose.words.Underline;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 生成字体颜色、表格对齐/合并和题注位置的可视化验收文档。
 *
 * <p>执行命令：</p>
 * <pre>
 * /Users/luojiang/maven/apache-maven-3.6.3/bin/mvn \
 *   -Dtest=DocumentFormattingCapabilitiesDemoTest test
 * </pre>
 */
class DocumentFormattingCapabilitiesDemoTest {

    /** 演示文档的稳定输出位置。 */
    private static final Path OUTPUT = Path.of("target", "formatting-capabilities-demo.docx");

    /**
     * 通过 DocumentSpec 公共协议生成演示文档，并回读关键 Word 属性。
     *
     * @throws Exception 当文档生成或回读失败时抛出
     */
    @Test
    void generatesFormattingCapabilitiesDemoDocument() throws Exception {
        DocumentSpec spec = createDemoSpec();
        DocumentSpecValidationResult validation = new DocumentSpecValidator().validate(spec);
        assertTrue(validation.isValid(), () -> validation.getViolations().toString());

        Files.createDirectories(OUTPUT.getParent());
        new DefaultDynamicDocumentExporter().export(spec, ReportOutputFormat.DOCX, OUTPUT);

        Document word = new Document(OUTPUT.toAbsolutePath().toString());
        NodeCollection tables = word.getChildNodes(NodeType.TABLE, true);
        Paragraph coloredParagraph = findParagraph(word, "红色字体用于风险提示");
        Paragraph richParagraph = findParagraph(word, "同一段落");
        String text = word.getText();

        assertEquals(new Color(192, 0, 0), coloredParagraph.getRuns().get(0).getFont().getColor());
        assertEquals(5, richParagraph.getRuns().getCount());
        assertEquals(new Color(192, 0, 0), richParagraph.getRuns().get(1).getFont().getColor());
        assertTrue(richParagraph.getRuns().get(1).getFont().getBold());
        assertEquals(new Color(31, 78, 121), richParagraph.getRuns().get(2).getFont().getColor());
        assertEquals(Underline.SINGLE, richParagraph.getRuns().get(2).getFont().getUnderline());
        assertEquals(14D, richParagraph.getRuns().get(3).getFont().getSize(), 0.1D);
        assertTrue(richParagraph.getRuns().get(3).getFont().getItalic());
        assertEquals("Arial", richParagraph.getRuns().get(4).getFont().getNameAscii());
        assertEquals(3, tables.getCount());
        assertEquals(TableAlignment.CENTER, ((Table) tables.get(0)).getAlignment());
        assertEquals(TableAlignment.LEFT, ((Table) tables.get(1)).getAlignment());
        assertEquals(TableAlignment.RIGHT, ((Table) tables.get(2)).getAlignment());
        double availablePageWidth = word.getFirstSection().getPageSetup().getPageWidth()
                - word.getFirstSection().getPageSetup().getLeftMargin()
                - word.getFirstSection().getPageSetup().getRightMargin();
        double renderedTableWidth = rowWidth(((Table) tables.get(0)).getFirstRow());
        for (int tableIndex = 0; tableIndex < tables.getCount(); tableIndex++) {
            Table table = (Table) tables.get(tableIndex);
            assertEquals(PreferredWidthType.POINTS, table.getPreferredWidth().getType());
            assertEquals(availablePageWidth, table.getPreferredWidth().getValue(), 0.1D);
            assertEquals(renderedTableWidth, rowWidth(table.getFirstRow()), 0.1D);
            assertEquals(renderedTableWidth, rowWidth(table.getLastRow()), 0.1D);
            assertEquals(ParagraphAlignment.CENTER,
                    table.getFirstRow().getFirstCell().getFirstParagraph().getParagraphFormat().getAlignment());
        }
        assertEquals(CellMerge.FIRST,
                ((Table) tables.get(1)).getRows().get(1).getFirstCell().getCellFormat().getVerticalMerge());
        assertEquals(CellMerge.PREVIOUS,
                ((Table) tables.get(1)).getRows().get(2).getFirstCell().getCellFormat().getVerticalMerge());
        assertEquals(CellMerge.FIRST,
                ((Table) tables.get(2)).getRows().get(1).getFirstCell().getCellFormat().getVerticalMerge());
        assertEquals(CellMerge.PREVIOUS,
                ((Table) tables.get(2)).getRows().get(2).getFirstCell().getCellFormat().getVerticalMerge());
        assertTrue(text.indexOf("表 1 居中表格（题注在上方）") < text.indexOf("横向合并标题"));
        assertTrue(text.indexOf("业务域 A") < text.indexOf("表 2 左对齐表格（题注在下方）"));

        System.out.println("Formatting demo generated: " + OUTPUT.toAbsolutePath());
    }

    /** 创建覆盖三项新增能力的 DocumentSpec。 */
    private DocumentSpec createDemoSpec() {
        DocumentSpec spec = new DocumentSpec();
        spec.setMetadata(new DocumentMetadataSpec("Word 格式能力验收文档"));
        spec.getLayout().setHeaderText("omni-office · DocumentSpec → Word");
        spec.getLayout().setFooterText("字体颜色 / 表格对齐与合并 / 题注位置");

        SectionSpec overview = new SectionSpec("一、字体颜色");
        ParagraphBlockSpec risk = new ParagraphBlockSpec("红色字体用于风险提示：该段内容应显示为深红色。");
        risk.setStyleName("BodyText");
        risk.setFontColor("#C00000");
        overview.addBlock(risk);
        ParagraphBlockSpec information = new ParagraphBlockSpec("蓝色字体用于说明信息：该段内容应显示为蓝色。");
        information.setStyleName("BodyText");
        information.setFontColor("#1F4E79");
        overview.addBlock(information);
        overview.addBlock(richTextParagraph());
        BulletListBlockSpec checklist = new BulletListBlockSpec();
        checklist.setItems(Arrays.asList("段落颜色可设置", "列表颜色可设置", "表格文字颜色可设置"));
        checklist.setStyleName("BodyText");
        checklist.setFontColor("#548235");
        overview.addBlock(checklist);
        spec.addSection(overview);

        SectionSpec tables = new SectionSpec("二、表格对齐与合并");
        tables.addBlock(centeredHorizontalMergeTable());
        tables.addBlock(leftVerticalMergeTable());
        tables.addBlock(rightRectangularMergeTable());
        spec.addSection(tables);

        SectionSpec captions = new SectionSpec("三、验收说明");
        captions.addBlock(new ParagraphBlockSpec(
                "第一张和第三张表的题注位于表格上方；第二张表的题注位于表格下方。"));
        captions.addBlock(new ParagraphBlockSpec(
                "三张表分别采用居中、左对齐和右对齐，可通过表格相对页面的位置直接观察。"));
        spec.addSection(captions);
        return spec;
    }

    /** 创建同一段落包含多个独立样式文本范围的示例。 */
    private ParagraphBlockSpec richTextParagraph() {
        ParagraphBlockSpec paragraph = new ParagraphBlockSpec();
        paragraph.setStyleName("BodyText");
        paragraph.setTextRanges(Arrays.asList(
                range("同一段落：", null),
                range("红色粗体", style("#C00000", null, true, null, null, null)),
                range("、蓝色下划线", style("#1F4E79", null, null, null, true, null)),
                range("、14磅斜体", style(null, 14D, null, true, null, null)),
                range("、Arial 字体", style(null, null, null, null, null, "Arial"))));
        return paragraph;
    }

    /** 创建文本范围。 */
    private TextRangeSpec range(String text, TextRangeStyleSpec style) {
        TextRangeSpec range = new TextRangeSpec(text);
        range.setStyle(style);
        return range;
    }

    /** 创建演示用文本范围样式。 */
    private TextRangeStyleSpec style(String color, Double size, Boolean bold, Boolean italic,
                                     Boolean underline, String fontFamily) {
        TextRangeStyleSpec style = new TextRangeStyleSpec();
        style.setFontColor(color);
        style.setFontSize(size);
        style.setBold(bold);
        style.setItalic(italic);
        style.setUnderline(underline);
        style.setFontFamily(fontFamily);
        return style;
    }

    /** 创建居中且表头横向合并的表格。 */
    private TableBlockSpec centeredHorizontalMergeTable() {
        TableBlockSpec table = baseTable("CENTER", "居中表格（题注在上方）", "ABOVE",
                Arrays.asList("横向合并标题", "", ""),
                Arrays.asList(
                        Arrays.asList("模块", "能力", "结果"),
                        Arrays.asList("DocumentSpec", "横向合并", "通过")),
                110D, 150D, 100D);
        table.setMerges(Collections.singletonList(new TableMergeSpec(0, 0, 1, 3)));
        return table;
    }

    /** 创建左对齐且第一列纵向合并的表格。 */
    private TableBlockSpec leftVerticalMergeTable() {
        TableBlockSpec table = baseTable("LEFT", "左对齐表格（题注在下方）", "BELOW",
                Arrays.asList("业务域", "组件", "状态"),
                Arrays.asList(
                        Arrays.asList("业务域 A", "文档生成", "正常"),
                        Arrays.asList("", "图形生成", "正常")),
                110D, 150D, 100D);
        table.setMerges(Collections.singletonList(new TableMergeSpec(1, 0, 2, 1)));
        return table;
    }

    /** 创建右对齐且左侧两行两列矩形合并的表格。 */
    private TableBlockSpec rightRectangularMergeTable() {
        TableBlockSpec table = baseTable("RIGHT", "右对齐矩形合并表格（题注在上方）", "ABOVE",
                Arrays.asList("区域", "子项", "结果"),
                Arrays.asList(
                        Arrays.asList("2×2 矩形合并", "", "第一行"),
                        Arrays.asList("", "", "第二行")),
                100D, 120D, 100D);
        table.setMerges(Collections.singletonList(new TableMergeSpec(1, 0, 2, 2)));
        return table;
    }

    /** 创建具有公共展示属性的表格。 */
    private TableBlockSpec baseTable(String alignment, String caption, String captionPosition,
                                     java.util.List<String> headers,
                                     java.util.List<java.util.List<String>> rows,
                                     Double... widths) {
        TableBlockSpec table = new TableBlockSpec();
        table.setStyleName("TableHeader");
        table.setAlignment(alignment);
        table.setFontColor("#1F4E79");
        table.setCaption(caption);
        table.setCaptionAutoNumbered(true);
        table.setCaptionPosition(captionPosition);
        table.setHeaders(headers);
        table.setRows(rows);
        table.setColumnWidths(Arrays.asList(widths));
        return table;
    }

    /** 在 Word 文档中查找包含指定文本的段落。 */
    private Paragraph findParagraph(Document document, String expectedText) {
        NodeCollection paragraphs = document.getChildNodes(NodeType.PARAGRAPH, true);
        for (int index = 0; index < paragraphs.getCount(); index++) {
            Paragraph paragraph = (Paragraph) paragraphs.get(index);
            if (paragraph.getText().contains(expectedText)) {
                return paragraph;
            }
        }
        throw new AssertionError("paragraph not found: " + expectedText);
    }

    /** 计算 Aspose 回读行中所有逻辑单元格的总宽度。 */
    private double rowWidth(com.aspose.words.Row row) throws Exception {
        double width = 0D;
        for (com.aspose.words.Cell cell : row.getCells()) {
            width += cell.getCellFormat().getWidth();
        }
        return width;
    }
}
