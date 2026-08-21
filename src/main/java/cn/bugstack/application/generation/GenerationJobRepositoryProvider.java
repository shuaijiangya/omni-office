package cn.bugstack.application.generation;

import cn.bugstack.application.generation.webhook.WebhookDeliveryRepository;

import java.nio.file.Path;

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

    /** 释放连接池等共享资源。 */
    @Override
    default void close() {
    }
}
