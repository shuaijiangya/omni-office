package cn.bugstack.application.generation;

/** 生成任务终态事件发布边界；实现必须保证同一任务终态事件幂等入队。 */
public interface GenerationEventPublisher {

    /**
     * 校验调用方引用的 Webhook 是否已由管理员预注册。
     *
     * @param tenantId 租户 ID
     * @param webhookId Webhook 配置 ID；可为 {@code null}
     */
    void validateWebhook(String tenantId, String webhookId);

    /**
     * 幂等写入任务终态事件。
     *
     * @param job 已进入终态的任务
     * @return 事件 ID；任务未配置 Webhook 时返回 {@code null}
     */
    String enqueueTerminal(GenerationJobRecord job);

    /**
     * 提交 Worker 持有租约的任务终态。
     *
     * <p>默认实现只保存任务，调用方随后用可恢复逻辑入队；生产实现可覆盖为数据库单事务。</p>
     *
     * @param jobs 任务仓储
     * @param job 终态任务
     * @param workerId 当前 Worker 标识
     * @return 持久化后的任务快照
     */
    default GenerationJobRecord commitClaimedTerminal(GenerationJobRepository jobs,
                                                       GenerationJobRecord job, String workerId) {
        return jobs.saveClaimed(job, workerId);
    }

    /**
     * 提交调用方取消等非 Worker 终态。
     *
     * @param jobs 任务仓储
     * @param job 终态任务
     * @return 持久化后的任务快照
     */
    default GenerationJobRecord commitTerminal(GenerationJobRepository jobs,
                                                GenerationJobRecord job) {
        return jobs.save(job);
    }
}
