package cn.bugstack.application.completion;

/** LLM 表格补全规划状态。 */
public enum TableCompletionStatus {
    /** 已生成可校验的表格绑定和补丁计划。 */
    READY,
    /** 文档或业务上下文不足，需要用户或工具补充信息。 */
    NEEDS_INPUT
}
