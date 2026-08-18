package cn.bugstack.application.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;

/** 将 DocumentSpec 与 DiagramSpec Schema 打包为无外部引用的模型输出 Schema。 */
public final class ClassPathAiOutputSchemaProvider implements AiOutputSchemaProvider {

    private static final String DIAGRAM_SCHEMA_URI
            = "https://omni-office.local/schemas/diagram-spec/1.0/schema.json";
    private final JsonNode bundledDocumentSchema;

    public ClassPathAiOutputSchemaProvider() {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode document = read(mapper, "/document-spec/1.0/schema.json");
        JsonNode diagram = read(mapper, "/diagram-spec/1.0/schema.json");
        this.bundledDocumentSchema = bundle(document, diagram);
    }

    @Override
    public JsonNode documentSpecSchema() {
        return bundledDocumentSchema.deepCopy();
    }

    private JsonNode bundle(JsonNode documentSource, JsonNode diagramSource) {
        ObjectNode document = (ObjectNode) documentSource.deepCopy();
        ObjectNode diagram = (ObjectNode) diagramSource.deepCopy();
        diagram.remove("$schema");
        diagram.remove("$id");
        rewriteDiagramLocalReferences(diagram);
        replaceExternalDiagramReference(document);
        JsonNode definitions = document.get("$defs");
        if (!(definitions instanceof ObjectNode)) {
            throw new IllegalStateException("DocumentSpec schema does not contain object $defs");
        }
        ((ObjectNode) definitions).set("diagramSpec", diagram);
        return document;
    }

    private void replaceExternalDiagramReference(JsonNode node) {
        walkObjects(node, object -> {
            JsonNode reference = object.get("$ref");
            if (reference != null && DIAGRAM_SCHEMA_URI.equals(reference.asText())) {
                object.put("$ref", "#/$defs/diagramSpec");
            }
        });
    }

    private void rewriteDiagramLocalReferences(JsonNode node) {
        walkObjects(node, object -> {
            JsonNode reference = object.get("$ref");
            if (reference != null && reference.isTextual() && reference.asText().startsWith("#/$defs/")) {
                object.put("$ref", "#/$defs/diagramSpec/$defs/" + reference.asText().substring(8));
            }
        });
    }

    private void walkObjects(JsonNode node, java.util.function.Consumer<ObjectNode> consumer) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            consumer.accept(object);
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext()) {
                walkObjects(fields.next().getValue(), consumer);
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                walkObjects(child, consumer);
            }
        }
    }

    private JsonNode read(ObjectMapper mapper, String resource) {
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("missing AI output schema resource: " + resource);
            }
            return mapper.readTree(input);
        } catch (IOException e) {
            throw new IllegalStateException("failed to read AI output schema resource: " + resource, e);
        }
    }
}
