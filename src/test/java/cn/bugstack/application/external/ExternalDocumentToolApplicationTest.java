package cn.bugstack.application.external;

import cn.bugstack.application.template.JsonSchemaTemplateDataValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalDocumentToolApplicationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void exposesStableToolCatalogAndTemplateDiscovery() throws Exception {
        ExternalDocumentToolApplication application = application("external-tools-catalog");
        registerAssessmentTemplate(application);

        assertEquals(8, application.listTools().size());
        ExternalToolDefinition documentTool = application.listTools().stream()
                .filter(tool -> ExternalDocumentToolApplication.EXPORT_DOCUMENT.equals(tool.getName()))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals("#/$defs/diagramSpec", documentTool.getInputSchema().path("$defs")
                .path("diagramBlock").path("properties").path("definition").path("$ref").asText());
        assertEquals("object", documentTool.getInputSchema().path("$defs")
                .path("diagramSpec").path("type").asText());
        assertEquals("chart", documentTool.getInputSchema().path("$defs")
                .path("chartBlock").path("properties").path("type").path("const").asText());
        ExternalToolResult listed = application.call(ExternalDocumentToolApplication.LIST_TEMPLATES,
                mapper.createObjectNode());
        assertEquals("system.assessment",
                listed.getStructuredContent().path("templates").get(0).path("templateId").asText());

        ObjectNode key = mapper.createObjectNode();
        key.put("templateId", "system.assessment");
        key.put("version", "1.0.0");
        JsonNode schema = application.call(ExternalDocumentToolApplication.GET_TEMPLATE_SCHEMA, key)
                .getStructuredContent().path("dataSchema");
        assertEquals("object", schema.path("type").asText());
        assertTrue(schema.path("properties").has("project"));
    }

    @Test
    void exportsDocumentSpecAndTemplateToReadableArtifacts() throws Exception {
        ExternalDocumentToolApplication application = application("external-tools-export");
        registerAssessmentTemplate(application);

        ObjectNode documentArguments = (ObjectNode) readResource(
                "/document-spec/1.0/example-simple.json").deepCopy();
        documentArguments.put("outputFormat", "DOCX");
        ExternalToolDefinition documentTool = application.listTools().stream()
                .filter(tool -> ExternalDocumentToolApplication.EXPORT_DOCUMENT.equals(tool.getName()))
                .findFirst().orElseThrow(AssertionError::new);
        assertTrue(new JsonSchemaTemplateDataValidator()
                .validate(documentTool.getInputSchema(), documentArguments).isEmpty());
        ExternalToolResult documentResult = application.call(
                ExternalDocumentToolApplication.EXPORT_DOCUMENT, documentArguments);
        assertDocx(application, documentResult.getStructuredContent().path("artifact").path("resourceUri").asText());

        ObjectNode templateArguments = mapper.createObjectNode();
        templateArguments.put("templateId", "system.assessment");
        templateArguments.put("version", "1.0.0");
        templateArguments.put("outputFormat", "DOCX");
        templateArguments.set("data", readResource(
                "/document-template/1.0/example-assessment-data.json"));
        ExternalToolResult templateResult = application.call(
                ExternalDocumentToolApplication.EXPORT_TEMPLATE, templateArguments);
        assertDocx(application, templateResult.getStructuredContent().path("artifact").path("resourceUri").asText());
    }

    @Test
    void generatesDiagramArtifactsThatCanBeEmbeddedById() throws Exception {
        ExternalDocumentToolApplication application = application("external-tools-diagram");
        JsonNode diagram = readResource("/diagram-spec/1.0/example-flow.json");

        ExternalToolResult generated = application.call(
                ExternalDocumentToolApplication.GENERATE_DIAGRAM, diagram);
        String diagramArtifactId = generated.getStructuredContent().path("diagramArtifactId").asText();
        assertTrue(diagramArtifactId.matches("[0-9a-f-]{36}"));
        assertEquals(2, generated.getArtifacts().size());
        assertTrue(Files.size(application.readResource(generated.getArtifacts().get(0).getResourceUri())
                .getContentPath()) > 0);
        assertTrue(Files.size(application.readResource(generated.getArtifacts().get(1).getResourceUri())
                .getContentPath()) > 0);

        ObjectNode documentArguments = (ObjectNode) readResource(
                "/document-spec/1.0/example-simple.json").deepCopy();
        ObjectNode diagramBlock = mapper.createObjectNode();
        diagramBlock.put("type", "diagram");
        diagramBlock.put("diagramArtifactId", diagramArtifactId);
        diagramBlock.put("embedMode", "PREVIEW_IMAGE");
        ((com.fasterxml.jackson.databind.node.ArrayNode) documentArguments.path("sections")
                .get(0).path("blocks")).add(diagramBlock);
        documentArguments.put("outputFormat", "DOCX");

        ExternalToolResult exported = application.call(
                ExternalDocumentToolApplication.EXPORT_DOCUMENT, documentArguments);
        assertDocx(application, exported.getStructuredContent().path("artifact").path("resourceUri").asText());
    }

    @Test
    void rejectsUnknownToolsAndSupportsHtmlFormat() throws Exception {
        ExternalDocumentToolApplication application = application("external-tools-errors");
        assertThrows(UnknownExternalToolException.class,
                () -> application.call("missing", mapper.createObjectNode()));

        ObjectNode documentArguments = (ObjectNode) readResource(
                "/document-spec/1.0/example-simple.json").deepCopy();
        documentArguments.put("outputFormat", "HTML");
        ExternalToolResult result = application.call(
                ExternalDocumentToolApplication.EXPORT_DOCUMENT, documentArguments);
        ResolvedExternalArtifact html = application.readResource(
                result.getStructuredContent().path("artifact").path("resourceUri").asText());
        assertEquals("text/html", html.getReference().getMediaType());
        assertTrue(new String(Files.readAllBytes(html.getContentPath()), java.nio.charset.StandardCharsets.UTF_8)
                .toLowerCase().contains("<html"));

        documentArguments.put("outputFormat", "RTF");
        assertThrows(IllegalArgumentException.class, () -> application.call(
                ExternalDocumentToolApplication.EXPORT_DOCUMENT, documentArguments));
    }

    @Test
    void rejectsUnmanagedImageSourcesFromExternalDocumentSpec() throws Exception {
        ExternalDocumentToolApplication application = application("external-tools-image-boundary");
        ObjectNode document = (ObjectNode) readResource("/document-spec/1.0/example-simple.json").deepCopy();
        ObjectNode image = mapper.createObjectNode();
        image.put("type", "image");
        image.put("source", "/etc/passwd");
        ((com.fasterxml.jackson.databind.node.ArrayNode) document.path("sections").get(0).path("blocks"))
                .add(image);
        document.put("outputFormat", "DOCX");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> application.call(ExternalDocumentToolApplication.EXPORT_DOCUMENT, document));
        assertTrue(error.getMessage().contains("EXTERNAL_IMAGE_SOURCE_NOT_ALLOWED"));
        ExternalToolDefinition tool = application.listTools().stream()
                .filter(item -> ExternalDocumentToolApplication.EXPORT_DOCUMENT.equals(item.getName()))
                .findFirst().orElseThrow();
        JsonNode imageSchema = tool.getInputSchema().path("$defs").path("imageBlock");
        assertEquals("string", imageSchema.path("properties").path("assetId").path("type").asText());
        assertTrue(!imageSchema.path("properties").has("source"));
    }

    @Test
    void storesReadsAndDeletesPrincipalOwnedManagedImageAssets() throws Exception {
        ExternalDocumentToolApplication application = application("external-tools-assets");
        ObjectNode upload = mapper.createObjectNode();
        upload.put("fileName", "pixel.png");
        upload.put("mediaType", "image/png");
        upload.put("contentBase64", "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        ExternalToolResult stored = application.call(
                ExternalDocumentToolApplication.STORE_ASSET, upload, "alice");
        String assetId = stored.getStructuredContent().path("assetId").asText();
        assertTrue(assetId.matches("[0-9a-f-]{36}"));

        ObjectNode key = mapper.createObjectNode().put("assetId", assetId);
        assertEquals(assetId, application.call(ExternalDocumentToolApplication.GET_ASSET, key, "alice")
                .getStructuredContent().path("assetId").asText());
        assertThrows(IllegalArgumentException.class,
                () -> application.call(ExternalDocumentToolApplication.GET_ASSET, key, "bob"));
        assertTrue(application.call(ExternalDocumentToolApplication.DELETE_ASSET, key, "alice")
                .getStructuredContent().path("deleted").asBoolean());
    }

    private ExternalDocumentToolApplication application(String prefix) throws Exception {
        return new ExternalDocumentToolApplication(Files.createTempDirectory(prefix));
    }

    private void registerAssessmentTemplate(ExternalDocumentToolApplication application) throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                "/document-template/1.0/example-assessment-template.json")) {
            assertNotNull(input);
            application.registerTemplate(input);
        }
    }

    private JsonNode readResource(String path) throws Exception {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertNotNull(input);
            return mapper.readTree(input);
        }
    }

    private void assertDocx(ExternalDocumentToolApplication application, String uri) throws Exception {
        byte[] bytes = Files.readAllBytes(application.readResource(uri).getContentPath());
        assertTrue(bytes.length > 4);
        assertEquals((byte) 'P', bytes[0]);
        assertEquals((byte) 'K', bytes[1]);
    }
}
