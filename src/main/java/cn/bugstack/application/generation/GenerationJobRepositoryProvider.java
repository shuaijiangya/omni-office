package cn.bugstack.application.generation;

import cn.bugstack.application.generation.webhook.WebhookDeliveryRepository;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/** 按租户提供任务仓储，并暴露生产依赖的就绪状态。 */
public interface GenerationJobRepositoryProvider extends AutoCloseable {

    /**
     * 获取指定租户的任务仓储。
     *
     * @param tenantId 租户 ID
     * @return 租户隔离的任务仓储
     */
    GenerationJobRepository repository(String tenantId);

    /**
     * 检查任务持久化依赖是否可用。
     *
     * @return 可接受生产流量时返回 {@code true}
     */
    boolean isReady();

    /**
     * 获取 Webhook Outbox 仓储。
     *
     * @param fileFallbackRoot 文件模式的回退目录；数据库实现可以忽略
     * @return Webhook 投递仓储
     */
    WebhookDeliveryRepository webhookRepository(Path fileFallbackRoot);

    /**
     * 返回启动时需要恢复 Worker 的租户。
     *
     * @param now 当前时刻
     * @return 存在排队、运行或待补偿终态事件任务的租户 ID
     */
    default Set<String> recoverableTenantIds(Instant now) {
        return Collections.emptySet();
    }

    /** @return 所有租户的任务状态聚合；不支持时返回空映射 */
    default Map<GenerationJobStatus, Long> countsByStatus() {
        return Collections.emptyMap();
    }

    /**
     * 跨租户分批清理终态任务。
     *
     * @param cutoff 截止时间
     * @param limit 单批最大删除数
     * @return 删除数量
     */
    default int purgeTerminalBefore(Instant cutoff, int limit) {
        return 0;
    }

    /** 释放连接池等共享资源。 */
    @Override
    default void close() {
    }
}
