package cn.bugstack.application.external;

import com.fasterxml.jackson.databind.JsonNode;

/** Function Calling 与 MCP 共用的工具定义。 */
public final class ExternalToolDefinition {

    private final String name;
    private final String title;
    private final String description;
    private final JsonNode inputSchema;
    private final JsonNode outputSchema;
    private final JsonNode annotations;

    public ExternalToolDefinition(String name, String title, String description,
                                  JsonNode inputSchema, JsonNode outputSchema, JsonNode annotations) {
        if (name == null || title == null || description == null || inputSchema == null
                || outputSchema == null || annotations == null) {
            throw new IllegalArgumentException("external tool definition fields must not be null");
        }
        this.name = name;
        this.title = title;
        this.description = description;
        this.inputSchema = inputSchema.deepCopy();
        this.outputSchema = outputSchema.deepCopy();
        this.annotations = annotations.deepCopy();
    }

    public String getName() {
        return name;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public JsonNode getInputSchema() {
        return inputSchema.deepCopy();
    }

    public JsonNode getOutputSchema() {
        return outputSchema.deepCopy();
    }

    public JsonNode getAnnotations() {
        return annotations.deepCopy();
    }
}
