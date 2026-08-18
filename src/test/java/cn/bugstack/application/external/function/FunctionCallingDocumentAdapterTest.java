package cn.bugstack.application.external.function;

import cn.bugstack.application.external.ExternalDocumentToolApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FunctionCallingDocumentAdapterTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void publishesFunctionDefinitionsAndInvokesDocumentExport() throws Exception {
        ExternalDocumentToolApplication application = new ExternalDocumentToolApplication(
                Files.createTempDirectory("function-calling"));
        FunctionCallingDocumentAdapter adapter = new FunctionCallingDocumentAdapter(application);

        JsonNode tools = mapper.readTree(adapter.listFunctionToolsJson());
        assertEquals(5, tools.size());
        assertEquals("function", tools.get(0).path("type").asText());
        assertTrue(tools.get(0).path("function").path("parameters").isObject());

        ObjectNode arguments;
        try (InputStream input = getClass().getResourceAsStream(
                "/document-spec/1.0/example-simple.json")) {
            assertNotNull(input);
            arguments = (ObjectNode) mapper.readTree(input);
        }
        arguments.put("outputFormat", "DOCX");
        JsonNode result = mapper.readTree(adapter.invoke(
                ExternalDocumentToolApplication.EXPORT_DOCUMENT, arguments.toString()));
        byte[] content = adapter.readResource(result.path("artifact").path("resourceUri").asText());
        assertEquals((byte) 'P', content[0]);
        assertEquals((byte) 'K', content[1]);
    }
}
