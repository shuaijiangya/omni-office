package cn.bugstack.application.external.mcp;

import cn.bugstack.application.external.ExternalDocumentToolApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpJsonRpcServerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void enforcesLifecycleAndPublishesSharedTools() throws Exception {
        McpJsonRpcServer server = server("mcp-lifecycle");
        ObjectNode initialize = request(1, "initialize");
        ObjectNode params = initialize.putObject("params");
        params.put("protocolVersion", "2025-11-25");
        params.set("capabilities", mapper.createObjectNode());
        params.putObject("clientInfo").put("name", "test").put("version", "1.0");

        JsonNode initialized = server.handle(initialize);
        assertEquals("2025-11-25", initialized.path("result").path("protocolVersion").asText());
        assertEquals(-32010, server.handle(request(2, "tools/list")).path("error").path("code").asInt());

        ObjectNode notification = mapper.createObjectNode();
        notification.put("jsonrpc", "2.0");
        notification.put("method", "notifications/initialized");
        assertNull(server.handle(notification));
        assertEquals(5, server.handle(request(3, "tools/list"))
                .path("result").path("tools").size());
    }

    @Test
    void callsDocumentToolAndReadsBinaryResource() throws Exception {
        McpJsonRpcServer server = initializedServer("mcp-document-call");
        ObjectNode arguments;
        try (InputStream input = getClass().getResourceAsStream(
                "/document-spec/1.0/example-simple.json")) {
            assertNotNull(input);
            arguments = (ObjectNode) mapper.readTree(input);
        }
        arguments.put("outputFormat", "DOCX");
        ObjectNode call = request(10, "tools/call");
        call.putObject("params").put("name", ExternalDocumentToolApplication.EXPORT_DOCUMENT)
                .set("arguments", arguments);

        JsonNode result = server.handle(call).path("result");
        assertEquals(false, result.path("isError").asBoolean());
        assertEquals("resource_link", result.path("content").get(1).path("type").asText());
        String uri = result.path("structuredContent").path("artifact").path("resourceUri").asText();

        ObjectNode read = request(11, "resources/read");
        read.putObject("params").put("uri", uri);
        byte[] bytes = Base64.getDecoder().decode(server.handle(read).path("result")
                .path("contents").get(0).path("blob").asText());
        assertEquals((byte) 'P', bytes[0]);
        assertEquals((byte) 'K', bytes[1]);
    }

    @Test
    void returnsToolErrorsInsideResultAndUnknownToolsAsProtocolErrors() throws Exception {
        McpJsonRpcServer server = initializedServer("mcp-tool-errors");
        ObjectNode invalidCall = request(20, "tools/call");
        invalidCall.putObject("params").put("name", ExternalDocumentToolApplication.EXPORT_DOCUMENT)
                .set("arguments", mapper.createObjectNode());
        assertTrue(server.handle(invalidCall).path("result").path("isError").asBoolean());

        ObjectNode unknownCall = request(21, "tools/call");
        unknownCall.putObject("params").put("name", "missing")
                .set("arguments", mapper.createObjectNode());
        assertEquals(-32602, server.handle(unknownCall).path("error").path("code").asInt());
    }

    @Test
    void stdioUsesOneJsonMessagePerLineAndDoesNotRespondToNotifications() throws Exception {
        McpJsonRpcServer server = server("mcp-stdio");
        String initialize = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
                + "\"params\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},"
                + "\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}";
        String input = initialize + "\n"
                + "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}\n"
                + "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"ping\"}\n";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        server.run(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), output,
                new PrintStream(new ByteArrayOutputStream()));

        String[] lines = output.toString(StandardCharsets.UTF_8).trim().split("\\R");
        assertEquals(2, lines.length);
        assertEquals(1, mapper.readTree(lines[0]).path("id").asInt());
        assertEquals(2, mapper.readTree(lines[1]).path("id").asInt());
    }

    @Test
    void supportsAuthenticatedAsynchronousTaskLifecycle() throws Exception {
        McpJsonRpcServer server = authenticatedInitializedServer("mcp-task");
        ObjectNode arguments;
        try (InputStream input = getClass().getResourceAsStream(
                "/document-spec/1.0/example-simple.json")) {
            arguments = (ObjectNode) mapper.readTree(input);
        }
        arguments.put("outputFormat", "DOCX");
        ObjectNode call = request(30, "tools/call");
        ObjectNode params = call.putObject("params");
        params.put("name", ExternalDocumentToolApplication.EXPORT_DOCUMENT);
        params.set("arguments", arguments);
        params.putObject("task").put("ttl", 60_000);
        String taskId = server.handle(call).path("result").path("task").path("taskId").asText();
        assertTrue(!taskId.isBlank());

        ObjectNode result = request(31, "tasks/result");
        result.putObject("params").put("taskId", taskId);
        JsonNode completed = server.handle(result).path("result");
        assertEquals(false, completed.path("isError").asBoolean());
        assertEquals(taskId, completed.path("_meta").path("io.modelcontextprotocol/related-task")
                .path("taskId").asText());
        assertEquals(1, server.handle(request(32, "tasks/list")).path("result").path("tasks").size());

        ObjectNode cancel = request(33, "tasks/cancel");
        cancel.putObject("params").put("taskId", taskId);
        assertEquals(-32602, server.handle(cancel).path("error").path("code").asInt());
    }

    private McpJsonRpcServer initializedServer(String prefix) throws Exception {
        McpJsonRpcServer server = server(prefix);
        ObjectNode initialize = request(1, "initialize");
        ObjectNode params = initialize.putObject("params");
        params.put("protocolVersion", "2025-11-25");
        params.set("capabilities", mapper.createObjectNode());
        params.putObject("clientInfo").put("name", "test").put("version", "1.0");
        server.handle(initialize);
        ObjectNode notification = mapper.createObjectNode();
        notification.put("jsonrpc", "2.0");
        notification.put("method", "notifications/initialized");
        server.handle(notification);
        return server;
    }

    private McpJsonRpcServer authenticatedInitializedServer(String prefix) throws Exception {
        McpJsonRpcServer server = new McpJsonRpcServer(new ExternalDocumentToolApplication(
                Files.createTempDirectory(prefix)), true);
        ObjectNode initialize = request(1, "initialize");
        ObjectNode params = initialize.putObject("params");
        params.put("protocolVersion", "2025-11-25");
        params.set("capabilities", mapper.createObjectNode());
        params.putObject("clientInfo").put("name", "test").put("version", "1.0");
        server.handle(initialize);
        ObjectNode notification = mapper.createObjectNode();
        notification.put("jsonrpc", "2.0");
        notification.put("method", "notifications/initialized");
        server.handle(notification);
        return server;
    }

    private McpJsonRpcServer server(String prefix) throws Exception {
        return new McpJsonRpcServer(new ExternalDocumentToolApplication(
                Files.createTempDirectory(prefix)));
    }

    private ObjectNode request(int id, String method) {
        ObjectNode request = mapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        return request;
    }
}
