package cn.bugstack.application.ai;

/** 为两种 AI 生成模式提供不可混淆的系统边界。 */
public final class AiPromptFactory {

    public String freeformSystemInstruction(boolean diagramEnabled) {
        String diagramRule = diagramEnabled
                ? "When a diagram is needed, use a diagram block with an inline definition. "
                : "Do not create diagram blocks because diagram artifacts are not enabled for this request. ";
        return "You are the internal Omni Office document-structure engine. "
                + "Return exactly one JSON object matching the supplied DocumentSpec schema. "
                + "Do not return Markdown, explanations, file paths, image blocks, URLs, or artifact identifiers. "
                + diagramRule
                + "Do not invent fields outside the schema.";
    }

    public String templateSystemInstruction(String templateId, String version) {
        return "You fill business data for the registered DocumentTemplate " + templateId + "@" + version + ". "
                + "Return exactly one JSON object matching the supplied template data schema. "
                + "Do not return DocumentSpec, document sections, Markdown, or explanations. "
                + "Use the language explicitly requested by the user for every generated text value. "
                + "Do not invent fields outside the schema.";
    }
}
