package cn.bugstack.application.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassPathAiOutputSchemaProviderTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void bundlesDiagramSchemaWithoutExternalReferences() throws Exception {
        JsonNode schema = new ClassPathAiOutputSchemaProvider().documentSpecSchema();
        JsonNode document = mapper.readTree("{"
                + "\"schemaVersion\":\"1.0\","
                + "\"metadata\":{\"title\":\"AI 图形报告\"},"
                + "\"layout\":{},"
                + "\"sections\":[{\"title\":\"流程\",\"blocks\":[{"
                + "\"type\":\"diagram\",\"embedMode\":\"PREVIEW_IMAGE\","
                + "\"definition\":{\"schemaVersion\":\"1.0\",\"type\":\"FLOW\",\"title\":\"流程\","
                + "\"nodes\":[{\"id\":\"s\",\"label\":\"开始\",\"type\":\"START\"}],\"edges\":[]}}]}]}" );

        assertFalse(hasExternalReference(schema));
        assertTrue(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(schema).validate(document).isEmpty());
    }

    private boolean hasExternalReference(JsonNode node) {
        if (node == null) {
            return false;
        }
        if (node.isObject()) {
            JsonNode reference = node.get("$ref");
            if (reference != null && reference.isTextual() && !reference.asText().startsWith("#")) {
                return true;
            }
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                if (hasExternalReference(fields.next().getValue())) {
                    return true;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                if (hasExternalReference(child)) {
                    return true;
                }
            }
        }
        return false;
    }
}
