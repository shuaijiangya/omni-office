package cn.bugstack.application.external.function;

import cn.bugstack.application.external.ExternalDocumentToolApplication;
import cn.bugstack.application.external.ExternalToolDefinition;
import cn.bugstack.application.external.ExternalToolResult;
import cn.bugstack.application.external.ResolvedExternalArtifact;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;

/**
 * 模型厂商无关的 JSON Function Calling 适配器。
 *
 * <p>工具描述采用常见的 {@code type=function/function.parameters} 结构；模型网关负责
 * tool_call_id 等会话关联，本适配器只处理稳定工具名、JSON 参数和结构化结果。</p>
 */
public final class FunctionCallingDocumentAdapter {

    private final ExternalDocumentToolApplication application;
    private final ObjectMapper mapper;

    public FunctionCallingDocumentAdapter(ExternalDocumentToolApplication application) {
        this(application, new ObjectMapper());
    }

    FunctionCallingDocumentAdapter(ExternalDocumentToolApplication application, ObjectMapper mapper) {
        if (application == null || mapper == null) {
            throw new IllegalArgumentException("function calling application and mapper are required");
        }
        this.application = application;
        this.mapper = mapper.copy();
    }

    public ArrayNode listFunctionTools() {
        ArrayNode tools = mapper.createArrayNode();
        for (ExternalToolDefinition definition : application.listTools()) {
            ObjectNode tool = tools.addObject();
            tool.put("type", "function");
            ObjectNode function = tool.putObject("function");
            function.put("name", definition.getName());
            function.put("description", definition.getDescription());
            function.set("parameters", definition.getInputSchema());
        }
        return tools;
    }

    public String listFunctionToolsJson() {
        return writeJson(listFunctionTools());
    }

    public ExternalToolResult invoke(String functionName, JsonNode arguments) {
        return application.call(functionName, arguments);
    }

    public String invoke(String functionName, String argumentsJson) {
        if (argumentsJson == null || argumentsJson.trim().isEmpty()) {
            throw new IllegalArgumentException("function arguments JSON must not be blank");
        }
        try {
            JsonNode arguments = mapper.readTree(argumentsJson);
            if (arguments == null || !arguments.isObject()) {
                throw new IllegalArgumentException("function arguments JSON root must be an object");
            }
            return writeJson(invoke(functionName, arguments).getStructuredContent());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("invalid function arguments JSON: " + e.getOriginalMessage(), e);
        }
    }

    /** 供承载 Function Calling 的业务网关把资源 URI 转换为下载响应。 */
    public byte[] readResource(String resourceUri) {
        ResolvedExternalArtifact artifact = application.readResource(resourceUri);
        try {
            return Files.readAllBytes(artifact.getContentPath());
        } catch (IOException e) {
            throw new IllegalStateException("failed to read generated function artifact", e);
        }
    }

    private String writeJson(JsonNode value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize function calling JSON", e);
        }
    }
}
