package cn.bugstack.application.external;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/** 外部生成结果的受控工件存储 SPI。 */
public interface ExternalArtifactStore {

    ExternalArtifactReference store(byte[] content, String fileName, String mediaType);

    ResolvedExternalArtifact resolve(String resourceUri);

    default List<ExternalArtifactReference> list() {
        return Collections.emptyList();
    }

    default boolean delete(String resourceUri) {
        throw new UnsupportedOperationException("artifact deletion is not supported");
    }

    default int purgeExpired(Instant now) {
        return 0;
    }
}
