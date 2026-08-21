package cn.bugstack.application.generation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Generation Job 的持久化边界；生产实现可替换为 PostgreSQL。 */
public interface GenerationJobRepository {

    /**
     * 使用无限制配额创建任务。
     *
     * @param record 待持久化任务
     * @return 带持久化版本号的任务快照
     */
    default GenerationJobRecord create(GenerationJobRecord record) {
        return create(record, GenerationQuota.unlimited(), Instant.EPOCH);
    }

    /**
     * 在同一持久化临界区检查配额并创建任务，避免多实例并发穿透。
     *
     * @param record 待持久化任务
     * @param quota 当前租户配额
     * @param dayStart 当前 UTC 自然日的起始时刻
     * @return 带持久化版本号的任务快照
     * @throws GenerationQuotaExceededException 活动任务数或日创建数达到上限时抛出
     */
    GenerationJobRecord create(GenerationJobRecord record, GenerationQuota quota, Instant dayStart);

    /**
     * 使用乐观版本保存任务快照。
     *
     * @param record 待保存任务
     * @return 更新后的任务快照
     * @throws GenerationJobConflictException 持久化版本已经变化时抛出
     */
    GenerationJobRecord save(GenerationJobRecord record);

    /**
     * 原子领取一个排队任务，或租约已经过期的运行中任务。
     *
     * @param workerId Worker 唯一标识
     * @param now 领取时刻
     * @param leaseUntil 新租约截止时刻
     * @return 已领取任务；当前没有可执行任务时为空
     */
    Optional<GenerationJobRecord> claimNext(String workerId, Instant now, Instant leaseUntil);

    /**
     * 领取已经耗尽重试次数的排队任务或过期运行任务，用于可靠地提交失败终态。
     *
     * @param workerId Worker 唯一标识
     * @param now 领取时刻
     * @param leaseUntil 新租约截止时刻
     * @return 已领取任务；没有待恢复任务时为空
     */
    Optional<GenerationJobRecord> claimExhausted(String workerId, Instant now, Instant leaseUntil);

    /**
     * 仅允许当前租约持有者保存执行结果或释放任务。
     *
     * @param record 待保存任务
     * @param workerId 当前 Worker 标识
     * @return 更新后的任务快照
     * @throws GenerationJobConflictException Worker 不持有有效租约时抛出
     */
    GenerationJobRecord saveClaimed(GenerationJobRecord record, String workerId);

    /**
     * 仅允许当前租约持有者为仍在运行的任务续租。
     *
     * @param jobId 任务 ID
     * @param workerId 当前 Worker 标识
     * @param now 续租时刻
     * @param leaseUntil 新租约截止时刻
     * @return 续租成功返回 {@code true}
     */
    boolean renewLease(String jobId, String workerId, Instant now, Instant leaseUntil);

    /**
     * 查询任务。
     *
     * @param jobId 任务 ID
     * @return 任务快照；不存在时为空
     */
    Optional<GenerationJobRecord> find(String jobId);

    /**
     * 按调用主体和幂等键查询任务。
     *
     * @param principalId 调用主体 ID
     * @param idempotencyKey 幂等键
     * @return 已存在的任务；不存在时为空
     */
    Optional<GenerationJobRecord> findByIdempotencyKey(String principalId, String idempotencyKey);

    /**
     * 查询最新任务。
     *
     * @param limit 最大返回数量
     * @return 按创建时间和任务 ID 倒序排列的任务
     */
    default List<GenerationJobRecord> list(int limit) {
        return list(null, null, null, limit);
    }

    /**
     * 按创建时间和任务 ID 倒序稳定分页。
     *
     * @param status 可选状态过滤条件
     * @param beforeCreatedAt 游标中的创建时刻
     * @param beforeJobId 游标中的任务 ID
     * @param limit 最大返回数量
     * @return 满足条件的任务快照
     * @throws IllegalArgumentException 两个游标字段未同时提供时抛出
     */
    List<GenerationJobRecord> list(GenerationJobStatus status, Instant beforeCreatedAt,
                                   String beforeJobId, int limit);
}
