package cn.bugstack.application.ai;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 发送给内部结构化生成模型的供应商无关请求。 */
public final class StructuredAiRequest {

    private final String operation;
    private final String systemInstruction;
    private final String userInstruction;
    private final JsonNode context;
    private final JsonNode outputSchema;
    private final int attempt;
    private final List<String> validationFeedback;

    public StructuredAiRequest(String operation, String systemInstruction, String userInstruction,
                               JsonNode context, JsonNode outputSchema, int attempt,
                               List<String> validationFeedback) {
        this.operation = operation;
        this.systemInstruction = systemInstruction;
        this.userInstruction = userInstruction;
        this.context = context == null ? null : context.deepCopy();
        this.outputSchema = outputSchema == null ? null : outputSchema.deepCopy();
        this.attempt = attempt;
        this.validationFeedback = validationFeedback == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(validationFeedback));
    }

    public String getOperation() {
        return operation;
    }

    public String getSystemInstruction() {
        return systemInstruction;
    }

    public String getUserInstruction() {
        return userInstruction;
    }

    public JsonNode getContext() {
        return context == null ? null : context.deepCopy();
    }

    public JsonNode getOutputSchema() {
        return outputSchema == null ? null : outputSchema.deepCopy();
    }

    public int getAttempt() {
        return attempt;
    }

    public List<String> getValidationFeedback() {
        return validationFeedback;
    }
}
