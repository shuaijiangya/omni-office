package cn.bugstack.export.example;

import com.aspose.words.CellMerge;
import com.aspose.words.Document;
import com.aspose.words.NodeCollection;
import com.aspose.words.NodeType;
import com.aspose.words.Paragraph;
import com.aspose.words.Section;
import com.aspose.words.Table;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AssessmentReportExportExample} 的文档结构回归测试。
 */
class AssessmentReportExportExampleTest {

    /**
     * 验证风险模块能生成一级、二级和三级标题。
     *
     * @throws Exception 示例文档生成或读取失败时抛出
     */
    @Test
    void exportsThreeLevelHeadingsFromRiskModule() throws Exception {
        AssessmentReportExportExample.main(new String[0]);

        Path output = Path.of("target/assessment-report-example.docx");
        Document document = new Document(output.toString());
        document.updateListLabels();

        assertTrue(Files.exists(output));
        assertHeadingNumber(document, "风险项", "2");
        assertHeadingNumber(document, "风险分析", "2.1");
        assertHeadingNumber(document, "处置建议", "2.1.1");
    }

    /**
     * 验证目录后直接衔接第一个模块内容，不包含框架追加的标题或基础信息表。
     *
     * @throws Exception 示例文档生成或读取失败时抛出
     */
    @Test
    void startsBodyDirectlyWithFirstModuleContentAfterTableOfContents() throws Exception {
        AssessmentReportExportExample.main(new String[0]);

        Path output = Path.of("target/assessment-report-example.docx");
        Document document = new Document(output.toString());
        Section bodySection = document.getSections().get(document.getSections().getCount() - 1);
        Paragraph firstBodyParagraph = firstNonEmptyParagraph(bodySection);

        assertEquals("评估概述", firstBodyParagraph.getText().trim());
        assertFalse(bodySection.getText().contains("ASSESSMENT-20260806-001"));
        assertFalse(bodySection.getText().contains("报告编号"));
        assertFalse(bodySection.getText().contains("生成时间"));
    }

    /**
     * 验证两行重复的“通过”可以由合并配置收敛为一个纵向合并单元格。
     *
     * @throws Exception 示例文档生成或读取失败时抛出
     */
    @Test
    void mergesRepeatedAssessmentConclusionContent() throws Exception {
        AssessmentReportExportExample.main(new String[0]);

        Document document = new Document("target/assessment-report-example.docx");
        Table table = findTable(document, "架构边界");

        assertEquals(CellMerge.FIRST,
                table.getRows().get(1).getCells().get(1).getCellFormat().getVerticalMerge());
        assertEquals(CellMerge.PREVIOUS,
                table.getRows().get(2).getCells().get(1).getCellFormat().getVerticalMerge());
        assertEquals("通过", table.getRows().get(1).getCells().get(1).getText().trim());
    }

    /**
     * 验证指定标题使用 Word 原生列表编号。
     *
     * @param document 已更新列表标签的 Word 文档
     * @param headingText 标题正文
     * @param expectedNumber 预期列表标签
     */
    private void assertHeadingNumber(Document document, String headingText, String expectedNumber) {
        for (Section section : document.getSections()) {
            for (Paragraph paragraph : section.getBody().getParagraphs()) {
                if (headingText.equals(paragraph.getText().trim())) {
                    assertTrue(paragraph.getListFormat().isListItem());
                    assertTrue(expectedNumber.equals(paragraph.getListLabel().getLabelString()));
                    return;
                }
            }
        }
        throw new AssertionError("heading not found: " + headingText);
    }

    /**
     * 获取 Section 中第一个包含正文字符的段落。
     *
     * @param section 待检查的 Word Section
     * @return 第一个非空段落
     */
    private Paragraph firstNonEmptyParagraph(Section section) {
        for (Paragraph paragraph : section.getBody().getParagraphs()) {
            if (!paragraph.getText().trim().isEmpty()) {
                return paragraph;
            }
        }
        throw new AssertionError("body section does not contain a non-empty paragraph");
    }

    /** 查找包含指定文本的表格。 */
    private Table findTable(Document document, String text) {
        NodeCollection tables = document.getChildNodes(NodeType.TABLE, true);
        for (int index = 0; index < tables.getCount(); index++) {
            Table table = (Table) tables.get(index);
            if (table.getText().contains(text)) {
                return table;
            }
        }
        throw new AssertionError("table not found: " + text);
    }
}
