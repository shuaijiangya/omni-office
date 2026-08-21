package cn.bugstack.application.generation;

/** 可持久化生成任务状态。 */
public enum GenerationJobStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    /**
     * 判断当前状态是否已经不可继续执行。
     *
     * @return 成功、失败或取消状态返回 {@code true}
     */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }
}
