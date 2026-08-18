package cn.bugstack.application.ai.observability;

import cn.bugstack.application.ai.StructuredAiRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiObservabilityTest {

    @Test
    void recordsHashesAndMetricsWithoutPromptOrOutputBodies() throws Exception {
        java.nio.file.Path path = Files.createTempDirectory("ai-traces").resolve("trace.jsonl");
        JsonLinesAiTraceStore store = new JsonLinesAiTraceStore(path);
        TracingStructuredAiClient client = new TracingStructuredAiClient(request -> "{\"ok\":true}",
                store, "ollama", "qwen3.5:2b");
        String output = client.generateJson(new StructuredAiRequest("test", "system-secret",
                "user-sensitive-text", new ObjectMapper().createObjectNode().put("secret", "context-value"),
                new ObjectMapper().createObjectNode(), 1, Collections.emptyList()));
        assertEquals("{\"ok\":true}", output);
        AiCallTrace trace = store.readAll().get(0);
        assertTrue(trace.isSuccess());
        assertEquals("qwen3.5:2b", trace.getModel());
        assertEquals(output.length(), trace.getOutputCharacters());
        assertNotEquals("user-sensitive-text", trace.getInstructionSha256());
        String raw = Files.readString(path);
        assertTrue(!raw.contains("user-sensitive-text") && !raw.contains("context-value")
                && !raw.contains("{\"ok\":true}"));
    }
}
