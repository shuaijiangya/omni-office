package cn.bugstack.application.template.importer;

import com.aspose.words.Document;
import com.aspose.words.DocumentBuilder;
import com.aspose.words.StyleIdentifier;
import com.aspose.words.ChartType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 上传 DOCX 模板的章节、样式和占位符识别测试。 */
class DocxTemplateAnalyzerTest {

    @TempDir
    Path temporaryDirectory;

    /** Word 标题、跨 Run 标记、表格与原生图表均归入正确章节。 */
    @Test
    void extractsOutlineStylesMarkersAndChartSamples() throws Exception {
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        builder.getParagraphFormat().setStyleIdentifier(StyleIdentifier.TITLE);
        builder.writeln("项目评估报告");
        builder.getParagraphFormat().setStyleIdentifier(StyleIdentifier.HEADING_1);
        builder.writeln("项目概述");
        builder.getParagraphFormat().setStyleIdentifier(StyleIdentifier.NORMAL);
        builder.write("项目名称：{{project");
        builder.getFont().setBold(true);
        builder.writeln(".name}}");
        builder.getFont().setBold(false);
        builder.getParagraphFormat().setStyleIdentifier(StyleIdentifier.HEADING_2);
        builder.writeln("指标分析");
        builder.getParagraphFormat().setStyleIdentifier(StyleIdentifier.NORMAL);
        builder.startTable();
        builder.insertCell();
        builder.write("指标");
        builder.insertCell();
        builder.write("{{metric.value}}");
        builder.endRow();
        builder.endTable();
        builder.insertChart(ChartType.COLUMN, 320, 180);
        Path source = temporaryDirectory.resolve("评估模板.docx");
        document.save(source.toString());

        DocxTemplateAnalysis analysis = new DocxTemplateAnalyzer().analyze(source, "assessment");
        JsonNode structure = analysis.getStructure();
        assertEquals(2, structure.path("sections").size());
        assertEquals(1, structure.path("sections").get(0).path("level").asInt());
        assertEquals(2, structure.path("sections").get(1).path("level").asInt());
        assertEquals("project.name", structure.path("sections").get(0)
                .path("placeholders").get(0).path("name").asText());
        assertEquals("metric.value", structure.path("sections").get(1)
                .path("placeholders").get(0).path("name").asText());
        assertTrue(structure.path("styles").path("paragraphs").has("Heading 1"));
        assertTrue(structure.path("sections").get(1).path("blocks").toString().contains("\"chart\""));
        assertEquals("AI_WRITE", analysis.getReportTemplate().path("sections").get(0).path("mode").asText());
        assertEquals("AI_WRITE", analysis.getReportTemplate().path("sections").get(1).path("mode").asText());
        assertTrue(analysis.getStyleBindings().path("sections").path("section-002")
                .path("blockSamples").has("chart"));
        assertFalse(analysis.getStyleBindings().path("sourceSha256").asText().isEmpty());
        DocxTemplateBindingReview review = new DocxTemplateBindingReview();
        review.validate(source, analysis.getReportTemplate(), analysis.getStyleBindings());
        ObjectNode staleBindings = analysis.getStyleBindings();
        staleBindings.put("sourceSha256", "wrong-source");
        assertThrows(IllegalArgumentException.class,
                () -> review.validate(source, analysis.getReportTemplate(), staleBindings));
    }

    /** 无语义标题时只报告审核问题，不依据字号猜测章节。 */
    @Test
    void reportsUnstyledHeadingsAsReviewIssue() throws Exception {
        Document document = new Document();
        DocumentBuilder builder = new DocumentBuilder(document);
        builder.getFont().setSize(18);
        builder.writeln("视觉上像标题");
        builder.getFont().setSize(12);
        builder.writeln("{{content}}");
        Path source = temporaryDirectory.resolve("visual-only.docx");
        document.save(source.toString());

        DocxTemplateAnalysis analysis = new DocxTemplateAnalyzer().analyze(source, "visual-only");
        assertEquals(0, analysis.getReportTemplate().path("sections").size());
        assertTrue(analysis.getStructure().path("reviewIssues").toString().contains("NO_HEADINGS"));
    }
}
