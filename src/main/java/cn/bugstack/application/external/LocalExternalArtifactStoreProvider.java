package cn.bugstack.application.external;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/** 本地开发工件库提供器。 */
public final class LocalExternalArtifactStoreProvider implements ExternalArtifactStoreProvider {

    private final Duration retention;

    /** 创建默认保留 30 天的本地工件库提供器。 */
    public LocalExternalArtifactStoreProvider() {
        this(Duration.ofDays(30));
    }

    /**
     * 创建使用指定统一保留时间的本地工件库提供器。
     *
     * @param retention 工件保留时间
     */
    public LocalExternalArtifactStoreProvider(Duration retention) {
        if (retention == null || retention.isZero() || retention.isNegative()) {
            throw new IllegalArgumentException("artifact retention must be positive");
        }
        this.retention = retention;
    }

    /** {@inheritDoc} */
    @Override
    public ExternalArtifactStore store(String tenantId, Path tenantRoot) {
        return new LocalExternalArtifactStore(tenantRoot.resolve("outputs"), retention,
                new cn.bugstack.application.external.security.BasicArtifactSecurityScanner());
    }

    /** {@inheritDoc} */
    @Override
    public boolean isReady() {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public Duration retention() {
        return retention;
    }
}
