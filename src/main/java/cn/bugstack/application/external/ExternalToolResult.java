package cn.bugstack.application.external;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 共享工具调用结果；协议适配器再把它投影为各自的响应格式。 */
public final class ExternalToolResult {

    private final JsonNode structuredContent;
    private final List<ExternalArtifactReference> artifacts;

    public ExternalToolResult(JsonNode structuredContent, List<ExternalArtifactReference> artifacts) {
        if (structuredContent == null || !structuredContent.isObject() || artifacts == null) {
            throw new IllegalArgumentException("external tool result requires object content and artifacts");
        }
        this.structuredContent = structuredContent.deepCopy();
        this.artifacts = Collections.unmodifiableList(new ArrayList<>(artifacts));
    }

    public static ExternalToolResult data(JsonNode structuredContent) {
        return new ExternalToolResult(structuredContent, Collections.emptyList());
    }

    public static ExternalToolResult artifact(JsonNode structuredContent, ExternalArtifactReference artifact) {
        return new ExternalToolResult(structuredContent, Collections.singletonList(artifact));
    }

    public JsonNode getStructuredContent() {
        return structuredContent.deepCopy();
    }

    public List<ExternalArtifactReference> getArtifacts() {
        return artifacts;
    }
}
