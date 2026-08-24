package cn.bugstack.application.generation.webhook;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Map;

/** Webhook Outbox 的持久化边界，支持幂等入队、租约领取和投递审计。 */
public interface WebhookDeliveryRepository {

    /**
     * 幂等写入投递事件。
     *
     * @param record 待入队事件
     * @return 持久化后的事件快照
     */
    WebhookDeliveryRecord enqueue(WebhookDeliveryRecord record);

    /**
     * 使用乐观版本保存事件。
     *
     * @param record 待保存事件
     * @return 更新后的事件快照
     */
    WebhookDeliveryRecord save(WebhookDeliveryRecord record);

    /**
     * 按租户、任务和事件类型查询幂等事件。
     *
     * @param tenantId 租户 ID
     * @param jobId 任务 ID
     * @param eventType 事件类型
     * @return 已存在事件；不存在时为空
     */
    Optional<WebhookDeliveryRecord> findByEventKey(String tenantId, String jobId, String eventType);

    /**
     * 原子领取到期且可重试的投递事件。
     *
     * @param workerId 投递 Worker 标识
     * @param now 领取时刻
     * @param leaseUntil 租约截止时刻
     * @param limit 最大领取数量
     * @return 已领取事件
     */
    List<WebhookDeliveryRecord> claimDue(String workerId, Instant now, Instant leaseUntil, int limit);

    /**
     * 仅由有效租约持有者保存投递结果。
     *
     * @param record 待保存事件
     * @param workerId 当前 Worker 标识
     * @param now 保存时刻
     * @return 更新后的事件快照
     */
    WebhookDeliveryRecord saveClaimed(WebhookDeliveryRecord record, String workerId, Instant now);

    /**
     * 查询租户投递记录。
     *
     * @param tenantId 租户 ID
     * @param limit 最大返回数量
     * @return 按创建时间倒序排列的事件
     */
    List<WebhookDeliveryRecord> list(String tenantId, int limit);

    /**
     * 统计所有租户的投递状态，供无租户标签的服务指标使用。
     *
     * @return 包含所有状态的数量映射
     */
    Map<WebhookDeliveryStatus, Long> countsByStatus();

    /**
     * 统计指定租户的投递状态。
     *
     * @param tenantId 租户 ID
     * @return 包含所有状态的数量映射
     */
    default Map<WebhookDeliveryStatus, Long> countsByStatus(String tenantId) {
        Map<WebhookDeliveryStatus, Long> result = new java.util.EnumMap<>(WebhookDeliveryStatus.class);
        for (WebhookDeliveryStatus status : WebhookDeliveryStatus.values()) result.put(status, 0L);
        list(tenantId, Integer.MAX_VALUE).forEach(item ->
                result.put(item.getStatus(), result.get(item.getStatus()) + 1L));
        return result;
    }

    /**
     * 将 DEAD 事件重新加入投递队列，并在保留累计尝试次数的前提下增加重试预算。
     *
     * @param tenantId 租户 ID
     * @param eventId 事件 ID
     * @param now 重放时间
     * @param additionalAttempts 新增重试次数
     * @return 重放后的事件
     */
    WebhookDeliveryRecord redrive(String tenantId, String eventId, Instant now, int additionalAttempts);

    /**
     * 分批清理截止时间之前的 DELIVERED/DEAD 记录。
     *
     * @param cutoff 截止时间
     * @param limit 单批最大删除数
     * @return 删除数量
     */
    int purgeTerminalBefore(Instant cutoff, int limit);
}
