package cn.bugstack.application.ai.report;

import cn.bugstack.application.ai.AiGenerationPolicy;
import cn.bugstack.application.ai.StructuredAiClient;
import cn.bugstack.application.ai.StructuredAiRequest;
import cn.bugstack.application.ai.ollama.OllamaStructuredAiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredReportTemplateAiApplicationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void generatesEditableTemplateFromTextAndValidatesManualChanges() throws Exception {
        ObjectNode draft = sourceTemplate();
        RecordingClient client = new RecordingClient(draft);
        StructuredReportTemplateAiApplication app = new StructuredReportTemplateAiApplication(client);

        ObjectNode generated = app.generate("项目有背景、检查、风险与结论，检查可用表格。",
                "shared-text-assessment");

        assertEquals("design_report_template", client.requests.get(0).getOperation());
        assertEquals("shared-text-assessment", generated.path("templateId").asText());
        assertEquals(6, generated.path("sections").size());
        assertEquals("/content", generated.path("sections").get(3).path("dataRefs").get(0).asText());
        assertFalse(app.toPrettyJson(generated).contains("项目有背景、检查"));

        Path file = tempDir.resolve("editable-template.json");
        Files.writeString(file, app.toPrettyJson(generated), StandardCharsets.UTF_8);
        ObjectNode edited = (ObjectNode) mapper.readTree(Files.readString(file, StandardCharsets.UTF_8));
        ((ObjectNode) edited.withArray("sections").get(1)).put("title", "业务背景");
        app.validate(edited);
        assertEquals("业务背景", edited.path("sections").get(1).path("title").asText());
    }

    @Test
    void sendsUnstructuredDescriptionDirectlyToModel() throws Exception {
        String description = "请写一份系统评估报告，先说明背景，再展示检查记录与风险，最后给出结论；"
                + "检查记录多时适合表格，少时用段落。";
        RecordingClient client = new RecordingClient(sourceTemplate());
        StructuredReportTemplateAiApplication app = new StructuredReportTemplateAiApplication(client);

        ObjectNode generated = app.generate(description, "shared-text-assessment");

        assertEquals(description, client.requests.get(0).getContext().path("sourceText").asText());
        assertEquals(1, client.requests.get(0).getContext().size());
        assertEquals(6, generated.path("sections").size());
    }

    @Test
    void revisesTemplateWithoutMutatingOriginal() throws Exception {
        ObjectNode original = sourceTemplate();
        ObjectNode changed = original.deepCopy();
        ((ObjectNode) changed.withArray("sections").get(3)).put("title", "因果对分析");
        RecordingClient client = new RecordingClient(changed);
        StructuredReportTemplateAiApplication app = new StructuredReportTemplateAiApplication(client);

        ObjectNode revised = app.revise(original, "将验证结果改为因果对分析");

        assertEquals("revise_report_template", client.requests.get(0).getOperation());
        assertEquals("验证结果", original.path("sections").get(3).path("title").asText());
        assertEquals("因果对分析", revised.path("sections").get(3).path("title").asText());
    }

    @Test
    void retriesInvalidStructureAndRejectsUnscopedDataRefs() throws Exception {
        ObjectNode invalid = sourceTemplate();
        ((ObjectNode) invalid.withArray("sections").get(3)).withArray("dataRefs").set(0,
                mapper.getNodeFactory().textNode("/secret"));
        RecordingClient client = new RecordingClient(invalid, sourceTemplate());
        StructuredReportTemplateAiApplication app = new StructuredReportTemplateAiApplication(client);

        app.generate("生成评估报告", "shared-text-assessment");

        assertEquals(2, client.requests.size());
        assertEquals(2, client.requests.get(1).getAttempt());
        assertFalse(client.requests.get(1).getValidationFeedback().isEmpty());
        assertTrue(client.requests.get(1).getValidationFeedback().get(0)
                .contains("report template JSON format"));
        assertThrows(IllegalArgumentException.class, () -> app.validate(invalid));
    }

    @Test
    void rejectsWrongIdAndHeadingLevelJump() throws Exception {
        ObjectNode wrongId = sourceTemplate();
        wrongId.put("templateId", "different");
        RecordingClient client = new RecordingClient(wrongId);
        StructuredReportTemplateAiApplication app = new StructuredReportTemplateAiApplication(client);
        assertThrows(IllegalStateException.class,
                () -> app.generate("生成评估报告", "shared-text-assessment"));

        ObjectNode jump = sourceTemplate();
        ((ObjectNode) jump.withArray("sections").get(0)).put("level", 2);
        assertThrows(IllegalArgumentException.class, () -> app.validate(jump));
    }

    @Test
    void preservesModelChosenOutlineWithoutParsingTextKeywords() throws Exception {
        ObjectNode unordered = sourceTemplate();
        ((ObjectNode) unordered.withArray("sections").get(0)).put("mode", "AI_WRITE")
                .put("prompt", "请设计项目概述标题模板。")
                .putArray("dataRefs").add("/content");
        ((ObjectNode) unordered.withArray("sections").get(0)).putArray("allowedBlocks").add("paragraph");
        JsonNode child = unordered.withArray("sections").remove(1);
        unordered.withArray("sections").add(child);
        RecordingClient client = new RecordingClient(unordered);
        StructuredReportTemplateAiApplication app = new StructuredReportTemplateAiApplication(client);

        ObjectNode result = app.generate("一级标题依次为概述、验证、结论。"
                + "项目概述下设“项目背景”。父标题不写正文。", "shared-text-assessment");

        assertEquals("项目概述", result.path("sections").get(0).path("title").asText());
        assertEquals("功能验证", result.path("sections").get(1).path("title").asText());
        assertEquals("项目背景", result.path("sections").get(5).path("title").asText());
        assertEquals("AI_WRITE", result.path("sections").get(0).path("mode").asText());
        assertFalse(client.requests.get(0).getContext().has("explicitOutline"));
        assertEquals(1, client.requests.size());
    }

    @Test
    void retriesResponseThatDoesNotMatchStandardJsonFormat() throws Exception {
        ObjectNode invalid = sourceTemplate();
        ((ObjectNode) invalid.withArray("sections").get(0)).put("prompt", "撰写概述");
        RecordingClient client = new RecordingClient(invalid, sourceTemplate());
        StructuredReportTemplateAiApplication app = new StructuredReportTemplateAiApplication(client);

        ObjectNode result = app.generate("生成报告标题模板", "shared-text-assessment");

        assertEquals(2, client.requests.size());
        assertEquals("项目概述", result.path("sections").get(0).path("title").asText());
        assertFalse(result.path("sections").get(0).has("prompt"));
        assertThrows(IllegalArgumentException.class, () -> app.validate(invalid));
    }

    @Test
    void ollamaFollowsExplicitHierarchyWhenEnabled() throws Exception {
        String description = Files.readString(Path.of("docs/examples/structured-report-template-request.txt"),
                StandardCharsets.UTF_8);
        StructuredReportTemplateAiApplication app = new StructuredReportTemplateAiApplication(
                new OllamaStructuredAiClient("qwen3.5:2b"),
                new AiGenerationPolicy(4, 20_000, 100_000, 2_000_000));

        ObjectNode result = app.generate(description, "shared-text-assessment");
        JsonNode sections = result.path("sections");

        assertEquals("共享业务文本评估报告", result.path("title").asText());
        assertEquals(6, sections.size());
        assertEquals("项目概述", sections.get(0).path("title").asText());
        assertEquals(1, sections.get(0).path("level").asInt());
        assertEquals("HEADING_ONLY", sections.get(0).path("mode").asText());
        assertEquals("项目背景", sections.get(1).path("title").asText());
        assertEquals(2, sections.get(1).path("level").asInt());
        assertEquals("功能验证", sections.get(2).path("title").asText());
        assertEquals(1, sections.get(2).path("level").asInt());
        assertEquals("HEADING_ONLY", sections.get(2).path("mode").asText());
        assertEquals("验证结果", sections.get(3).path("title").asText());
        assertEquals(2, sections.get(3).path("level").asInt());
        assertEquals("风险与建议", sections.get(4).path("title").asText());
        assertEquals(2, sections.get(4).path("level").asInt());
        assertEquals("评估结论", sections.get(5).path("title").asText());
        assertEquals(1, sections.get(5).path("level").asInt());
        Set<String> ids = new HashSet<>();
        for (JsonNode section : sections) {
            assertFalse(section.path("id").asText().equals(section.path("title").asText()));
            assertTrue(ids.add(section.path("id").asText()));
        }
        assertTrue(sections.get(3).path("allowedBlocks").toString().contains("table"));
    }

    @Test
    void formatCheckDoesNotCompareHeadingCountWithSourceText() throws Exception {
        ObjectNode oneSection = sourceTemplate();
        JsonNode conclusion = oneSection.withArray("sections").get(5).deepCopy();
        oneSection.withArray("sections").removeAll().add(conclusion);
        RecordingClient client = new RecordingClient(oneSection);
        StructuredReportTemplateAiApplication app = new StructuredReportTemplateAiApplication(client);

        ObjectNode generated = app.generate("报告需要概述、背景、验证结果和结论", "shared-text-assessment");

        assertEquals(1, generated.path("sections").size());
        assertEquals(1, client.requests.size());
    }

    @Test
    void rejectsExtraFieldsAndTrailingJson() throws Exception {
        ObjectNode extraField = sourceTemplate();
        extraField.put("unrecognized", true);
        StructuredReportTemplateAiApplication extraFieldApp =
                new StructuredReportTemplateAiApplication(new RecordingClient(extraField));
        assertThrows(IllegalStateException.class,
                () -> extraFieldApp.generate("生成报告", "shared-text-assessment"));

        String multipleObjects = sourceTemplate().toString() + "{}";
        StructuredReportTemplateAiApplication trailingJsonApp =
                new StructuredReportTemplateAiApplication(request -> multipleObjects);
        assertThrows(IllegalStateException.class,
                () -> trailingJsonApp.generate("生成报告", "shared-text-assessment"));

        ObjectNode duplicateId = sourceTemplate();
        ((ObjectNode) duplicateId.withArray("sections").get(1))
                .put("id", duplicateId.path("sections").get(0).path("id").asText());
        StructuredReportTemplateAiApplication duplicateIdApp =
                new StructuredReportTemplateAiApplication(new RecordingClient(duplicateId));
        assertThrows(IllegalStateException.class,
                () -> duplicateIdApp.generate("生成报告", "shared-text-assessment"));

        ObjectNode titleAsId = sourceTemplate();
        ((ObjectNode) titleAsId.withArray("sections").get(0)).put("id", "项目概述");
        StructuredReportTemplateAiApplication titleAsIdApp =
                new StructuredReportTemplateAiApplication(new RecordingClient(titleAsId));
        assertThrows(IllegalStateException.class,
                () -> titleAsIdApp.generate("生成报告", "shared-text-assessment"));
    }

    private ObjectNode sourceTemplate() throws Exception {
        JsonNode template = mapper.readTree(Files.readString(Path.of(
                "docs/examples/structured-report-shared-text-template.json"), StandardCharsets.UTF_8));
        return (ObjectNode) template;
    }

    private static final class RecordingClient implements StructuredAiClient {
        private final List<StructuredAiRequest> requests = new ArrayList<>();
        private final JsonNode[] replies;

        private RecordingClient(JsonNode... replies) {
            this.replies = replies;
        }

        @Override
        public String generateJson(StructuredAiRequest request) {
            requests.add(request);
            return replies[Math.min(requests.size() - 1, replies.length - 1)].toString();
        }
    }
}
