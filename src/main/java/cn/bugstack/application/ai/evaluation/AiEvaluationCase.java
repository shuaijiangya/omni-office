package cn.bugstack.application.ai.evaluation;

import cn.bugstack.application.ai.AiGenerationMode;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 可重复执行的内部 AI 文档评测用例。 */
public final class AiEvaluationCase {

    private final String id;
    private final AiGenerationMode mode;
    private final String templateId;
    private final String templateVersion;
    private final String instruction;
    private final JsonNode context;
    private final List<String> requiredJsonPointers;
    private final List<String> requiredText;
    private final int maximumAttempts;

    public AiEvaluationCase(String id, AiGenerationMode mode, String templateId, String templateVersion,
                            String instruction, JsonNode context, List<String> requiredJsonPointers,
                            List<String> requiredText, int maximumAttempts) {
        if (id == null || id.isBlank() || mode == null || instruction == null || instruction.isBlank()
                || maximumAttempts < 1) throw new IllegalArgumentException("invalid AI evaluation case");
        this.id = id;
        this.mode = mode;
        this.templateId = templateId;
        this.templateVersion = templateVersion;
        this.instruction = instruction;
        this.context = context == null ? null : context.deepCopy();
        this.requiredJsonPointers = immutable(requiredJsonPointers);
        this.requiredText = immutable(requiredText);
        this.maximumAttempts = maximumAttempts;
    }

    private List<String> immutable(List<String> values) {
        return values == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(values));
    }

    public String getId() { return id; }
    public AiGenerationMode getMode() { return mode; }
    public String getTemplateId() { return templateId; }
    public String getTemplateVersion() { return templateVersion; }
    public String getInstruction() { return instruction; }
    public JsonNode getContext() { return context == null ? null : context.deepCopy(); }
    public List<String> getRequiredJsonPointers() { return requiredJsonPointers; }
    public List<String> getRequiredText() { return requiredText; }
    public int getMaximumAttempts() { return maximumAttempts; }
}
