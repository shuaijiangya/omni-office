package cn.bugstack.application.ai;

import cn.bugstack.protocol.document.DocumentSpec;
import com.fasterxml.jackson.databind.JsonNode;

/** 可在导出前由业务方审查的 AI 结构化生成结果。 */
public final class AiDocumentResult {

    private final AiGenerationMode mode;
    private final DocumentSpec documentSpec;
    private final JsonNode templateData;
    private final String templateId;
    private final String templateVersion;
    private final int attempts;

    public AiDocumentResult(AiGenerationMode mode, DocumentSpec documentSpec, JsonNode templateData,
                            String templateId, String templateVersion, int attempts) {
        this.mode = mode;
        this.documentSpec = documentSpec;
        this.templateData = templateData == null ? null : templateData.deepCopy();
        this.templateId = templateId;
        this.templateVersion = templateVersion;
        this.attempts = attempts;
    }

    public AiGenerationMode getMode() {
        return mode;
    }

    public DocumentSpec getDocumentSpec() {
        return documentSpec;
    }

    public JsonNode getTemplateData() {
        return templateData == null ? null : templateData.deepCopy();
    }

    public String getTemplateId() {
        return templateId;
    }

    public String getTemplateVersion() {
        return templateVersion;
    }

    public int getAttempts() {
        return attempts;
    }
}
