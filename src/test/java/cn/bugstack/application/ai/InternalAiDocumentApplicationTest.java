package cn.bugstack.application.ai;

import cn.bugstack.export.api.ReportOutputFormat;
import com.aspose.words.Document;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalAiDocumentApplicationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void generatesFreeformDocumentAndExportsOnlyAfterValidation() throws Exception {
        ScriptedAiClient client = new ScriptedAiClient(resourceText("/document-spec/1.0/example-simple.json"));
        InternalAiDocumentApplication application = new InternalAiDocumentApplication(client);

        AiDocumentResult result = application.generateFreeform("生成一份系统评估报告");
        byte[] docx = application.exportToBytes(result, ReportOutputFormat.DOCX);
        Document word = new Document(new ByteArrayInputStream(docx));

        assertEquals(AiGenerationMode.FREEFORM_DOCUMENT, result.getMode());
        assertEquals(1, result.getAttempts());
        assertTrue(word.getText().contains("系统评估报告"));
        assertEquals("generate_document_spec", client.requests.get(0).getOperation());
        assertTrue(client.requests.get(0).getSystemInstruction().contains("DocumentSpec"));
        assertTrue(client.requests.get(0).getSystemInstruction().contains("not enabled"));
    }

    @Test
    void retriesInvalidDocumentWithBoundedValidationFeedback() throws Exception {
        ScriptedAiClient client = new ScriptedAiClient(
                "{\"schemaVersion\":\"1.0\"}",
                resourceText("/document-spec/1.0/example-simple.json"));
        InternalAiDocumentApplication application = new InternalAiDocumentApplication(client);

        AiDocumentResult result = application.generateFreeform("生成报告");

        assertEquals(2, result.getAttempts());
        assertEquals(2, client.requests.size());
        assertFalse(client.requests.get(1).getValidationFeedback().isEmpty());
        assertEquals(2, client.requests.get(1).getAttempt());
    }

    @Test
    void rejectsAiGeneratedImageSourcesAndFeedsBackSafetyError() throws Exception {
        JsonNode root = mapper.readTree(resourceText("/document-spec/1.0/example-simple.json"));
        ((com.fasterxml.jackson.databind.node.ArrayNode) root.path("sections").get(0).path("blocks"))
                .add(mapper.readTree("{\"type\":\"image\",\"source\":\"https://untrusted.example/image.png\"}"));
        ScriptedAiClient client = new ScriptedAiClient(root.toString(),
                resourceText("/document-spec/1.0/example-simple.json"));
        InternalAiDocumentApplication application = new InternalAiDocumentApplication(client);

        AiDocumentResult result = application.generateFreeform("生成包含说明内容的报告");

        assertEquals(2, result.getAttempts());
        assertTrue(client.requests.get(1).getValidationFeedback().stream()
                .anyMatch(value -> value.contains("AI_IMAGE_NOT_ALLOWED")));
    }

    @Test
    void generatesOnlyTemplateDataForExplicitTemplateVersion() throws Exception {
        ScriptedAiClient client = new ScriptedAiClient(
                resourceText("/document-template/1.0/example-assessment-data.json"));
        InternalAiDocumentApplication application = new InternalAiDocumentApplication(client);
        try (InputStream template = resource("/document-template/1.0/example-assessment-template.json")) {
            application.registerTemplate(template);
        }

        AiDocumentResult result = application.generateFromTemplate(
                "system.assessment", "1.0.0", "根据上下文填写评估模板");

        assertEquals(AiGenerationMode.DOCUMENT_TEMPLATE, result.getMode());
        assertEquals("system.assessment", result.getTemplateId());
        assertEquals("1.0.0", result.getTemplateVersion());
        assertEquals("无人系统评估报告", result.getDocumentSpec().getMetadata().getTitle());
        assertTrue(result.getTemplateData().has("project"));
        assertEquals("fill_document_template", client.requests.get(0).getOperation());
        assertTrue(client.requests.get(0).getSystemInstruction().contains("Do not return DocumentSpec"));
        assertEquals(application.getTemplateDataSchema("system.assessment", "1.0.0"),
                client.requests.get(0).getOutputSchema());
    }

    @Test
    void retriesTemplateDataAgainstItsRegisteredSchema() throws Exception {
        JsonNode invalid = mapper.readTree(resourceText(
                "/document-template/1.0/example-assessment-data.json"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) invalid.path("risks").get(0)).put("level", "严重");
        ScriptedAiClient client = new ScriptedAiClient(invalid.toString(),
                resourceText("/document-template/1.0/example-assessment-data.json"));
        InternalAiDocumentApplication application = new InternalAiDocumentApplication(client);
        try (InputStream template = resource("/document-template/1.0/example-assessment-template.json")) {
            application.registerTemplate(template);
        }

        AiDocumentResult result = application.generateFromTemplate(
                "system.assessment", "1.0.0", "填写评估数据");

        assertEquals(2, result.getAttempts());
        assertFalse(client.requests.get(1).getValidationFeedback().isEmpty());
        assertTrue(client.requests.get(1).getValidationFeedback().stream()
                .anyMatch(value -> value.contains("risks")));
    }

    @Test
    void neverCallsModelOrFallsBackWhenTemplateCoordinateIsUnknown() {
        ScriptedAiClient client = new ScriptedAiClient("{}");
        InternalAiDocumentApplication application = new InternalAiDocumentApplication(client);

        assertThrows(IllegalArgumentException.class, () -> application.generateFromTemplate(
                "missing.template", "1.0.0", "生成报告"));
        assertTrue(client.requests.isEmpty());
    }

    @Test
    void failsAfterConfiguredValidationAttemptsWithoutExporting() {
        ScriptedAiClient client = new ScriptedAiClient("{} trailing text", "[]");
        InternalAiDocumentApplication application = new InternalAiDocumentApplication(client);

        AiGenerationException exception = assertThrows(AiGenerationException.class,
                () -> application.generateFreeform("生成报告"));

        assertEquals(2, exception.getAttempts());
        assertEquals(AiGenerationMode.FREEFORM_DOCUMENT, exception.getMode());
        assertFalse(exception.getValidationErrors().isEmpty());
    }

    private String resourceText(String path) throws IOException {
        try (InputStream input = resource(path)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private InputStream resource(String path) {
        InputStream input = getClass().getResourceAsStream(path);
        if (input == null) {
            throw new IllegalStateException("missing resource: " + path);
        }
        return input;
    }

    private static final class ScriptedAiClient implements StructuredAiClient {

        private final List<String> outputs;
        private final List<StructuredAiRequest> requests = new ArrayList<>();
        private int index;

        private ScriptedAiClient(String... outputs) {
            this.outputs = Arrays.asList(outputs);
        }

        @Override
        public String generateJson(StructuredAiRequest request) {
            requests.add(request);
            if (index >= outputs.size()) {
                throw new IllegalStateException("no scripted AI output remains");
            }
            return outputs.get(index++);
        }
    }
}
