package cn.bugstack.application.generation;

/** 统一生成任务支持的确定性输入与内部 AI 输入模式。 */
public enum GenerationMode {
    TEMPLATE_DATA,
    DOCUMENT_SPEC,
    AI_FREEFORM,
    AI_TEMPLATE
}
