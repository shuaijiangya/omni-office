package cn.bugstack.application.external.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceContractConsistencyTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void openApiHasUniqueOperationsAndRequiredProductContracts() throws Exception {
        JsonNode openApi = resource("/omni-service/1.0/openapi.json");
        assertEquals("3.0.3", openApi.path("openapi").asText());
        JsonNode paths = openApi.path("paths");
        for (String path : Set.of(
                "/v1/generation-jobs",
                "/v1/generation-jobs/{jobId}",
                "/v1/admin/templates",
                "/v1/admin/templates/{templateId}/compare",
                "/v1/admin/operations/summary",
                "/v1/webhook-deliveries",
                "/artifacts/{artifactId}")) {
            assertTrue(paths.has(path), "missing OpenAPI path: " + path);
        }
        Set<String> operationIds = new HashSet<>();
        paths.fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(operation -> {
            String id = operation.getValue().path("operationId").asText();
            assertFalse(id.isBlank(), "operationId is required: " + path.getKey());
            assertTrue(operationIds.add(id), "duplicate operationId: " + id);
            assertTrue(operation.getValue().path("responses").isObject(),
                    "responses are required: " + id);
        }));
        JsonNode schemas = openApi.path("components").path("schemas");
        for (String schema : Set.of("GenerationRequest", "GenerationJob", "GenerationJobPage",
                "TemplateRevision", "Problem", "ValidationProblem")) {
            assertTrue(schemas.has(schema), "missing OpenAPI schema: " + schema);
        }
        assertTrue(openApi.toString().contains("GENERATION_QUOTA_EXCEEDED")
                        || openApi.path("paths").path("/v1/generation-jobs").path("post")
                        .path("responses").has("429"),
                "generation submission must describe quota rejection");
    }

    @Test
    void capabilitiesAdvertiseImplementedManagementAndProductionBoundaries() throws Exception {
        JsonNode capabilities = resource("/omni-service/1.0/capabilities.json");
        assertEquals("postgresql", capabilities.path("generationJob")
                .path("productionRepository").asText());
        assertTrue(capabilities.path("generationJob").path("stableCursorPagination").asBoolean());
        assertTrue(capabilities.path("generationJob").path("quotaAdmissionAtomicInPostgresql").asBoolean());
        assertTrue(capabilities.path("templateManagement").path("fourEyesApproval").asBoolean());
        assertEquals("s3-compatible", capabilities.path("artifactStorage").path("production").asText());
        assertFalse(capabilities.path("javaSdk").isEmpty());
    }

    private JsonNode resource(String name) throws Exception {
        try (InputStream input = getClass().getResourceAsStream(name)) {
            assertNotNull(input, "missing classpath resource: " + name);
            return mapper.readTree(input);
        }
    }
}
