package cn.bugstack.export.example;

import com.aspose.words.CellMerge;
import com.aspose.words.CellVerticalAlignment;
import com.aspose.words.Document;
import com.aspose.words.NodeCollection;
import com.aspose.words.NodeType;
import com.aspose.words.Paragraph;
import com.aspose.words.ParagraphAlignment;
import com.aspose.words.Section;
import com.aspose.words.Table;
import com.aspose.words.Underline;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link FormattingCapabilitiesReportExportExample} 的 Word 属性回归测试。 */
class FormattingCapabilitiesReportExportExampleTest {

    /**
     * 验证示例同时覆盖富文本范围、响应式表格、默认居中和纵向合并。
     *
     * @throws Exception 示例生成或 Word 回读失败时抛出
     */
    @Test
    void exportsRichTextAndResponsiveCenteredTables() throws Exception {
        FormattingCapabilitiesReportExportExample.main(new String[0]);

        assertTrue(Files.exists(FormattingCapabilitiesReportExportExample.OUTPUT));
        Document document = new Document(FormattingCapabilitiesReportExportExample.OUTPUT.toString());
        Paragraph richText = findParagraph(document, "评估对象");
        NodeCollection tableNodes = document.getChildNodes(NodeType.TABLE, true);
        Table responsiveTable = (Table) tableNodes.get(0);
        Table mergedTable = (Table) tableNodes.get(1);
        Section section = (Section) responsiveTable.getAncestor(NodeType.SECTION);
        double availableWidth = section.getPageSetup().getPageWidth()
                - section.getPageSetup().getLeftMargin()
                - section.getPageSetup().getRightMargin();
        double firstColumnWidth = responsiveTable.getFirstRow().getCells().get(0).getCellFormat().getWidth();
        double secondColumnWidth = responsiveTable.getFirstRow().getCells().get(1).getCellFormat().getWidth();

        assertEquals(new Color(31, 78, 121), richText.getRuns().get(1).getFont().getColor());
        assertTrue(richText.getRuns().get(1).getFont().getBold());
        assertEquals(Underline.SINGLE, richText.getRuns().get(4).getFont().getUnderline());
        assertEquals("Arial", richText.getRuns().get(5).getFont().getNameAscii());
        assertTrue(richText.getRuns().get(5).getFont().getItalic());
        assertEquals(14D, richText.getRuns().get(5).getFont().getSize(), 0.1D);

        assertEquals(availableWidth, responsiveTable.getPreferredWidth().getValue(), 0.1D);
        assertEquals(2.5D, secondColumnWidth / firstColumnWidth, 0.05D);
        assertEquals(ParagraphAlignment.CENTER,
                responsiveTable.getFirstRow().getFirstCell().getFirstParagraph()
                        .getParagraphFormat().getAlignment());
        assertEquals(CellVerticalAlignment.CENTER,
                responsiveTable.getFirstRow().getFirstCell().getCellFormat().getVerticalAlignment());
        assertEquals("黑体", responsiveTable.getFirstRow().getFirstCell().getFirstParagraph()
                .getRuns().get(0).getFont().getNameFarEast());
        assertEquals("Times New Roman", responsiveTable.getFirstRow().getFirstCell().getFirstParagraph()
                .getRuns().get(0).getFont().getNameAscii());
        assertEquals("Times New Roman", responsiveTable.getRows().get(1).getCells().get(1).getFirstParagraph()
                .getRuns().get(0).getFont().getNameAscii());
        assertEquals("宋体", responsiveTable.getRows().get(1).getCells().get(1).getFirstParagraph()
                .getRuns().get(0).getFont().getNameFarEast());
        assertFalse(responsiveTable.getFirstRow().getFirstCell().getFirstParagraph()
                .getRuns().get(0).getFont().getBold());
        assertTrue(!responsiveTable.getRows().get(1).getFirstCell().getFirstParagraph()
                .getRuns().get(0).getFont().getBold());
        assertEquals(Color.BLACK, mergedTable.getFirstRow().getFirstCell().getFirstParagraph()
                .getRuns().get(0).getFont().getColor());
        assertEquals("Arial", mergedTable.getFirstRow().getFirstCell().getFirstParagraph()
                .getRuns().get(0).getFont().getNameAscii());
        assertEquals("微软雅黑", mergedTable.getFirstRow().getFirstCell().getFirstParagraph()
                .getRuns().get(0).getFont().getNameFarEast());
        assertEquals(Color.BLACK, mergedTable.getRows().get(1).getFirstCell().getFirstParagraph()
                .getRuns().get(0).getFont().getColor());
        assertEquals("Calibri", mergedTable.getRows().get(1).getFirstCell().getFirstParagraph()
                .getRuns().get(0).getFont().getNameAscii());
        assertEquals("仿宋", mergedTable.getRows().get(1).getFirstCell().getFirstParagraph()
                .getRuns().get(0).getFont().getNameFarEast());
        assertEquals(CellMerge.FIRST,
                mergedTable.getRows().get(1).getFirstCell().getCellFormat().getVerticalMerge());
        assertEquals(CellMerge.PREVIOUS,
                mergedTable.getRows().get(2).getFirstCell().getCellFormat().getVerticalMerge());
    }

    /** 查找包含指定文本的段落。 */
    private Paragraph findParagraph(Document document, String text) {
        NodeCollection paragraphs = document.getChildNodes(NodeType.PARAGRAPH, true);
        for (int index = 0; index < paragraphs.getCount(); index++) {
            Paragraph paragraph = (Paragraph) paragraphs.get(index);
            if (paragraph.getText().contains(text)) {
                return paragraph;
            }
        }
        throw new AssertionError("paragraph not found: " + text);
    }
}
