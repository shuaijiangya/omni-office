package cn.bugstack.application.external.mcp;

import cn.bugstack.application.external.ExternalArtifactReference;
import cn.bugstack.application.external.ExternalDocumentToolApplication;
import cn.bugstack.application.external.ExternalToolDefinition;
import cn.bugstack.application.external.ExternalToolResult;
import cn.bugstack.application.external.ResolvedExternalArtifact;
import cn.bugstack.application.external.UnknownExternalToolException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

/** MCP 2025-11-25/2025-06-18 JSON-RPC 核心与官方换行分隔 stdio 传输。 */
public final class McpJsonRpcServer implements AutoCloseable {

    public static final String LATEST_PROTOCOL_VERSION = "2025-11-25";
    private static final int PARSE_ERROR = -32700;
    private static final int INVALID_REQUEST = -32600;
    private static final int METHOD_NOT_FOUND = -32601;
    private static final int INVALID_PARAMS = -32602;
    private static final int INTERNAL_ERROR = -32603;
    private static final int RESOURCE_NOT_FOUND = -32002;
    private static final int NOT_INITIALIZED = -32010;
    private final ExternalDocumentToolApplication application;
    private final ObjectMapper mapper;
    private final Set<String> supportedVersions = new HashSet<>();
    private final McpTaskManager taskManager;
    private final boolean authenticatedContext;
    private final String principalId;
    private final boolean allowAnyArtifacts;
    private boolean initializeResponded;
    private boolean initialized;
    private String negotiatedVersion;

    public McpJsonRpcServer(ExternalDocumentToolApplication application) {
        this(application, false);
    }

    public McpJsonRpcServer(ExternalDocumentToolApplication application, boolean authenticatedContext) {
        this(application, new ObjectMapper(), authenticatedContext, "system", false);
    }

    /**
     * 创建绑定认证主体的 MCP 会话，工具生成和资源读取都会执行主体级工件授权。
     *
     * @param application 外部工具应用
     * @param authenticatedContext 是否为认证上下文
     * @param principalId 当前主体 ID
     * @param allowAnyArtifacts 是否允许读取租户内任意主体工件
     */
    public McpJsonRpcServer(ExternalDocumentToolApplication application, boolean authenticatedContext,
                            String principalId, boolean allowAnyArtifacts) {
        this(application, new ObjectMapper(), authenticatedContext, principalId, allowAnyArtifacts);
    }

    public synchronized String getNegotiatedVersion() {
        return negotiatedVersion;
    }

    public synchronized boolean isInitializeResponded() {
        return initializeResponded;
    }

    McpJsonRpcServer(ExternalDocumentToolApplication application, ObjectMapper mapper) {
        this(application, mapper, false);
    }

    McpJsonRpcServer(ExternalDocumentToolApplication application, ObjectMapper mapper,
                     boolean authenticatedContext) {
        this(application, mapper, authenticatedContext, "system", false);
    }

    McpJsonRpcServer(ExternalDocumentToolApplication application, ObjectMapper mapper,
                     boolean authenticatedContext, String principalId, boolean allowAnyArtifacts) {
        if (application == null || mapper == null) {
            throw new IllegalArgumentException("MCP application and mapper are required");
        }
        if (principalId == null || !principalId.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("MCP principal id is invalid");
        }
        this.application = application;
        this.mapper = mapper.copy();
        this.taskManager = new McpTaskManager(this.mapper);
        this.authenticatedContext = authenticatedContext;
        this.principalId = principalId;
        this.allowAnyArtifacts = allowAnyArtifacts;
        supportedVersions.add(LATEST_PROTOCOL_VERSION);
        supportedVersions.add("2025-06-18");
    }

    /** 处理一个已解析 JSON-RPC 消息；通知返回 {@code null}。 */
    public synchronized ObjectNode handle(JsonNode message) {
        if (message == null || !message.isObject() || !"2.0".equals(message.path("jsonrpc").asText())
                || !message.path("method").isTextual()) {
            return error(idOf(message), INVALID_REQUEST, "Invalid JSON-RPC request", null);
        }
        JsonNode id = idOf(message);
        boolean notification = !message.has("id");
        String method = message.path("method").asText();
        if (notification) {
            handleNotification(method);
            return null;
        }
        try {
            if ("initialize".equals(method)) {
                return success(id, initialize(message.path("params")));
            }
            if ("ping".equals(method)) {
                return success(id, mapper.createObjectNode());
            }
            requireInitialized();
            switch (method) {
                case "tools/list":
                    return success(id, listTools());
                case "tools/call":
                    return callTool(id, message.path("params"));
                case "tasks/get":
                    return taskGet(id, message.path("params"));
                case "tasks/result":
                    return taskResult(id, message.path("params"));
                case "tasks/list":
                    return taskList(id);
                case "tasks/cancel":
                    return taskCancel(id, message.path("params"));
                case "resources/list":
                    return success(id, mapper.createObjectNode().set("resources", mapper.createArrayNode()));
                case "resources/templates/list":
                    return success(id, resourceTemplates());
                case "resources/read":
                    return readResource(id, message.path("params"));
                default:
                    return error(id, METHOD_NOT_FOUND, "Method not found: " + method, null);
            }
        } catch (McpProtocolException e) {
            return error(id, e.code, e.getMessage(), e.data);
        } catch (RuntimeException e) {
            return error(id, INTERNAL_ERROR, "Internal MCP server error", null);
        }
    }

    /** 按官方 stdio 传输逐行读取和写入 UTF-8 JSON-RPC 消息。 */
    public void run(InputStream input, OutputStream output, PrintStream errorOutput) throws IOException {
        if (input == null || output == null || errorOutput == null) {
            throw new IllegalArgumentException("MCP stdio streams must not be null");
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8), true);
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) {
                continue;
            }
            ObjectNode response;
            try {
                response = handle(mapper.readTree(line));
            } catch (JsonProcessingException e) {
                response = error(NullNode.getInstance(), PARSE_ERROR, "Parse error", null);
            } catch (RuntimeException e) {
                errorOutput.println("MCP message handling failed: " + e.getMessage());
                response = error(NullNode.getInstance(), INTERNAL_ERROR, "Internal MCP server error", null);
            }
            if (response != null) {
                writer.println(mapper.writeValueAsString(response));
            }
        }
    }

    private JsonNode initialize(JsonNode params) {
        if (initializeResponded) {
            throw new McpProtocolException(INVALID_REQUEST, "MCP session is already initialized", null);
        }
        if (!params.isObject() || !params.path("protocolVersion").isTextual()
                || !params.path("clientInfo").isObject() || !params.path("capabilities").isObject()) {
            throw new McpProtocolException(INVALID_PARAMS, "initialize requires protocolVersion, capabilities and clientInfo", null);
        }
        String requested = params.path("protocolVersion").asText();
        negotiatedVersion = supportedVersions.contains(requested) ? requested : LATEST_PROTOCOL_VERSION;
        initializeResponded = true;
        ObjectNode result = mapper.createObjectNode();
        result.put("protocolVersion", negotiatedVersion);
        ObjectNode capabilities = result.putObject("capabilities");
        capabilities.putObject("tools").put("listChanged", false);
        capabilities.putObject("resources").put("subscribe", false).put("listChanged", false);
        if (LATEST_PROTOCOL_VERSION.equals(negotiatedVersion)) {
            ObjectNode tasks = capabilities.putObject("tasks");
            if (authenticatedContext) {
                tasks.putObject("list");
            }
            tasks.putObject("cancel");
            tasks.putObject("requests").putObject("tools").putObject("call");
        }
        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", "cn.bugstack.omni-office");
        serverInfo.put("title", "Omni Office Document Server");
        serverInfo.put("version", "1.0.0");
        serverInfo.put("description", "Generate validated Word, PDF and Visio artifacts from versioned schemas.");
        result.put("instructions", "Choose exactly one explicit template version or provide DocumentSpec 1.0. "
                + "Generated files are returned as omni-office resource links and never as server paths.");
        return result;
    }

    private void handleNotification(String method) {
        if ("notifications/initialized".equals(method) && initializeResponded) {
            initialized = true;
        }
    }

    private JsonNode listTools() {
        ArrayNode values = mapper.createArrayNode();
        for (ExternalToolDefinition definition : application.listTools()) {
            ObjectNode value = values.addObject();
            value.put("name", definition.getName());
            value.put("title", definition.getTitle());
            value.put("description", definition.getDescription());
            value.set("inputSchema", definition.getInputSchema());
            value.set("outputSchema", definition.getOutputSchema());
            value.set("annotations", definition.getAnnotations());
            if (LATEST_PROTOCOL_VERSION.equals(negotiatedVersion)) {
                value.putObject("execution").put("taskSupport", "optional");
            }
        }
        return mapper.createObjectNode().set("tools", values);
    }

    private ObjectNode callTool(JsonNode id, JsonNode params) {
        if (!params.isObject() || !params.path("name").isTextual()) {
            return error(id, INVALID_PARAMS, "tools/call requires a tool name", null);
        }
        if (params.has("arguments") && !params.path("arguments").isObject()) {
            return error(id, INVALID_PARAMS, "tools/call arguments must be an object", null);
        }
        String requestedName = params.path("name").asText();
        boolean knownTool = application.listTools().stream()
                .anyMatch(tool -> tool.getName().equals(requestedName));
        if (!knownTool) {
            return error(id, INVALID_PARAMS, "Unknown external document tool: " + requestedName, null);
        }
        if (params.has("task") && LATEST_PROTOCOL_VERSION.equals(negotiatedVersion)) {
            if (!params.path("task").isObject()) {
                return error(id, INVALID_PARAMS, "tools/call task must be an object", null);
            }
            long ttl = params.path("task").path("ttl").asLong(0);
            ObjectNode copied = (ObjectNode) params.deepCopy();
            copied.remove("task");
            try {
                return success(id, taskManager.submit(() -> performToolCall(copied), ttl));
            } catch (IllegalArgumentException e) {
                return error(id, INVALID_PARAMS, e.getMessage(), null);
            }
        }
        return success(id, performToolCall(params));
    }

    private ObjectNode performToolCall(JsonNode params) {
        String name = params.path("name").asText();
        JsonNode arguments = params.has("arguments") ? params.path("arguments") : mapper.createObjectNode();
        final ExternalToolResult toolResult;
        try {
            toolResult = application.call(name, arguments, principalId);
        } catch (UnknownExternalToolException e) {
            throw e;
        } catch (RuntimeException e) {
            ObjectNode result = mapper.createObjectNode();
            result.put("isError", true);
            result.putArray("content").addObject().put("type", "text").put("text", safeMessage(e));
            return result;
        }
        ObjectNode result = mapper.createObjectNode();
        result.set("structuredContent", toolResult.getStructuredContent());
        ArrayNode content = result.putArray("content");
        content.addObject().put("type", "text")
                .put("text", compactJson(toolResult.getStructuredContent()));
        for (ExternalArtifactReference artifact : toolResult.getArtifacts()) {
            ObjectNode link = content.addObject();
            link.put("type", "resource_link");
            link.put("uri", artifact.getResourceUri());
            link.put("name", artifact.getFileName());
            link.put("title", artifact.getFileName());
            link.put("description", "Generated Omni Office artifact");
            link.put("mimeType", artifact.getMediaType());
            link.put("size", artifact.getSize());
        }
        result.put("isError", false);
        return result;
    }

    private ObjectNode taskGet(JsonNode id, JsonNode params) {
        if (!LATEST_PROTOCOL_VERSION.equals(negotiatedVersion)) {
            return error(id, METHOD_NOT_FOUND, "MCP Tasks require protocol 2025-11-25", null);
        }
        try {
            return success(id, taskManager.get(requiredTaskId(params)));
        } catch (IllegalArgumentException e) {
            return error(id, INVALID_PARAMS, e.getMessage(), null);
        }
    }

    private ObjectNode taskResult(JsonNode id, JsonNode params) {
        if (!LATEST_PROTOCOL_VERSION.equals(negotiatedVersion)) {
            return error(id, METHOD_NOT_FOUND, "MCP Tasks require protocol 2025-11-25", null);
        }
        try {
            return success(id, taskManager.result(requiredTaskId(params)));
        } catch (IllegalArgumentException e) {
            return error(id, INVALID_PARAMS, e.getMessage(), null);
        }
    }

    private ObjectNode taskList(JsonNode id) {
        if (!LATEST_PROTOCOL_VERSION.equals(negotiatedVersion)) {
            return error(id, METHOD_NOT_FOUND, "MCP Tasks require protocol 2025-11-25", null);
        }
        try {
            return success(id, taskManager.list(authenticatedContext));
        } catch (UnsupportedOperationException e) {
            return error(id, METHOD_NOT_FOUND, e.getMessage(), null);
        }
    }

    private ObjectNode taskCancel(JsonNode id, JsonNode params) {
        if (!LATEST_PROTOCOL_VERSION.equals(negotiatedVersion)) {
            return error(id, METHOD_NOT_FOUND, "MCP Tasks require protocol 2025-11-25", null);
        }
        try {
            return success(id, taskManager.cancel(requiredTaskId(params)));
        } catch (IllegalArgumentException e) {
            return error(id, INVALID_PARAMS, e.getMessage(), null);
        }
    }

    private String requiredTaskId(JsonNode params) {
        if (!params.isObject() || !params.path("taskId").isTextual()
                || params.path("taskId").asText().isBlank()) {
            throw new IllegalArgumentException("taskId is required");
        }
        return params.path("taskId").asText();
    }

    private JsonNode resourceTemplates() {
        ObjectNode result = mapper.createObjectNode();
        ObjectNode template = result.putArray("resourceTemplates").addObject();
        template.put("uriTemplate", "omni-office://artifacts/{artifactId}");
        template.put("name", "generated-artifact");
        template.put("title", "Generated Omni Office artifact");
        template.put("description", "Binary DOCX, PDF, VSDX or PNG generated by an Omni Office tool call.");
        return result;
    }

    private ObjectNode readResource(JsonNode id, JsonNode params) {
        if (!params.isObject() || !params.path("uri").isTextual()) {
            return error(id, INVALID_PARAMS, "resources/read requires a URI", null);
        }
        String uri = params.path("uri").asText();
        final ResolvedExternalArtifact artifact;
        try {
            artifact = application.readResource(uri, principalId, allowAnyArtifacts);
        } catch (RuntimeException e) {
            ObjectNode data = mapper.createObjectNode().put("uri", uri);
            return error(id, RESOURCE_NOT_FOUND, "Resource not found", data);
        }
        try {
            byte[] bytes = Files.readAllBytes(artifact.getContentPath());
            ObjectNode result = mapper.createObjectNode();
            ObjectNode content = result.putArray("contents").addObject();
            content.put("uri", artifact.getReference().getResourceUri());
            content.put("mimeType", artifact.getReference().getMediaType());
            content.put("blob", Base64.getEncoder().encodeToString(bytes));
            return success(id, result);
        } catch (IOException e) {
            return error(id, INTERNAL_ERROR, "Failed to read resource", null);
        }
    }

    private void requireInitialized() {
        if (!initializeResponded || !initialized) {
            throw new McpProtocolException(NOT_INITIALIZED,
                    "MCP session requires initialize and notifications/initialized", null);
        }
    }

    private ObjectNode success(JsonNode id, JsonNode result) {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id == null ? NullNode.getInstance() : id.deepCopy());
        response.set("result", result);
        return response;
    }

    private ObjectNode error(JsonNode id, int code, String message, JsonNode data) {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id == null ? NullNode.getInstance() : id.deepCopy());
        ObjectNode error = response.putObject("error");
        error.put("code", code);
        error.put("message", message);
        if (data != null) {
            error.set("data", data);
        }
        return response;
    }

    private JsonNode idOf(JsonNode message) {
        return message != null && message.has("id") ? message.path("id") : NullNode.getInstance();
    }

    private String compactJson(JsonNode value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize MCP tool result", e);
        }
    }

    private String safeMessage(RuntimeException error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "Tool execution failed";
        }
        return message.length() <= 2_000 ? message : message.substring(0, 2_000);
    }

    @Override
    public void close() {
        taskManager.close();
    }

    private static final class McpProtocolException extends RuntimeException {

        private final int code;
        private final JsonNode data;

        private McpProtocolException(int code, String message, JsonNode data) {
            super(message);
            this.code = code;
            this.data = data;
        }
    }
}
