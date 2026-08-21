package cn.bugstack.application.external;

import java.nio.file.Files;
import java.nio.file.Path;

/** 本地开发工件库提供器。 */
public final class LocalExternalArtifactStoreProvider implements ExternalArtifactStoreProvider {

    /** {@inheritDoc} */
    @Override
    public ExternalArtifactStore store(String tenantId, Path tenantRoot) {
        return new LocalExternalArtifactStore(tenantRoot.resolve("outputs"));
    }

    /** {@inheritDoc} */
    @Override
    public boolean isReady() {
        return true;
    }
}
