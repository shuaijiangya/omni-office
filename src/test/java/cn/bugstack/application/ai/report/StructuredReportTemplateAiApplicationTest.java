package cn.bugstack.application.ai.report;

import cn.bugstack.application.ai.StructuredAiClient;
import cn.bugstack.application.ai.StructuredAiRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
        assertTrue(client.requests.get(1).getValidationFeedback().get(0).contains("dataRefs"));
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
    void alignsExplicitParentChildOutlineAndRemovesParentBody() throws Exception {
        ObjectNode unordered = sourceTemplate();
        ((ObjectNode) unordered.withArray("sections").get(0)).put("mode", "AI_WRITE")
                .put("prompt", "请设计项目概述标题模板。")
                .putArray("dataRefs").add("/content");
        ((ObjectNode) unordered.withArray("sections").get(0)).putArray("allowedBlocks").add("paragraph");
        JsonNode child = unordered.withArray("sections").remove(1);
        unordered.withArray("sections").add(child);
        RecordingClient client = new RecordingClient(unordered);
        StructuredReportTemplateAiApplication app = new StructuredReportTemplateAiApplication(client);

        ObjectNode result = app.generate("一级标题依次为项目概述、功能验证、评估结论。"
                + "项目概述下设“项目背景”。父标题不写正文。", "shared-text-assessment");

        assertEquals("项目概述", result.path("sections").get(0).path("title").asText());
        assertEquals("项目背景", result.path("sections").get(1).path("title").asText());
        assertEquals("HEADING_ONLY", result.path("sections").get(0).path("mode").asText());
        assertFalse(result.path("sections").get(0).has("prompt"));
    }

    @Test
    void refusesTemplateThatOmitsExplicitChildHeading() throws Exception {
        ObjectNode incomplete = sourceTemplate();
        incomplete.withArray("sections").remove(1);
        StructuredReportTemplateAiApplication app = new StructuredReportTemplateAiApplication(
                new RecordingClient(incomplete));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> app.generate("一级标题依次为项目概述、功能验证、评估结论。"
                        + "项目概述下设“项目背景”。", "shared-text-assessment"));

        assertTrue(error.getMessage().contains("项目背景"));
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
