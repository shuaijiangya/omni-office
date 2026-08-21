package cn.bugstack.application.generation;

/** 可重试的租户任务配额拒绝。 */
public final class GenerationQuotaExceededException extends RuntimeException {

    private final String quota;

    /**
     * 创建配额超限异常。
     *
     * @param quota 被触发的配额名称
     * @param message 面向调用方的错误说明
     */
    public GenerationQuotaExceededException(String quota, String message) {
        super(message);
        this.quota = quota;
    }

    /** @return 被触发的配额名称 */
    public String getQuota() { return quota; }
}
