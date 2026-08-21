package cn.bugstack.application.template.governance;

/** 模板版本已存在、状态不允许或违反四眼审核时的业务冲突。 */
public final class TemplateWorkflowConflictException extends IllegalStateException {

    /**
     * 创建模板工作流冲突异常。
     *
     * @param message 冲突原因
     */
    public TemplateWorkflowConflictException(String message) { super(message); }
}
