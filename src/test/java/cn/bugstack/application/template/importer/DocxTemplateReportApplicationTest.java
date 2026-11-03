package cn.bugstack.application.template.importer;

import cn.bugstack.application.ai.StructuredAiClient;
import cn.bugstack.application.ai.report.StructuredReportAiApplication;
import com.aspose.words.Document;
import com.aspose.words.DocumentBuilder;
import com.aspose.words.NodeType;
import com.aspose.words.Shape;
import com.aspose.words.StyleIdentifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 原模板精确写回与图表编辑性集成测试。 */
class DocxTemplateReportApplicationTest {

    @TempDir Path temporaryDirectory;
    private final ObjectMapper mapper = new ObjectMapper();

    /** 多槽位、跨 Run 文本、固定表头、重复行和原生图表均写回同一源模板。 */
    @Test
    void fillsReviewedSlotsWithoutRebuildingTemplate() throws Exception {
        Path source = temporaryDirectory.resolve("source.docx");
        Document word = new Document();
        DocumentBuilder builder = new DocumentBuilder(word);
        builder.getParagraphFormat().setStyleIdentifier(StyleIdentifier.HEADING_1);
        builder.writeln("项目概述");
        builder.getParagraphFormat().setStyleIdentifier(StyleIdentifier.NORMAL);
        builder.write("固定前缀：{{project");
        builder.getFont().setBold(true);
        builder.writeln("}}；版本：{{version}}");
        builder.getFont().setBold(false);
        builder.getParagraphFormat().setStyleIdentifier(StyleIdentifier.HEADING_2);
        builder.writeln("指标明细");
        builder.getParagraphFormat().setStyleIdentifier(StyleIdentifier.NORMAL);
        builder.startTable();
        builder.insertCell(); builder.write("指标");
        builder.insertCell(); builder.write("数值");
        builder.endRow();
        builder.insertCell(); builder.write("{{table:metrics}}");
        builder.insertCell(); builder.write("示例");
        builder.endRow();
        builder.endTable();
        builder.getParagraphFormat().setStyleIdentifier(StyleIdentifier.HEADING_2);
        builder.writeln("趋势图");
        builder.getParagraphFormat().setStyleIdentifier(StyleIdentifier.NORMAL);
        builder.writeln("{{chart:trend}}");
        word.save(source.toString());

        DocxTemplateAnalysis analysis = new DocxTemplateAnalyzer().analyze(source, "reviewed-report");
        ObjectNode template = analysis.getReportTemplate();
        ObjectNode bindings = analysis.getStyleBindings();
        assertEquals(2, template.path("sections").get(0).path("slots").size());
        assertEquals("table", template.path("sections").get(1).path("slots").get(0).path("type").asText());
        assertEquals("chart", template.path("sections").get(2).path("slots").get(0).path("type").asText());
        ((ObjectNode) template.withArray("sections").get(2)).put("chartStylePreset", "BUSINESS_BLUE");
        StructuredAiClient client = request -> {
            String id = request.getContext().path("sectionId").asText();
            if ("section-001".equals(id)) return "{\"sectionId\":\"section-001\",\"status\":\"READY\","
                    + "\"blocks\":[{\"slotId\":\"slot-001\",\"type\":\"paragraph\",\"text\":\"海岚系统\",\"factRefs\":[\"/content\"]},"
                    + "{\"slotId\":\"slot-002\",\"type\":\"paragraph\",\"text\":\"V2\",\"factRefs\":[\"/content\"]}],\"questions\":[]}";
            if ("section-002".equals(id)) return "{\"sectionId\":\"section-002\",\"status\":\"READY\","
                    + "\"blocks\":[{\"slotId\":\"slot-001\",\"type\":\"table\",\"headers\":[\"指标\",\"数值\"],"
                    + "\"rows\":[[\"响应时间\",\"120ms\"],[\"成功率\",\"99%\"]],\"factRefs\":[\"/content\"]}],\"questions\":[]}";
            return "{\"sectionId\":\"section-003\",\"status\":\"READY\","
                    + "\"blocks\":[{\"slotId\":\"slot-001\",\"type\":\"chart\",\"chartType\":\"COLUMN\","
                    + "\"title\":\"请求趋势\",\"categories\":[\"一月\",\"二月\"],"
                    + "\"series\":[{\"name\":\"请求数\",\"values\":[12,18]}],\"factRefs\":[\"/content\"]}],\"questions\":[]}";
        };
        Path output = temporaryDirectory.resolve("filled.docx");
        new DocxTemplateReportApplication(new StructuredReportAiApplication(client)).generate(
                source, template, bindings, mapper.readTree("{\"content\":\"海岚系统 V2 响应时间120ms 成功率99%\"}"), output);
        assertTrue(Files.size(output) > 0);
        Document generated = new Document(output.toString());
        assertTrue(generated.getText().contains("固定前缀：海岚系统；版本：V2"));
        assertTrue(generated.getText().contains("响应时间"));
        assertTrue(generated.getText().contains("成功率"));
        assertFalse(generated.getText().contains("{{"));
        assertEquals(3, generated.getSections().get(0).getBody().getTables().get(0).getRows().getCount());
        boolean editableChart = false;
        for (Object item : generated.getChildNodes(NodeType.SHAPE, true)) {
            editableChart |= ((Shape) item).hasChart();
        }
        assertTrue(editableChart);
        assertTrue(new Document(source.toString()).getText().contains("{{project"));
    }

    /** 原模板变化时必须拒绝写回，不产生部分产物。 */
    @Test
    void rejectsStaleTemplateBeforeCallingAi() throws Exception {
        Path source = temporaryDirectory.resolve("stale.docx");
        Document word = new Document();
        DocumentBuilder builder = new DocumentBuilder(word);
        builder.getParagraphFormat().setStyleIdentifier(StyleIdentifier.HEADING_1);
        builder.writeln("概述");
        builder.getParagraphFormat().setStyleIdentifier(StyleIdentifier.NORMAL);
        builder.writeln("{{content}}");
        word.save(source.toString());
        DocxTemplateAnalysis analysis = new DocxTemplateAnalyzer().analyze(source, "stale");
        builder.writeln("模板后来新增的固定文字");
        word.save(source.toString());
        Path output = temporaryDirectory.resolve("not-created.docx");
        assertThrows(IllegalArgumentException.class, () -> new DocxTemplateReportApplication(
                new StructuredReportAiApplication(request -> "unexpected"))
                .generate(source, analysis.getReportTemplate(), analysis.getStyleBindings(),
                        mapper.readTree("{\"content\":\"hello\"}"), output));
        assertFalse(Files.exists(output));
    }

    /** 模型漏填槽位时不写出半成品。 */
    @Test
    void rejectsMissingAiSlotWithoutCreatingOutput() throws Exception {
        Path source = temporaryDirectory.resolve("two-slots.docx");
        Document word = new Document();
        DocumentBuilder builder = new DocumentBuilder(word);
        builder.getParagraphFormat().setStyleIdentifier(StyleIdentifier.HEADING_1);
        builder.writeln("概述");
        builder.getParagraphFormat().setStyleIdentifier(StyleIdentifier.NORMAL);
        builder.writeln("{{name}} / {{version}}");
        word.save(source.toString());
        DocxTemplateAnalysis analysis = new DocxTemplateAnalyzer().analyze(source, "two-slots");
        StructuredAiClient incomplete = request -> "{\"sectionId\":\"section-001\",\"status\":\"READY\","
                + "\"blocks\":[{\"slotId\":\"slot-001\",\"type\":\"paragraph\",\"text\":\"项目\","
                + "\"factRefs\":[\"/content\"]}],\"questions\":[]}";
        Path output = temporaryDirectory.resolve("incomplete.docx");
        assertThrows(IllegalStateException.class, () -> new DocxTemplateReportApplication(
                new StructuredReportAiApplication(incomplete)).generate(source, analysis.getReportTemplate(),
                analysis.getStyleBindings(), mapper.readTree("{\"content\":\"项目 V1\"}"), output));
        assertFalse(Files.exists(output));
    }
}
