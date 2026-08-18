package cn.bugstack.application.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaGovernanceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void checksCompatibilityPublishesImmutableVersionsAndRunsExplicitMigration() {
        ObjectNode v1 = schema(false);
        ObjectNode compatible = schema(false);
        compatible.withObject("properties").putObject("optional").put("type", "string");
        ObjectNode incompatible = schema(true);
        assertTrue(new JsonSchemaCompatibilityChecker().backwardCompatible(v1, compatible).isCompatible());
        assertFalse(new JsonSchemaCompatibilityChecker().backwardCompatible(v1, incompatible).isCompatible());

        ProtocolSchemaRegistry schemas = new ProtocolSchemaRegistry();
        schemas.publish("document-spec", "1.0", v1, null, false);
        schemas.publish("document-spec", "1.1", compatible, "1.0", true);
        assertThrows(IllegalStateException.class,
                () -> schemas.publish("document-spec", "1.1", compatible, "1.0", true));

        SchemaMigrationRegistry migrations = new SchemaMigrationRegistry();
        migrations.register(new SchemaMigration() {
            public String protocol() { return "document-spec"; }
            public String fromVersion() { return "1.0"; }
            public String toVersion() { return "1.1"; }
            public JsonNode migrate(JsonNode input) {
                ((ObjectNode) input).put("schemaVersion", "1.1");
                return input;
            }
        });
        assertEquals("1.1", migrations.migrate("document-spec", "1.0", "1.1",
                mapper.createObjectNode().put("schemaVersion", "1.0")).path("schemaVersion").asText());
        assertThrows(IllegalArgumentException.class, () -> migrations.migrate(
                "document-spec", "1.1", "2.0", mapper.createObjectNode()));
    }

    private ObjectNode schema(boolean addRequired) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.putObject("properties").putObject("name").put("type", "string");
        schema.putArray("required").add("name");
        if (addRequired) {
            schema.withObject("properties").putObject("newField").put("type", "string");
            schema.withArray("required").add("newField");
        }
        return schema;
    }
}
