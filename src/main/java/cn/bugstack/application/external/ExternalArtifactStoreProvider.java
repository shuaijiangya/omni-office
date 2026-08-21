package cn.bugstack.application.external;

import java.nio.file.Path;

/** 按租户提供最终文档工件库。 */
public interface ExternalArtifactStoreProvider extends AutoCloseable {

    /**
     * 获取指定租户的最终文档工件库。
     *
     * @param tenantId 租户 ID
     * @param tenantRoot 租户本地数据根目录，可作为缓存目录使用
     * @return 租户隔离的工件库
     */
    ExternalArtifactStore store(String tenantId, Path tenantRoot);

    /**
     * 检查工件存储依赖是否可用。
     *
     * @return 可读写时返回 {@code true}
     */
    boolean isReady();

    /** 释放对象存储客户端等共享资源。 */
    @Override
    default void close() {
    }
}
