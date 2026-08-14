package cn.bugstack.export.example;

import com.aspose.words.Document;
import com.aspose.words.Paragraph;
import com.aspose.words.Section;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

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
}
