package cn.bugstack.application.generation;

/** 可持久化的文档生成阶段，用于进度展示、超时治理和错误定位。 */
public enum GenerationStage {
    QUEUED,
    AI_GENERATION,
    AI_REVIEW,
    TEMPLATE_ASSEMBLY,
    DOCUMENT_VALIDATION,
    DIAGRAM_GENERATION,
    RENDERING,
    SECURITY_SCAN,
    ARTIFACT_STORAGE,
    COMPLETED
}
